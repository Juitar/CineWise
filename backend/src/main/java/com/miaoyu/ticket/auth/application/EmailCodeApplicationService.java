package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 组织发送许可、账号隐私判断、验证码登记和事务外邮件投递。 */
@Service
public class EmailCodeApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailCodeApplicationService.class);

    private final AuthUserRepository userRepository;
    private final VerificationCodeGenerator generator;
    private final VerificationCodeHasher hasher;
    private final VerificationCodeRateLimiter rateLimiter;
    private final VerificationCodeIssueTransaction issueTransaction;
    private final VerificationEmailSender emailSender;
    private final LoginAuditSanitizer sanitizer;
    private final VerificationCodeProperties properties;

    public EmailCodeApplicationService(
            AuthUserRepository userRepository,
            VerificationCodeGenerator generator,
            VerificationCodeHasher hasher,
            VerificationCodeRateLimiter rateLimiter,
            VerificationCodeIssueTransaction issueTransaction,
            VerificationEmailSender emailSender,
            LoginAuditSanitizer sanitizer,
            VerificationCodeProperties properties) {
        this.userRepository = userRepository;
        this.generator = generator;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.issueTransaction = issueTransaction;
        this.emailSender = emailSender;
        this.sanitizer = sanitizer;
        this.properties = properties;
    }

    public SendEmailCodeResult send(SendEmailCodeCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        String emailHash = hasher.hash(normalizedEmail, command.purpose(), "rate-limit");
        String ipHash = sanitizer.hashIp(command.remoteAddress());
        VerificationCodeRateLimiter.SendPermit permit;
        try {
            permit = rateLimiter.acquire(
                    emailHash,
                    ipHash,
                    command.purpose(),
                    properties.cooldown(),
                    properties.ipWindow(),
                    properties.maximumIpRequests());
        } catch (RuntimeException exception) {
            LOGGER.warn("验证码限流服务不可用, traceId={}", command.traceId(), exception);
            throw new BusinessException(AuthErrorCode.MAIL_SERVICE_UNAVAILABLE);
        }
        if (!permit.ipAllowed()) {
            throw new BusinessException(AuthErrorCode.RATE_LIMITED);
        }
        if (!permit.emailAllowed()) {
            return result(permit.remainingSeconds());
        }

        Optional<AuthUser> candidate = userRepository.findByEmail(normalizedEmail);
        if (!shouldDeliver(command.purpose(), candidate)) {
            return result(properties.cooldown().toSeconds());
        }

        String plainCode = generator.generate();
        String codeHash = hasher.hash(normalizedEmail, command.purpose(), plainCode);
        EmailVerificationCode stored = issueTransaction.issue(
                normalizedEmail, command.purpose(), codeHash, properties.ttl());
        VerificationEmailSender.DeliveryResult delivery =
                emailSender.send(normalizedEmail, plainCode, command.purpose(), command.traceId());
        if (delivery == VerificationEmailSender.DeliveryResult.SENT) {
            return result(properties.cooldown().toSeconds());
        }
        if (delivery == VerificationEmailSender.DeliveryResult.FAILED) {
            issueTransaction.invalidate(stored.id());
            rateLimiter.releaseEmailCooldown(emailHash, command.purpose());
        }
        throw new BusinessException(AuthErrorCode.MAIL_SERVICE_UNAVAILABLE);
    }

    private boolean shouldDeliver(VerificationPurpose purpose, Optional<AuthUser> candidate) {
        if (purpose == VerificationPurpose.REGISTER) {
            return candidate.isEmpty();
        }
        return candidate.filter(AuthUser::isActive)
                .filter(AuthUser::emailVerified)
                .filter(user -> user.role() == RoleCode.USER)
                .isPresent();
    }

    private SendEmailCodeResult result(long cooldownSeconds) {
        return new SendEmailCodeResult(cooldownSeconds, properties.ttl().toSeconds());
    }

    private String normalizeEmail(String email) {
        try {
            return EmailAddress.normalize(email);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
    }
}
