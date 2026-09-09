package com.chen404.service.support;

import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.entity.Comment;
import com.chen404.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 首页与最新评论共用视图组装；公共内容流中的文章标题按匿名权限读取。 */
@Component
@RequiredArgsConstructor
public class RecentCommentViewAssembler {
    private final HomeViewConverter converter;
    private final ArticleService articleService;

    public List<RecentCommentVO> toList(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        List<RecentCommentVO> views = converter.toRecentCommentVOList(comments);
        Set<Long> articleIds = comments.stream().map(Comment::getArticleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> titles = articleService.getVisibleArticleTitles(articleIds, null);
        for (RecentCommentVO view : views) {
            if (view.getArticleId() != null) {
                view.setArticleTitle(titles.get(view.getArticleId()));
            }
        }
        return views;
    }
}
