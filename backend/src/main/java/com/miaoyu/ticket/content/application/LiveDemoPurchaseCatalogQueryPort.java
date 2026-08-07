package com.miaoyu.ticket.content.application;

/** D 内部读取真实演示目录的端口；A 只能依赖 {@link ContentPurchaseQueryPort}。 */
public interface LiveDemoPurchaseCatalogQueryPort {

    ContentPurchaseQueryPort.DemoPurchaseCatalog findLiveCatalog(String cityCode);
}
