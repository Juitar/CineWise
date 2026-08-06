package com.miaoyu.ticket.profile.infrastructure.tool;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.profile.application.ProfileQueryService;
import com.miaoyu.ticket.profile.application.ProfileSummary;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * B 可注册的只读画像工具。
 * 工具不接收 userId、不访问 Mapper、不写 Agent 表，也不发布 SSE；身份只来自认证线程。
 */
@Component
public class GetProfileSummaryTool {
  private static final String TOOL_NAME = "profile-summary";
  private final CurrentUserAccessor currentUserAccessor;
  private final ProfileQueryService profileQueryService;

  public GetProfileSummaryTool(CurrentUserAccessor currentUserAccessor, ProfileQueryService profileQueryService) {
    this.currentUserAccessor = currentUserAccessor;
    this.profileQueryService = profileQueryService;
  }

  public ToolResult<ProfileSummary> execute(ToolContext context) {
    if (!TOOL_NAME.equals(context.targetName())) {
      return failed(102002, "工具用途不匹配");
    }
    long userId = currentUserAccessor.requireCurrentUserId();
    ProfileSummary summary = profileQueryService.assembleSummary(userId);
    Instant now = Instant.now();
    return new ToolResult<>(ToolStatus.SUCCESS, summary, null, false, false, null, false, null,
        summary.version(), now, now.plusSeconds(300));
  }

  private ToolResult<ProfileSummary> failed(int code, String message) {
    return new ToolResult<>(ToolStatus.FAILED, null, code, false, false, message, false, null,
        null, null, null);
  }
}
