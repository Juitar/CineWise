package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 密码重置应用服务先完成请求规则校验，再进入单一数据库事务。 */
@Service
public class PasswordResetApplicationService {

    private static final Pattern CODE = Pattern.compile("\\d{6}");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private final PasswordResetTransaction transaction;

    public PasswordResetApplicationService(PasswordResetTransaction transaction) {
        this.transaction = transaction;
    }

    public void reset(PasswordResetCommand command) {
        if (command == null
                || command.clientRequestId() == null
                || command.clientRequestId().isBlank()
                || command.clientRequestId().length() > 64
                || command.code() == null
                || !CODE.matcher(command.code()).matches()
                || command.newPassword() == null
                || !PASSWORD.matcher(command.newPassword()).matches()) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        String normalizedEmail;
        try {
            normalizedEmail = EmailAddress.normalize(command.email());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        transaction.execute(normalizedEmail, command.code(), command.newPassword());
    }
}
