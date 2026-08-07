package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 管理员手动导入外部排期参考的应用入口。
 *
 * <p>URL 安全规则只能保护 HTTP 调用；这里再次复核 ADMIN，避免未来 Job、Tool 或其他适配器直接
 * 调用导入能力时绕过授权。影院 ID 在这里而不是 Controller 解析，保证所有调用方都遵守相同的
 * 十进制字符串与上限规则。</p>
 */
@Service
public class AdminExternalShowtimeSandboxImportService {

    private static final int MAXIMUM_CINEMA_IDS = 100;

    private final CurrentUserAccessor currentUserAccessor;
    private final ExternalShowtimeSandboxImportApplicationService importApplicationService;

    public AdminExternalShowtimeSandboxImportService(
            CurrentUserAccessor currentUserAccessor,
            ExternalShowtimeSandboxImportApplicationService importApplicationService) {
        this.currentUserAccessor = Objects.requireNonNull(currentUserAccessor, "currentUserAccessor must not be null");
        this.importApplicationService = Objects.requireNonNull(
                importApplicationService,
                "importApplicationService must not be null");
    }

    /**
     * 使用管理员选择的日期和影院执行一次受控导入。
     *
     * <p>重复影院 ID 在调用 D Port 前去重，避免同一影院在一批请求中被重复查询。外部三元键到
     * 本地场次的重复与并发安全仍由既有导入事务和 V019 唯一约束负责。</p>
     */
    public ExternalShowtimeSandboxImportApplicationService.ImportResult importReferences(
            LocalDate showDate,
            List<String> cinemaIds) {
        requireAdministrator();
        LocalDate normalizedShowDate = requireShowDate(showDate);
        List<Long> normalizedCinemaIds = normalizeCinemaIds(cinemaIds);
        return importApplicationService.importReferences(normalizedShowDate, normalizedCinemaIds);
    }

    /** 读取 C 的当前身份摘要后复核角色，禁止信任任何请求字段描述的权限。 */
    private void requireAdministrator() {
        CurrentUser currentUser = currentUserAccessor.requireCurrentUser();
        if (currentUser.role() != RoleCode.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 日期是 D 公开查询的必填条件；不在此处发明额外的日期窗口。 */
    private LocalDate requireShowDate(LocalDate showDate) {
        if (showDate == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return showDate;
    }

    /**
     * 将 REST 的十进制字符串业务 ID 转换为有序且去重的正 long 列表。
     *
     * <p>前导零会造成同一个业务 ID 有多种文本表示，进而破坏日志、缓存和请求签名的一致性，
     * 因此直接拒绝。解析溢出也统一归为 100001，而不是泄漏 NumberFormatException。</p>
     */
    private List<Long> normalizeCinemaIds(List<String> cinemaIds) {
        if (cinemaIds == null || cinemaIds.isEmpty() || cinemaIds.size() > MAXIMUM_CINEMA_IDS) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }

        Set<Long> normalizedCinemaIds = new LinkedHashSet<>();
        for (String cinemaId : cinemaIds) {
            normalizedCinemaIds.add(parseCinemaId(cinemaId));
        }
        return List.copyOf(new ArrayList<>(normalizedCinemaIds));
    }

    /** 解析单个影院业务 ID，保留 REST ID 必须为规范十进制字符串的跨端约束。 */
    private long parseCinemaId(String cinemaId) {
        if (cinemaId == null || !cinemaId.matches("[1-9][0-9]*")) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        try {
            return Long.parseLong(cinemaId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
