package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;

/**
 * 公开给页面或后续工具适配器的单日排期摘要。
 *
 * <p>showCount表示符合场次状态和时间窗口的排期数量，不代表余座数量或可锁定承诺。</p>
 */
public record AvailableDateView(LocalDate date, int showCount) {
}
