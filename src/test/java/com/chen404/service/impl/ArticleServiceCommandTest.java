package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.converter.ArticleCommandConverter;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleWriteCommand;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.UpdateArticleCommand;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.service.AccessService;
import com.chen404.service.AdminContentEventPublisher;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.TagService;
import com.chen404.service.support.ArticleViewAssembler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖命令到持久化的成功路径，防止抽离实体临时字段后丢失标签或编辑版本。 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceCommandTest {
    @Mock private ArticleMapper articleMapper;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private AccessService accessService;
    @Mock private SysFileService sysFileService;
    @Mock private ProtectedFileAccessService protectedFileAccessService;
    @Mock private TagService tagService;
    @Mock private FileReferenceService fileReferenceService;
    @Mock private ArticleKnowledgeService articleKnowledgeService;
    @Mock private AdminContentEventPublisher adminContentEventPublisher;
    @Mock private ArticleViewAssembler viewAssembler;
    @InjectMocks private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "commandConverter", Mappers.getMapper(ArticleCommandConverter.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "command-test");
        assistant.setCurrentNamespace("command-test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
        TableInfoHelper.initTableInfo(assistant, ArticleTag.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPersistCommandTagsAndReturnAssembledView(boolean update) {
        Article stored = new Article();
        stored.setId(11L);
        stored.setAuthorId(7L);
        stored.setVersion(5);
        stored.setStatus(0);
        stored.setCategoryId(3L);
        ArticleDetailVO expectedView = new ArticleDetailVO();
        expectedView.setId(11L);
        when(articleMapper.selectById(11L)).thenReturn(stored);
        when(accessService.getUserOrNull(7L)).thenReturn(new User());
        when(accessService.canViewArticle(7L, stored)).thenReturn(true);
        when(viewAssembler.toDetail(stored, 7L)).thenReturn(expectedView);
        when(protectedFileAccessService.normalizeContent("正文")).thenReturn("正文");
        Tag resolved = new Tag();
        resolved.setId(9L);
        when(tagService.findOrCreateByName("Java")).thenReturn(resolved);

        ArticleWriteCommand command;
        if (update) {
            UpdateArticleCommand edit = new UpdateArticleCommand();
            edit.setVersion(5);
            command = edit;
            when(accessService.canManageArticle(7L, stored)).thenReturn(true);
            when(articleMapper.updateEditableFields(any(), eq(5), eq(false))).thenReturn(1);
        } else {
            command = new CreateArticleCommand();
            when(accessService.canCreateArticle(7L)).thenReturn(true);
            when(articleMapper.insert(any(Article.class))).thenAnswer(call -> {
                call.getArgument(0, Article.class).setId(11L);
                return 1;
            });
        }
        command.setTitle("标题");
        command.setContent("正文");
        command.setCategoryId(3L);
        command.setStatus(0);
        command.setTagIds(List.of(8L));
        command.setTagNames(List.of(" Java ", "Java"));
        ArticleDetailVO actual = update
                ? service.updateArticle(11L, (UpdateArticleCommand) command, 7L)
                : service.createArticle((CreateArticleCommand) command, 7L);

        assertSame(expectedView, actual);
        ArgumentCaptor<Article> article = ArgumentCaptor.forClass(Article.class);
        if (update) {
            verify(articleMapper).updateEditableFields(article.capture(), eq(5), eq(false));
            assertNull(article.getValue().getViewCount());
        } else {
            verify(articleMapper).insert(article.capture());
            assertEquals(0, article.getValue().getViewCount());
            assertEquals(0, article.getValue().getVersion());
        }
        assertEquals(7L, article.getValue().getAuthorId());
        assertEquals("正文", article.getValue().getContent());
        ArgumentCaptor<ArticleTag> links = ArgumentCaptor.forClass(ArticleTag.class);
        verify(articleTagMapper, times(2)).insert(links.capture());
        assertEquals(List.of(8L, 9L), links.getAllValues().stream().map(ArticleTag::getTagId).toList());
        assertEquals(List.of(11L, 11L), links.getAllValues().stream().map(ArticleTag::getArticleId).toList());
        verify(fileReferenceService).syncArticleReferences(11L, "正文", null);
        verify(articleKnowledgeService).syncArticleChunks(11L);
        assertEquals(List.of(" Java ", "Java"), command.getTagNames());
    }
}
