package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ProfileContextTag;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.profile.infrastructure.tool.GetProfileSummaryTool;
import java.util.List;

/** B 内部画像预取；不属于工具注册或运行节点。 */
public final class ProfileContextPrefetcher {
    private static final String INTERNAL_NODE_ID = "profile-context-prefetch";
    private final GetProfileSummaryTool tool;

    public ProfileContextPrefetcher(GetProfileSummaryTool tool) {
        this.tool = tool;
    }

    public static ProfileContextPrefetcher disabled() {
        return new ProfileContextPrefetcher(null);
    }

    public List<ProfileContextTag> prefetch(MultiToolSupervisorRequest request) {
        if (tool == null) {
            return List.of();
        }
        try {
            var result = tool.execute(new ToolContext(request.runId(), INTERNAL_NODE_ID, "profile-summary", List.of(),
                    request.remainingDeadlineMs(), request.traceId(), null, null,
                    request.validationContext().slotSnapshot().version()));
            if (result.status() != ToolStatus.SUCCESS || result.data() == null || !result.data().enabled()) {
                return List.of();
            }
            return result.data().tags().stream().map(tag -> new ProfileContextTag(tag.type().name(), tag.value(),
                    tag.polarity().name(), tag.weight().toPlainString(), tag.confidence().toPlainString(),
                    tag.source().name(), tag.updatedAt().toString())).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
