-- Private review draft for A. Do not commit or execute before A completes static review.
-- OpenSpec: password-reset-by-email-code
-- Owner: C / auth

ALTER TABLE `sys_email_verify_code`
    DROP CHECK `chk_verify_purpose`,
    ADD CONSTRAINT `chk_verify_purpose`
        CHECK (`purpose` IN ('REGISTER', 'LOGIN', 'RESET_PASSWORD'));
