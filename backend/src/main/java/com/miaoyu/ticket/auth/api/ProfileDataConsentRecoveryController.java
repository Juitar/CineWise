package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.ProfileDataConsentOutboxDeliveryService;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.error.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员人工恢复耗尽事件，仍复用原 eventId 和原 outbox 行。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/profile-data-consent-outbox")
public class ProfileDataConsentRecoveryController {
  private final ProfileDataConsentOutboxDeliveryService deliveryService;

  public ProfileDataConsentRecoveryController(ProfileDataConsentOutboxDeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @PostMapping("/{eventId}/recover")
  public Result<Void> recover(
      @PathVariable @NotBlank @Size(max = 64) String eventId) {
    if (!deliveryService.recoverExhausted(eventId)) {
      throw new BusinessException(AuthErrorCode.PROFILE_DATA_CONSENT_RECOVERY_UNAVAILABLE);
    }
    return Result.success();
  }
}
