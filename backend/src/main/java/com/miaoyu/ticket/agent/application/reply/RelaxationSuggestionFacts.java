package com.miaoyu.ticket.agent.application.reply;

/** 空方案时唯一允许展示的放宽建议。 */
public record RelaxationSuggestionFacts(String factor, String message) { }
