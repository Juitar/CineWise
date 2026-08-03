package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 演示账号只在显式开关开启时补缺，不更新任何已经存在的认证账号。 */
@Service
public class AuthDemoSeedService {

    private static final Pattern DEMO_PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private final AuthUserRepository userRepository;
    private final PasswordVerifier passwordVerifier;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AuthDemoSeedService(
            AuthUserRepository userRepository,
            PasswordVerifier passwordVerifier,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordVerifier = passwordVerifier;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public int createMissingAccounts(AuthDemoSeedCommand command) {
        validatePassword(command.userPassword());
        validatePassword(command.adminPassword());
        LocalDateTime now = LocalDateTime.now(clock);
        int created = createIfMissing(
                command.userEmail(), command.userPassword(), command.userNickname(), RoleCode.USER,
                command.privacyPolicyVersion(), now);
        return created + createIfMissing(
                command.adminEmail(), command.adminPassword(), command.adminNickname(), RoleCode.ADMIN,
                command.privacyPolicyVersion(), now);
    }

    private int createIfMissing(
            String email,
            String password,
            String nickname,
            RoleCode role,
            String privacyPolicyVersion,
            LocalDateTime now) {
        String normalizedEmail = EmailAddress.normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return 0;
        }
        AuthUser user = new AuthUser(
                idGenerator.nextId(),
                normalizedEmail,
                passwordVerifier.encode(password),
                nickname.strip(),
                role,
                AccountStatus.NORMAL,
                true,
                0L,
                privacyPolicyVersion.strip(),
                now);
        try {
            userRepository.create(user, now);
            return 1;
        } catch (DuplicateKeyException exception) {
            // 多实例同时初始化时唯一邮箱只允许一个成功；再次确认存在后按幂等成功处理。
            if (userRepository.existsByEmail(normalizedEmail)) {
                return 0;
            }
            throw exception;
        }
    }

    private void validatePassword(String password) {
        if (password == null || !DEMO_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalStateException("认证演示账号密码必须为 8 到 20 位且同时包含字母和数字");
        }
    }
}
