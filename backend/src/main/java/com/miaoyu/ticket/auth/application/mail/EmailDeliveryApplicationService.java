package com.miaoyu.ticket.auth.application.mail;

import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 校验命令、解析已验证邮箱并调用 Provider，业务提醒状态仍由调用模块维护。 */
@Service
public class EmailDeliveryApplicationService implements EmailDeliveryPort {

    static final int INVALID_COMMAND = 301101;
    static final int TEMPLATE_REJECTED = 301102;
    static final int RECIPIENT_UNAVAILABLE = 301103;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDeliveryApplicationService.class);
    private static final Pattern DELIVERY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,160}");
    private static final Pattern TEMPLATE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern USER_ID = Pattern.compile("[1-9]\\d{0,18}");
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final AuthUserRepository userRepository;
    private final EmailTemplateRegistry templateRegistry;
    private final EmailProviderPort provider;

    public EmailDeliveryApplicationService(
            AuthUserRepository userRepository,
            EmailTemplateRegistry templateRegistry,
            EmailProviderPort provider) {
        this.userRepository = userRepository;
        this.templateRegistry = templateRegistry;
        this.provider = provider;
    }

    @Override
    public EmailDeliveryResult send(EmailDeliveryCommand command) {
        if (!isValid(command)) {
            return EmailDeliveryResult.failed(INVALID_COMMAND);
        }
        Optional<EmailTemplateRegistry.RenderedEmail> rendered =
                templateRegistry.render(command.templateCode(), command.variables());
        if (rendered.isEmpty()) {
            return EmailDeliveryResult.failed(TEMPLATE_REJECTED);
        }

        long userId;
        try {
            userId = Long.parseLong(command.recipientUserId());
        } catch (NumberFormatException exception) {
            return EmailDeliveryResult.failed(INVALID_COMMAND);
        }
        Optional<AuthUser> candidate = userRepository.findById(userId)
                .filter(AuthUser::isActive)
                .filter(AuthUser::emailVerified)
                .filter(user -> user.role() == RoleCode.USER);
        if (candidate.isEmpty()) {
            return EmailDeliveryResult.failed(RECIPIENT_UNAVAILABLE);
        }

        EmailTemplateRegistry.RenderedEmail content = rendered.orElseThrow();
        EmailDeliveryResult result = provider.send(new EmailProviderPort.ProviderEmail(
                command.deliveryKey(),
                candidate.orElseThrow().email(),
                content.subject(),
                content.body(),
                command.traceId()));
        LOGGER.info(
                "公共邮件投递完成, deliveryKeyHash={}, templateCode={}, status={}, errorCode={}, traceId={}",
                hashKey(command.deliveryKey()),
                command.templateCode(),
                result.status(),
                result.errorCode(),
                command.traceId());
        return result;
    }

    @Override
    public EmailDeliveryResult query(String deliveryKey) {
        if (deliveryKey == null || !DELIVERY_KEY.matcher(deliveryKey).matches()) {
            return EmailDeliveryResult.failed(INVALID_COMMAND);
        }
        return provider.query(deliveryKey);
    }

    private boolean isValid(EmailDeliveryCommand command) {
        return command != null
                && command.deliveryKey() != null
                && DELIVERY_KEY.matcher(command.deliveryKey()).matches()
                && command.templateCode() != null
                && TEMPLATE_CODE.matcher(command.templateCode()).matches()
                && command.recipientUserId() != null
                && USER_ID.matcher(command.recipientUserId()).matches()
                && command.traceId() != null
                && TRACE_ID.matcher(command.traceId()).matches();
    }

    private String hashKey(String deliveryKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(deliveryKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                value.append(String.format("%02x", digest[index]));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
