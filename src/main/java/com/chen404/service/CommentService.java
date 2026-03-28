package com.chen404.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.dto.CommentLikeResult;
import com.chen404.domain.dto.CreateCommentDTO;
import com.chen404.domain.entity.Comment;

import java.util.List;

public interface CommentService {

    Page<Comment> getCommentsByArticleId(Long articleId, int page, int size, Long requesterId);

    Page<Comment> getGuestbookComments(int page, int size);

    List<Comment> getRecentComments(int limit);

    Comment createComment(CreateCommentDTO dto, Long userId, String ip, String userAgent);

    void deleteComment(Long id, Long userId);

    void deleteCommentAsGuest(Long id, String guestDeleteKey);

    Comment reviewComment(Long id, int status);

    CommentLikeResult likeComment(Long id, Long userId, String clientIp);
}
