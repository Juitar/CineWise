package com.miaoyu.ticket.auth.application.mail;

/** C 提供给其他模块的公共邮件 Application API；调用方不得绕过它访问 SMTP。 */
public interface EmailDeliveryPort {

    EmailDeliveryResult send(EmailDeliveryCommand command);

    EmailDeliveryResult query(String deliveryKey);
}
