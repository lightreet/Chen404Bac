package com.chen404.service.impl;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.User;
import com.chen404.exception.ConflictException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.service.AccessService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.service.SysFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证服务在版本冲突后立即终止，不再改标签、文件或知识切片。 */
class ArticleServiceImplUpdateTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {2})
    void shouldRejectVersionConflictBeforeDependentWrites(Integer submittedVersion) {
        ArticleMapper mapper = mock(ArticleMapper.class);
        AccessService access = mock(AccessService.class);
        SysFileService files = mock(SysFileService.class);
        FileReferenceService references = mock(FileReferenceService.class);
        ArticleKnowledgeService knowledge = mock(ArticleKnowledgeService.class);
        ArticleServiceImpl service = new ArticleServiceImpl();
        ReflectionTestUtils.setField(service, "articleMapper", mapper);
        ReflectionTestUtils.setField(service, "accessService", access);
        ReflectionTestUtils.setField(service, "sysFileService", files);
        ReflectionTestUtils.setField(service, "fileReferenceService", references);
        ReflectionTestUtils.setField(service, "articleKnowledgeService", knowledge);
        ReflectionTestUtils.setField(service, "protectedFileAccessService", mock(ProtectedFileAccessService.class));
        Article existing = new Article();
        existing.setId(1L);
        existing.setAuthorId(7L);
        existing.setStatus(1);
        existing.setVersion(3);
        when(mapper.selectById(1L)).thenReturn(existing);
        when(access.getUserOrNull(7L)).thenReturn(new User());
        when(access.canManageArticle(7L, existing)).thenReturn(true);
        Article edit = new Article();
        edit.setVersion(submittedVersion);
        edit.setStatus(1);

        assertThrows(ConflictException.class, () -> service.updateArticle(1L, edit, 7L));

        verify(mapper).updateEditableFields(edit, submittedVersion == null ? 3 : submittedVersion, false);
        verify(mapper, never()).updateById(any(Article.class));
        verifyNoInteractions(files, references, knowledge);
    }
}
