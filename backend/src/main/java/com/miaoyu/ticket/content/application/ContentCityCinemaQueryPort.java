package com.miaoyu.ticket.content.application;

import java.util.List;

/** 按 D 已确认的城市名读取本地影院业务 ID，不把城市或 Provider 参数传给票务持久层。 */
public interface ContentCityCinemaQueryPort {

    List<Long> findActiveCinemaIds(String cityName);
}
