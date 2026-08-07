package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.ProfileDataConsentService;
import com.miaoyu.ticket.common.api.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人画像同意写接口；不提供查询同意状态的 REST。 */
@RestController
@RequestMapping("/api/v1/auth/profile-data-consent")
public class ProfileDataConsentController {
  private final CurrentUserAccessor currentUserAccessor;
  private final ProfileDataConsentService consentService;

  public ProfileDataConsentController(
      CurrentUserAccessor currentUserAccessor, ProfileDataConsentService consentService) {
    this.currentUserAccessor = currentUserAccessor;
    this.consentService = consentService;
  }

  @PutMapping
  public Result<Void> grant(@Valid @RequestBody GrantRequest request) {
    consentService.grant(currentUserAccessor.requireCurrentUserId(), request.privacyPolicyVersion());
    return Result.success();
  }

  @DeleteMapping
  public Result<Void> withdraw() {
    consentService.withdraw(currentUserAccessor.requireCurrentUserId());
    return Result.success();
  }

  public record GrantRequest(
      @NotBlank @Size(max = 32) String privacyPolicyVersion) { }
}
