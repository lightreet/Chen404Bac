package com.chen404.service;

import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminCommentStatsVO;
import com.chen404.domain.dto.AdminCommentVO;
import com.chen404.domain.enums.CommentSceneEnum;

/**
 * 管理端评论查询与审核服务。
 */
public interface AdminCommentService {

    /**
     * 分页查询管理端评论，支持按审核状态、来源和关键词筛选。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @param status 审核状态，可为空
     * @param scene 评论来源
     * @param keyword 内容、昵称或邮箱关键词
     * @return 管理端评论分页结果
     */
    PageResult<AdminCommentVO> getAdminComments(
            Integer page,
            Integer size,
            Integer status,
            CommentSceneEnum scene,
            String keyword);

    /**
     * 获取各审核状态的评论数量。
     *
     * @return 评论审核统计
     */
    AdminCommentStatsVO getAdminCommentStats();

    /**
     * 更新评论审核结果，并同步文章评论数。
     *
     * @param commentId 评论 ID
     * @param status 目标状态：1-通过 2-拒绝
     * @param adminId 操作管理员 ID
     * @return 更新后的管理端评论视图
     */
    AdminCommentVO reviewComment(Long commentId, Integer status, Long adminId);
}
