package com.miaoyu.ticket.order.infrastructure.persistence;

/** MyBatis 承接自动失效候选的最小行投影。 */
public record ShowEndedTicketCandidateRow(long ticketId, int ticketVersion) {
}
