package com.miaoyu.ticket.travel.application;

/** 餐饮候选只保留页面展示所需字段，不支持预订、排队、点餐或支付。 */
public record FoodPoi(String name, int distanceMeters, boolean businessStatusKnown, String businessStatus) {
}
