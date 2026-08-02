package com.miaoyu.ticket.ticketing.application;

/** 一次固定票务种子执行后应确保存在的对象规模。 */
public record TicketingSeedReport(int auditoriumCount, int showCount, int seatCount) {
}
