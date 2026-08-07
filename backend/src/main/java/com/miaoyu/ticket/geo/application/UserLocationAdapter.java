package com.miaoyu.ticket.geo.application;

import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;

/**
 * 用户位置进入 D 模块的唯一适配入口。
 *
 * <p>C 负责浏览器授权，B 负责从对话中提取并确认地点文本。本接口不读取对话、不保存原文，也不
 * 接收 userId；具体地理编码由基础设施实现，避免 travel 与 recommendation 相互依赖。</p>
 */
public interface UserLocationAdapter {

    /** 将浏览器一次性经纬度校验为设备位置，不进行地址转换。 */
    ResolvedGeoPoint fromBrowser(BigDecimal longitude, BigDecimal latitude);

    /** 将 B 已确认的地点文本解析为带粒度的坐标；歧义必须由实现显式返回或报错。 */
    ResolvedGeoPoint fromPlaceText(String placeText);
}
