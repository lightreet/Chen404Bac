package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.ArticleFileRef;
import com.chen404.domain.entity.SysFile;
import com.chen404.mapper.ArticleFileRefMapper;
import com.chen404.service.ArticleFileRefService;
import com.chen404.service.SysFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ArticleFileRefServiceImpl extends ServiceImpl<ArticleFileRefMapper, ArticleFileRef> implements ArticleFileRefService {

    @Autowired
    private SysFileService sysFileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncForArticle(Long articleId, String content, String coverImage) {
        if (articleId == null) {
            return;
        }
        remove(new LambdaQueryWrapper<ArticleFileRef>().eq(ArticleFileRef::getArticleId, articleId));

        List<ArticleFileRef> rows = new ArrayList<>();

        Set<Long> contentFileIds = new LinkedHashSet<>();
        if (StringUtils.hasText(content)) {
            for (String url : sysFileService.extractImageUrlsFromContent(content)) {
                addIfBelongsToArticle(rows, articleId, url, ArticleFileRef.RefKind.CONTENT, contentFileIds);
            }
        }

        if (StringUtils.hasText(coverImage)) {
            Set<Long> coverDedup = new LinkedHashSet<>();
            addIfBelongsToArticle(rows, articleId, coverImage.trim(), ArticleFileRef.RefKind.COVER, coverDedup);
        }

        if (!rows.isEmpty()) {
            saveBatch(rows);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByArticleId(Long articleId) {
        if (articleId == null) {
            return;
        }
        remove(new LambdaQueryWrapper<ArticleFileRef>().eq(ArticleFileRef::getArticleId, articleId));
    }

    /**
     * 仅当 URL 能在 sys_file 命中且 ref_id 为本文章时写入关联（避免误绑他人或其它文章的文件）
     */
    private void addIfBelongsToArticle(List<ArticleFileRef> rows, Long articleId, String url, String refKind, Set<Long> dedupIds) {
        SysFile file = sysFileService.findByFileUrl(url);
        if (file == null || file.getId() == null) {
            return;
        }
        if (!Objects.equals(file.getRefId(), articleId)) {
            return;
        }
        if (!dedupIds.add(file.getId())) {
            return;
        }
        ArticleFileRef ref = new ArticleFileRef();
        ref.setArticleId(articleId);
        ref.setFileId(file.getId());
        ref.setRefKind(refKind);
        rows.add(ref);
    }
}
