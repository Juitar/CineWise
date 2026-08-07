package com.miaoyu.ticket.geo.domain;

/**
 * 位置来源所能表达的精度范围。
 *
 * <p>城市和行政区只表示一个区域，不能把区域代表点伪装成用户当前所在地。只有设备定位、POI 和
 * 完整地址可以用于个人距离或路线；这个限制放在统一类型上，避免各个 Provider 自行放宽。</p>
 */
public enum LocationGranularity {
    /** 浏览器在用户授权后提供的一次性设备坐标。 */
    DEVICE(true),
    /** 地图服务确认的兴趣点坐标。 */
    POI(true),
    /** 地图服务确认的完整地址坐标。 */
    ADDRESS(true),
    /** 行政区代表点，仅用于区域范围查询。 */
    DISTRICT(false),
    /** 城市代表点，仅用于城市范围查询。 */
    CITY(false);

    private final boolean suitableForPersonalDistance;

    LocationGranularity(boolean suitableForPersonalDistance) {
        this.suitableForPersonalDistance = suitableForPersonalDistance;
    }

    /**
     * 判断该粒度能否代表个人距离或路线的真实起点。
     *
     * <p>这里不根据经纬度数值猜测精度，必须以输入来源确认的粒度为准。</p>
     */
    public boolean suitableForPersonalDistance() {
        return suitableForPersonalDistance;
    }
}
