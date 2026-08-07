package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;
import java.util.Optional;

/** 影片公开目录的本地只读端口；实现不得访问外部 Provider。 */
public interface ContentLocalMovieCatalogPort {

    Optional<ContentResult<List<? extends ContentItem>>> findMovies(String keyword, String releaseStatus);
}
