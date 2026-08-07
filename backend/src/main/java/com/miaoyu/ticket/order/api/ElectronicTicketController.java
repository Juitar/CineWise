package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.application.ElectronicTicketQueryService;
import com.miaoyu.ticket.order.application.ElectronicTicketView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人电子票详情REST适配层。 */
@Validated
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "电子票")
@SecurityRequirement(name = "cookieAuth")
public class ElectronicTicketController {

    private final ElectronicTicketQueryService ticketQueryService;

    public ElectronicTicketController(ElectronicTicketQueryService ticketQueryService) {
        this.ticketQueryService = ticketQueryService;
    }

    /** 票ID在API边界保持字符串，跨用户与不存在统一返回领域404。 */
    @GetMapping("/{ticketId}")
    @Operation(summary = "查询本人电子票详情")
    public Result<ElectronicTicketResponse> queryTicket(@PathVariable String ticketId) {
        return Result.success(toResponse(ticketQueryService.queryTicket(parseBusinessId(ticketId))));
    }

    private ElectronicTicketResponse toResponse(ElectronicTicketView ticket) {
        return new ElectronicTicketResponse(
                Long.toString(ticket.ticketId()),
                ticket.ticketCode(),
                Long.toString(ticket.orderId()),
                ticket.orderNo(),
                Long.toString(ticket.showId()),
                ticket.seatIds().stream().map(String::valueOf).toList(),
                ticket.status().name(),
                ticket.invalidationReason() == null ? null : ticket.invalidationReason().name(),
                ticket.qrPayload(),
                toOffsetDateTime(ticket.issuedAt()),
                ticket.stateVersion(),
                toOffsetDateTime(ticket.updatedAt()));
    }

    private long parseBusinessId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "业务ID必须是正整数");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
