package com.chen404.converter;

import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.BannerVO;
import com.chen404.domain.dto.CategoryVO;
import com.chen404.domain.dto.CommentVO;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.CreateCategoryCommand;
import com.chen404.domain.dto.EmojiImportResultDTO;
import com.chen404.domain.dto.EmojiItemVO;
import com.chen404.domain.dto.EmojiPackVO;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.dto.TagVO;
import com.chen404.domain.dto.UserProfileVO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.Comment;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryConverterTest {

    private final ArticleCommandConverter articleCommandConverter = Mappers.getMapper(ArticleCommandConverter.class);
    private final ArticleViewConverter articleViewConverter = Mappers.getMapper(ArticleViewConverter.class);
    private final CategoryConverter categoryConverter = Mappers.getMapper(CategoryConverter.class);
    private final TagConverter tagConverter = Mappers.getMapper(TagConverter.class);
    private final HomeViewConverter homeViewConverter = Mappers.getMapper(HomeViewConverter.class);
    private final CommentConverter commentConverter = Mappers.getMapper(CommentConverter.class);
    private final UserConverter userConverter = Mappers.getMapper(UserConverter.class);
    private final EmojiConverter emojiConverter = Mappers.getMapper(EmojiConverter.class);

    @Test
    void articleCommandConverterShouldOnlyMapEditableFields() {
        CreateArticleCommand command = new CreateArticleCommand();
        command.setTitle("命令对象标题");
        command.setSummary("摘要");
        command.setContent("内容");
        command.setCategoryId(12L);
        command.setStatus(1);
        command.setTagIds(List.of(1L, 2L));
        command.setTagNames(List.of("新增标签"));
        command.setVisibility(2);
        command.setCommentPolicy(3);

        Article article = articleCommandConverter.toEntity(command);

        assertEquals("命令对象标题", article.getTitle());
        assertEquals(12L, article.getCategoryId());
        assertEquals(List.of(1L, 2L), article.getTagIds());
        assertEquals(List.of("新增标签"), article.getTagNames());
        assertEquals(2, article.getVisibility());
        assertNull(article.getId(), "命令对象不应携带数据库主键");
        assertNull(article.getLiked(), "命令对象不应映射展示态字段");
        assertNull(article.getFavorited(), "命令对象不应映射展示态字段");
    }

    @Test
    void articleViewConverterShouldExposeDetailVoWithoutLeakingCommandFields() {
        Article article = new Article();
        article.setId(123L);
        article.setTitle("文章标题");
        article.setSummary("文章摘要");
        article.setContent("Markdown 内容");
        article.setContentHtml("<p>HTML 内容</p>");
        article.setCategoryId(8L);
        article.setAuthorId(9L);
        article.setStatus(1);
        article.setViewCount(10);
        article.setLikeCount(3);
        article.setCommentCount(4);
        article.setIsTop(1);
        article.setIsRecommend(0);
        article.setCanEdit(Boolean.TRUE);
        article.setCanDelete(Boolean.TRUE);
        article.setCanComment(Boolean.TRUE);
        article.setLiked(Boolean.TRUE);
        article.setFavorited(Boolean.FALSE);

        User author = new User();
        author.setId(9L);
        author.setUsername("chen404");
        author.setNickname("辰");
        author.setAvatar("/avatar.png");
        article.setAuthor(author);

        Category category = new Category();
        category.setId(8L);
        category.setName("后端");
        category.setSlug("backend");
        article.setCategory(category);

        Tag tag = new Tag();
        tag.setId(6L);
        tag.setName("Spring");
        tag.setSlug("spring");
        tag.setColor("#42b883");
        article.setTags(List.of(tag));

        ArticleDetailVO detailVO = articleViewConverter.toDetailVO(article);

        assertEquals(123L, detailVO.getId());
        assertEquals("Markdown 内容", detailVO.getContent());
        assertEquals("chen404", detailVO.getAuthor().getUsername());
        assertEquals("后端", detailVO.getCategory().getName());
        assertEquals(1, detailVO.getTags().size());
        assertTrue(Boolean.TRUE.equals(detailVO.getCanComment()));
    }

    @Test
    void categoryConverterShouldUseDedicatedBoundaryModels() {
        CreateCategoryCommand command = new CreateCategoryCommand();
        command.setName("分类名");
        command.setSlug("category-slug");
        command.setStatus(1);

        Category category = categoryConverter.toEntity(command);
        assertEquals("分类名", category.getName());
        assertEquals("category-slug", category.getSlug());

        category.setId(5L);
        category.setArticleCount(18);
        CategoryVO vo = categoryConverter.toVO(category);
        assertEquals(5L, vo.getId());
        assertEquals(18, vo.getArticleCount());
    }

    @Test
    void tagAndHomeConvertersShouldProduceExplicitViewModels() {
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("Java");
        tag.setSlug("java");
        tag.setColor("#409EFF");
        tag.setArticleCount(20);
        TagVO tagVO = tagConverter.toVO(tag);
        assertEquals("Java", tagVO.getName());
        assertEquals(20, tagVO.getArticleCount());

        Banner banner = new Banner();
        banner.setId(9L);
        banner.setTitle("首页横幅");
        banner.setImage("/banner.jpg");
        BannerVO bannerVO = homeViewConverter.toBannerVO(banner);
        assertEquals(9L, bannerVO.getId());
        assertEquals("/banner.jpg", bannerVO.getImage());

        Comment comment = new Comment();
        comment.setId(100L);
        comment.setArticleId(200L);
        comment.setContent("很棒的文章");
        comment.setAuthorName("Chen");
        comment.setAuthorAvatar("/avatar.png");
        RecentCommentVO recentCommentVO = homeViewConverter.toRecentCommentVO(comment);
        assertEquals(200L, recentCommentVO.getArticleId());
        assertEquals("很棒的文章", recentCommentVO.getContent());
    }

    @Test
    void commentAndUserConvertersShouldProduceExplicitViewModels() {
        Comment child = new Comment();
        child.setId(2L);
        child.setContent("子评论");
        child.setAuthorName("Bob");

        Comment root = new Comment();
        root.setId(1L);
        root.setArticleId(10L);
        root.setContent("主评论");
        root.setAuthorName("Alice");
        root.setGuestDeleteKey("guest-token");
        root.setChildren(List.of(child));

        CommentVO commentVO = commentConverter.toVO(root);
        assertEquals(1L, commentVO.getId());
        assertEquals(1, commentVO.getChildren().size());
        assertEquals("子评论", commentVO.getChildren().get(0).getContent());
        assertEquals("guest-token", commentVO.getGuestDeleteKey());

        User user = new User();
        user.setId(100L);
        user.setUsername("chen404");
        user.setNickname("辰");
        user.setEmail("chen404@example.com");
        user.setAvatar("/avatar.png");
        user.setRoleCode("admin");
        user.setTrustLevel(1);

        UserProfileVO userProfileVO = userConverter.toVO(user);
        assertEquals(100L, userProfileVO.getId());
        assertEquals("chen404", userProfileVO.getUsername());
        assertEquals("admin", userProfileVO.getRoleCode());
    }

    @Test
    void emojiConverterShouldProduceExplicitViewModels() {
        EmojiPack pack = new EmojiPack();
        pack.setId(1L);
        pack.setPackCode("default");
        pack.setName("默认表情");
        pack.setEnabled(1);

        EmojiPackVO packVO = emojiConverter.toPackVO(pack);
        assertEquals(1L, packVO.getId());
        assertEquals("default", packVO.getPackCode());

        EmojiItem item = new EmojiItem();
        item.setId(2L);
        item.setPackCode("default");
        item.setShortcode(":smile:");
        item.setLabel("微笑");
        item.setType(EmojiItem.Type.UNICODE);
        item.setUnicode("😄");

        EmojiItemVO itemVO = emojiConverter.toItemVO(item);
        assertEquals(":smile:", itemVO.getShortcode());
        assertEquals("😄", itemVO.getUnicode());

        EmojiImportResultDTO importResultDTO = emojiConverter.toImportResultDTO(
                "default",
                2,
                1,
                List.of(Map.of("shortcode", ":sad:", "error", "图片缺失"))
        );
        assertEquals("default", importResultDTO.getPackCode());
        assertEquals(1, importResultDTO.getErrors().size());
        assertEquals(":sad:", importResultDTO.getErrors().get(0).getShortcode());
    }
}