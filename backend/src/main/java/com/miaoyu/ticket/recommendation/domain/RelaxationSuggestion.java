package com.miaoyu.ticket.recommendation.domain;

/** 空结果时给出的单项建议；它不修改用户原条件，必须由 B 取得确认后重新查询。 */
public record RelaxationSuggestion(Factor factor, String message) {
    public enum Factor { GENRE, TIME, BUDGET }
    public RelaxationSuggestion {
        if (factor == null || message == null || message.isBlank()) throw new IllegalArgumentException("放宽建议不能为空");
    }
}
