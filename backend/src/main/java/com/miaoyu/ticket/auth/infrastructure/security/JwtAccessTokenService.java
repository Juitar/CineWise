package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.IssuedAccessToken;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** JWT 只包含已确认的最小声明，用户展示信息始终从数据库重新读取。 */
final class JwtAccessTokenService implements AccessTokenService {

    static final String SESSION_STARTED_AT_CLAIM = "sessionStartedAt";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    JwtAccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(AuthUser user) {
        // 新登录会话的开始时间与签发时间相同，绝对会话时限从此刻开始计算。
        Instant issuedAt = now();
        return encode(user, issuedAt, issuedAt, issuedAt.plus(properties.accessTokenTtl()));
    }

    @Override
    public Optional<IssuedAccessToken> renew(AuthUser user, Instant sessionStartedAt) {
        // 续期只延长短令牌，绝不突破首次登录计算出的绝对会话到期时间。
        if (sessionStartedAt == null) {
            return Optional.empty();
        }
        Instant issuedAt = now();
        Instant absoluteExpiresAt = sessionStartedAt.plus(properties.absoluteSessionTtl());
        if (!issuedAt.isBefore(absoluteExpiresAt)) {
            return Optional.empty();
        }
        Instant regularExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant expiresAt = regularExpiresAt.isBefore(absoluteExpiresAt)
                ? regularExpiresAt
                : absoluteExpiresAt;
        return Optional.of(encode(user, issuedAt, sessionStartedAt, expiresAt));
    }

    private IssuedAccessToken encode(
            AuthUser user, Instant issuedAt, Instant sessionStartedAt, Instant expiresAt) {
        // 声明只包含鉴权所需的用户标识、角色、令牌版本和会话起点，不写邮箱等展示信息。
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(user.id()))
                .claim("role", user.role().name())
                .claim("tokenVersion", user.tokenVersion())
                .claim(SESSION_STARTED_AT_CLAIM, sessionStartedAt.getEpochSecond())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, issuedAt, expiresAt);
    }

    private Instant now() {
        // 秒级截断与 JWT 数值日期声明一致，避免子秒差异造成前后端边界判断不稳定。
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
