package com.miaoyu.ticket.content.application;

/** 读取版本化 Demo 内容资源的应用端口。 */
@FunctionalInterface
public interface DemoContentCatalogProvider {

    /**
     * 加载并校验唯一的共享 Demo 内容目录。
     *
     * @return 已校验的 Demo 内容目录
     */
    DemoContentCatalog load();
}
