package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.converter.TrustRequestConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.dto.CreateTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestAttachmentVO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.enums.UserTrustLevelEnum;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserTrustRequest;
import com.chen404.mapper.UserTrustRequestMapper;
import com.chen404.service.EmailService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.chen404.service.UserService;
import com.chen404.service.UserTrustRequestService;
import com.chen404.service.support.MailTemplateSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserTrustRequestServiceImpl extends ServiceImpl<UserTrustRequestMapper, UserTrustRequest>
        implements UserTrustRequestService {

    private static final int MAX_ATTACHMENT_COUNT = 3;
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int APPROVE_TOKEN_EXPIRE_DAYS = 7;
    private static final String MAIL_SUBJECT_PREFIX = "[Chen404] ";
    private static final String ADMIN_NOTIFICATION_TEMPLATE = "mail/trust-request-admin-notification.html";
    private static final String APPLICANT_RESULT_TEMPLATE = "mail/trust-request-applicant-result.html";
    private static final String APPROVED_BY_MAIL_REVIEW_NOTE = "已通过邮件快速审批";
    private static final String DEFAULT_APPROVED_REVIEW_NOTE = "审核通过";
    private static final String DEFAULT_EMPTY_ATTACHMENT_HTML = "<p style=\"color: #6b7280;\">未上传附件</p>";
    private static final String DEFAULT_EMPTY_CONTACT_EMAIL = "未填写";
    private static final String DEFAULT_EMPTY_REVIEW_NOTE = "管理员未填写额外说明";
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TrustRequestConverter trustRequestConverter;
    private final MailTemplateSupport mailTemplateSupport;
    private final UserService userService;
    private final SysFileService sysFileService;
    private final EmailService emailService;
    private final FileReferenceService fileReferenceService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.backend-base-url:http://localhost:10404/api}")
    private String backendBaseUrl;

    public UserTrustRequestServiceImpl(
            TrustRequestConverter trustRequestConverter,
            MailTemplateSupport mailTemplateSupport,
            UserService userService,
            SysFileService sysFileService,
            EmailService emailService,
            FileReferenceService fileReferenceService
    ) {
        this.trustRequestConverter = trustRequestConverter;
        this.mailTemplateSupport = mailTemplateSupport;
        this.userService = userService;
        this.sysFileService = sysFileService;
        this.emailService = emailService;
        this.fileReferenceService = fileReferenceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrustRequestVO createRequest(Long userId, CreateTrustRequestDTO dto) {
        if (userId == null) {
            throw new RuntimeException("请先登录后再申请");
        }

        User currentUser = userService.getCurrentUser(userId);
        if (currentUser == null) {
            throw new RuntimeException("用户不存在");
        }
        if (UserRoleEnum.ADMIN.matchesRoleCode(currentUser.getRoleCode())) {
            throw new RuntimeException("管理员无需申请受信权限");
        }
        if (Objects.equals(currentUser.getTrustLevel(), UserTrustLevelEnum.FRIEND.getLevel())) {
            throw new RuntimeException("你已经是知友了");
        }

        String reason = normalizeReason(dto == null ? null : dto.getReason());
        List<String> attachmentUrls = normalizeAttachmentUrls(dto == null ? null : dto.getAttachmentUrls(), userId);

        boolean hasPending = lambdaQuery()
                .eq(UserTrustRequest::getUserId, userId)
                .eq(UserTrustRequest::getStatus, UserTrustRequest.Status.PENDING)
                .count() > 0;
        if (hasPending) {
            throw new RuntimeException("你已经有一条待处理申请，请等待管理员审核");
        }

        String approveToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        UserTrustRequest request = new UserTrustRequest();
        request.setUserId(userId);
        request.setStatus(UserTrustRequest.Status.PENDING);
        request.setReason(reason);
        request.setContactEmail(currentUser.getEmail());
        request.setApproveTokenHash(sha256Hex(approveToken));
        request.setApproveTokenExpireAt(LocalDateTime.now().plusDays(APPROVE_TOKEN_EXPIRE_DAYS));
        save(request);

        if (!attachmentUrls.isEmpty()) {
            sysFileService.convertToPermanent(attachmentUrls, SysFile.RefType.TRUST_REQUEST_ATTACHMENT, request.getId());
        }
        fileReferenceService.syncTrustRequestAttachmentReferences(request.getId(), attachmentUrls);

        List<SysFile> attachments = loadAttachmentsByRequestId(request.getId());
        try {
            sendAdminNotificationEmail(currentUser, request, attachments, approveToken);
        } catch (Exception e) {
            log.warn("发送受信申请通知邮件失败，请求ID={}", request.getId(), e);
        }

        return buildTrustRequestVO(request, currentUser, null, attachments);
    }

    @Override
    public TrustRequestVO getLatestForUser(Long userId) {
        if (userId == null) {
            return null;
        }

        UserTrustRequest request = lambdaQuery()
                .eq(UserTrustRequest::getUserId, userId)
                .orderByDesc(UserTrustRequest::getCreateTime)
                .last("limit 1")
                .one();
        if (request == null) {
            return null;
        }

        Map<Long, User> users = loadUsers(Set.of(userId));
        Map<Long, User> reviewers = loadUsers(optionalIdSet(request.getReviewedBy()));
        Map<Long, List<SysFile>> attachments = loadAttachmentMap(Set.of(request.getId()));
        return buildTrustRequestVO(request, users, reviewers, attachments);
    }

    @Override
    public PageResult<TrustRequestVO> getAdminRequests(Integer page, Integer size, Integer status, String keyword) {
        long current = page == null || page < 1 ? 1L : page;
        long pageSize = size == null || size < 1 ? 10L : size;

        LambdaQueryWrapper<UserTrustRequest> wrapper = new LambdaQueryWrapper<UserTrustRequest>()
                .orderByDesc(UserTrustRequest::getCreateTime);
        if (status != null) {
            wrapper.eq(UserTrustRequest::getStatus, status);
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (StringUtils.hasText(normalizedKeyword)) {
            List<User> matchedUsers = userService.list(new LambdaQueryWrapper<User>()
                    .like(User::getUsername, normalizedKeyword)
                    .or()
                    .like(User::getNickname, normalizedKeyword)
                    .or()
                    .like(User::getEmail, normalizedKeyword));
            Set<Long> matchedUserIds = matchedUsers.stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (matchedUserIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, current, pageSize);
            }
            wrapper.in(UserTrustRequest::getUserId, matchedUserIds);
        }

        Page<UserTrustRequest> requestPage = page(new Page<>(current, pageSize), wrapper);
        List<UserTrustRequest> records = requestPage.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(new Page<TrustRequestVO>(current, pageSize, 0));
        }

        Map<Long, User> requestUsers = loadUsers(records.stream()
                .map(UserTrustRequest::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, User> reviewers = loadUsers(records.stream()
                .map(UserTrustRequest::getReviewedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, List<SysFile>> attachments = loadAttachmentMap(records.stream()
                .map(UserTrustRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<TrustRequestVO> list = records.stream()
                .map(record -> buildTrustRequestVO(record, requestUsers, reviewers, attachments))
                .toList();

        return new PageResult<>(list, requestPage.getTotal(), requestPage.getCurrent(), requestPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrustRequestVO approveRequest(Long requestId, Long adminId, String reviewNote) {
        UserTrustRequest request = requireRequest(requestId);
        UserTrustRequest approved = approveRequestInternal(request, adminId, normalizeReviewNote(reviewNote));
        Map<Long, User> users = loadUsers(Set.of(approved.getUserId()));
        Map<Long, User> reviewers = loadUsers(optionalIdSet(approved.getReviewedBy()));
        Map<Long, List<SysFile>> attachments = loadAttachmentMap(Set.of(approved.getId()));
        return buildTrustRequestVO(approved, users, reviewers, attachments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrustRequestVO rejectRequest(Long requestId, Long adminId, String reviewNote) {
        UserTrustRequest request = requireRequest(requestId);
        if (!Objects.equals(request.getStatus(), UserTrustRequest.Status.PENDING)) {
            throw new RuntimeException("该申请已处理，不能重复拒绝");
        }

        String note = normalizeReviewNote(reviewNote);
        if (!StringUtils.hasText(note)) {
            throw new RuntimeException("拒绝申请时请填写审核说明");
        }

        request.setStatus(UserTrustRequest.Status.REJECTED);
        request.setReviewNote(note);
        request.setReviewedBy(adminId);
        request.setReviewedAt(LocalDateTime.now());
        request.setApproveTokenUsedAt(LocalDateTime.now());
        updateById(request);

        User applicant = userService.getById(request.getUserId());
        try {
            sendApplicantResultEmail(applicant, false, note);
        } catch (Exception e) {
            log.warn("发送受信申请拒绝邮件失败，请求ID={}", request.getId(), e);
        }

        Map<Long, User> users = loadUsers(Set.of(request.getUserId()));
        Map<Long, User> reviewers = loadUsers(optionalIdSet(request.getReviewedBy()));
        Map<Long, List<SysFile>> attachments = loadAttachmentMap(Set.of(request.getId()));
        return buildTrustRequestVO(request, users, reviewers, attachments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String approveByEmailToken(String token) {
        if (!StringUtils.hasText(token)) {
            return buildResultHtml("链接无效", "这个审批链接缺少参数，无法完成审核。", false);
        }

        UserTrustRequest request = lambdaQuery()
                .eq(UserTrustRequest::getApproveTokenHash, sha256Hex(token.trim()))
                .last("limit 1")
                .one();
        if (request == null) {
            return buildResultHtml("链接已失效", "没有找到对应的申请记录，可能已经处理或链接已过期。", false);
        }
        if (!Objects.equals(request.getStatus(), UserTrustRequest.Status.PENDING)) {
            return buildResultHtml("申请已处理", "这条申请已经审核完成，不需要再次操作。", true);
        }
        if (request.getApproveTokenExpireAt() != null && request.getApproveTokenExpireAt().isBefore(LocalDateTime.now())) {
            return buildResultHtml("链接已过期", "这条邮件审批链接已经过期，请改为在后台管理页面处理。", false);
        }

        approveRequestInternal(request, null, APPROVED_BY_MAIL_REVIEW_NOTE);
        return buildResultHtml("审批成功", "该用户已被设置为知友，后台列表也会同步更新。", true);
    }

    private UserTrustRequest approveRequestInternal(UserTrustRequest request, Long adminId, String reviewNote) {
        if (!Objects.equals(request.getStatus(), UserTrustRequest.Status.PENDING)) {
            throw new RuntimeException("该申请已处理，不能重复通过");
        }

        request.setStatus(UserTrustRequest.Status.APPROVED);
        request.setReviewNote(StringUtils.hasText(reviewNote) ? reviewNote.trim() : DEFAULT_APPROVED_REVIEW_NOTE);
        request.setReviewedBy(adminId);
        request.setReviewedAt(LocalDateTime.now());
        request.setApproveTokenUsedAt(LocalDateTime.now());
        updateById(request);

        userService.updateTrustLevel(request.getUserId(), UserTrustLevelEnum.FRIEND.getLevel());

        User applicant = userService.getById(request.getUserId());
        try {
            sendApplicantResultEmail(applicant, true, request.getReviewNote());
        } catch (Exception e) {
            log.warn("发送受信申请通过邮件失败，请求ID={}", request.getId(), e);
        }

        return request;
    }

    private UserTrustRequest requireRequest(Long requestId) {
        if (requestId == null) {
            throw new RuntimeException("申请 ID 不能为空");
        }

        UserTrustRequest request = getById(requestId);
        if (request == null) {
            throw new RuntimeException("申请记录不存在");
        }
        return request;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new RuntimeException("请填写申请理由");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new RuntimeException("申请理由不能超过 " + MAX_REASON_LENGTH + " 个字");
        }
        return normalized;
    }

    private List<String> normalizeAttachmentUrls(List<String> attachmentUrls, Long userId) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return List.of();
        }

        List<String> normalized = attachmentUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        if (normalized.size() > MAX_ATTACHMENT_COUNT) {
            throw new RuntimeException("最多只能上传 " + MAX_ATTACHMENT_COUNT + " 个附件");
        }

        for (String fileUrl : normalized) {
            SysFile file = sysFileService.findByFileUrl(fileUrl);
            if (file == null) {
                throw new RuntimeException("存在无效附件，请重新上传");
            }
            if (!Objects.equals(file.getUserId(), userId)) {
                throw new RuntimeException("只能提交你自己上传的附件");
            }
            if (!SysFile.RefType.TRUST_REQUEST_ATTACHMENT.equals(file.getRefType())) {
                throw new RuntimeException("附件类型不合法，请重新上传");
            }
        }

        return normalized;
    }

    private String normalizeReviewNote(String reviewNote) {
        return StringUtils.hasText(reviewNote) ? reviewNote.trim() : null;
    }

    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            if (user != null && user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    private Map<Long, List<SysFile>> loadAttachmentMap(Collection<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }

        List<SysFile> files = sysFileService.list(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getRefType, SysFile.RefType.TRUST_REQUEST_ATTACHMENT)
                .in(SysFile::getRefId, requestIds)
                .ne(SysFile::getStatus, SysFile.Status.DELETED)
                .orderByAsc(SysFile::getId));
        if (files.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<SysFile>> map = new HashMap<>();
        for (SysFile file : files) {
            if (file == null || file.getRefId() == null) {
                continue;
            }
            map.computeIfAbsent(file.getRefId(), key -> new ArrayList<>()).add(file);
        }
        return map;
    }

    private List<SysFile> loadAttachmentsByRequestId(Long requestId) {
        if (requestId == null) {
            return List.of();
        }
        return loadAttachmentMap(Set.of(requestId)).getOrDefault(requestId, List.of());
    }

    private Set<Long> optionalIdSet(Long id) {
        if (id == null) {
            return Collections.emptySet();
        }
        return Set.of(id);
    }

    private TrustRequestVO buildTrustRequestVO(
            UserTrustRequest request,
            Map<Long, User> users,
            Map<Long, User> reviewers,
            Map<Long, List<SysFile>> attachments
    ) {
        User applicant = request == null ? null : users.get(request.getUserId());
        User reviewer = request == null || request.getReviewedBy() == null ? null : reviewers.get(request.getReviewedBy());
        List<SysFile> attachmentList = request == null || attachments == null
                ? List.of()
                : attachments.getOrDefault(request.getId(), List.of());
        return buildTrustRequestVO(request, applicant, reviewer, attachmentList);
    }

    private TrustRequestVO buildTrustRequestVO(
            UserTrustRequest request,
            User applicant,
            User reviewer,
            List<SysFile> attachments
    ) {
        TrustRequestVO vo = trustRequestConverter.toVO(request, applicant, reviewer);
        if (vo == null) {
            return null;
        }

        List<TrustRequestAttachmentVO> attachmentVOs = attachments == null || attachments.isEmpty()
                ? List.of()
                : trustRequestConverter.toAttachmentVOList(attachments);
        vo.setAttachments(attachmentVOs);
        return vo;
    }

    private void sendAdminNotificationEmail(
            User applicant,
            UserTrustRequest request,
            List<SysFile> attachments,
            String approveToken
    ) {
        String adminEmail = resolveAdminEmail();
        if (!StringUtils.hasText(adminEmail)) {
            log.warn("未配置管理员通知邮箱，跳过受信申请邮件通知");
            return;
        }

        String applicantName = StringUtils.hasText(applicant.getNickname()) ? applicant.getNickname() : applicant.getUsername();
        String approveUrl = joinUrl(
                backendBaseUrl,
                "/trust-requests/email-approve?token=" + URLEncoder.encode(approveToken, StandardCharsets.UTF_8)
        );
        String adminUrl = joinUrl(frontendBaseUrl, "/admin?tab=trust-requests");

        Map<String, String> variables = new LinkedHashMap<>(mailTemplateSupport.buildBrandVariables());
        variables.put("mailEyebrow", "Trust Request");
        variables.put("mailTitle", "新的受信任用户申请");
        variables.put("mailLead", "有一位读者提交了受信任用户申请，下面是本次申请的核心信息。");
        variables.put("applicantName", mailTemplateSupport.safeText(applicantName));
        variables.put("username", mailTemplateSupport.safeText(applicant.getUsername()));
        variables.put("applicantAvatarVisual", buildApplicantAvatarVisual(applicant, applicantName));
        variables.put("contactEmail", mailTemplateSupport.safeText(
                StringUtils.hasText(applicant.getEmail()) ? applicant.getEmail() : DEFAULT_EMPTY_CONTACT_EMAIL
        ));
        variables.put("createTime", mailTemplateSupport.safeText(formatEmailTime(request.getCreateTime())));
        variables.put("reasonHtml", mailTemplateSupport.safeText(request.getReason()).replace("\n", "<br/>"));
        variables.put("attachmentHtml", buildAttachmentHtml(attachments));
        variables.put("approveUrl", mailTemplateSupport.safeAttribute(approveUrl));
        variables.put("adminUrl", mailTemplateSupport.safeAttribute(adminUrl));

        String html = mailTemplateSupport.render(ADMIN_NOTIFICATION_TEMPLATE, variables);
        emailService.sendHtmlEmail(
                adminEmail,
                MAIL_SUBJECT_PREFIX + "新的受信任用户申请 - " + mailTemplateSupport.safeText(applicant.getUsername()),
                html
        );
    }

    private void sendApplicantResultEmail(User applicant, boolean approved, String reviewNote) {
        if (applicant == null || !StringUtils.hasText(applicant.getEmail())) {
            return;
        }

        String title = approved ? "你的受信任用户申请已通过" : "你的受信任用户申请未通过";
        Map<String, String> variables = new LinkedHashMap<>(mailTemplateSupport.buildBrandVariables());
        variables.put("mailEyebrow", approved ? "Approved" : "Rejected");
        variables.put("title", mailTemplateSupport.safeText(title));
        variables.put("resultTone", approved ? "申请已通过" : "申请未通过");
        variables.put("displayName", mailTemplateSupport.safeText(
                StringUtils.hasText(applicant.getNickname()) ? applicant.getNickname() : applicant.getUsername()
        ));
        variables.put("resultMessage", mailTemplateSupport.safeText(
                approved
                        ? "你的申请已经审核通过，现在可以查看更多知友可见的内容了。"
                        : "这次申请暂未通过，你可以根据审核说明调整后再次申请。"
        ));
        variables.put("reviewNote", mailTemplateSupport.safeText(
                StringUtils.hasText(reviewNote) ? reviewNote : DEFAULT_EMPTY_REVIEW_NOTE
        ).replace("\n", "<br/>"));

        String html = mailTemplateSupport.render(APPLICANT_RESULT_TEMPLATE, variables);
        emailService.sendHtmlEmail(applicant.getEmail(), MAIL_SUBJECT_PREFIX + title, html);
    }

    private String resolveAdminEmail() {
        return StringUtils.hasText(mailUsername) ? mailUsername.trim() : null;
    }

    private String buildAttachmentHtml(List<SysFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return DEFAULT_EMPTY_ATTACHMENT_HTML;
        }

        List<String> items = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            SysFile file = attachments.get(i);
            String url = file.getFileUrl();
            String label = buildAttachmentLabel(file, i + 1);
            String originalName = StringUtils.hasText(file.getFileOriginalName()) ? file.getFileOriginalName() : label;
            String fileMeta = buildAttachmentMeta(file);
            items.add("""
                    <li style="margin-bottom: 10px;">
                      <div style="padding:12px 12px; border-radius:14px; background:rgba(255,243,248,0.76); border:1px solid rgba(245,155,188,0.16);">
                        <a href="%s" style="display:inline-block; padding:7px 12px; border-radius:999px; background:#ffffff; color:#c55f8c; text-decoration:none; font-size:13px; font-weight:700; border:1px solid rgba(245,155,188,0.18);">打开附件</a>
                        <div style="margin-top:8px; font-size:12px; line-height:1.7; color:#5f5367; word-break:break-all; overflow-wrap:anywhere; white-space:normal;">%s</div>
                        <div style="margin-top:4px; font-size:11px; line-height:1.6; color:#8b7990;">%s</div>
                      </div>
                    </li>
                    """.formatted(
                    mailTemplateSupport.safeAttribute(url),
                    mailTemplateSupport.safeText(originalName),
                    mailTemplateSupport.safeText(fileMeta)
            ));
        }
        return "<ul style=\"padding-left: 0; margin: 0; list-style: none;\">" + String.join("", items) + "</ul>";
    }

    private String buildAttachmentLabel(SysFile file, int index) {
        if (file == null) {
            return "查看附件 " + index;
        }

        String candidate = StringUtils.hasText(file.getFileOriginalName()) ? file.getFileOriginalName() : file.getFileName();
        if (!StringUtils.hasText(candidate)) {
            return "查看附件 " + index;
        }
        return candidate.length() > 28 ? candidate.substring(0, 28) + "..." : candidate;
    }

    private String buildAttachmentMeta(SysFile file) {
        if (file == null) {
            return "";
        }

        List<String> parts = new ArrayList<>(2);
        if (file.getFileSize() != null && file.getFileSize() > 0) {
            parts.add(formatFileSize(file.getFileSize()));
        }
        if (StringUtils.hasText(file.getContentType())) {
            parts.add(file.getContentType().trim());
        }
        return parts.isEmpty() ? "系统文件" : String.join(" · ", parts);
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }

    private String buildApplicantAvatarVisual(User applicant, String applicantName) {
        String avatarUrl = resolveEmailImageUrl(applicant == null ? null : applicant.getAvatar());
        if (StringUtils.hasText(avatarUrl)) {
            return """
                    <div style="width: 76px; height: 76px; border-radius: 26px; padding: 4px; background: linear-gradient(135deg, rgba(216,102,146,0.82), rgba(238,186,208,0.62)); box-shadow: 0 14px 30px rgba(216,102,146,0.18);">
                      <img src="%s" alt="%s" style="display:block; width:100%%; height:100%%; border-radius: 22px; object-fit: cover; background:#ffffff;" />
                    </div>
                    """.formatted(mailTemplateSupport.safeAttribute(avatarUrl), mailTemplateSupport.safeAttribute(applicantName));
        }

        String initial = extractInitial(applicantName);
        return """
                <div style="width: 76px; height: 76px; border-radius: 26px; background: linear-gradient(135deg, rgba(216,102,146,0.90), rgba(240,182,208,0.72)); box-shadow: 0 14px 30px rgba(216,102,146,0.18); display:flex; align-items:center; justify-content:center; color:#ffffff; font-size:28px; font-weight:800;">
                  %s
                </div>
                """.formatted(mailTemplateSupport.safeText(initial));
    }

    private String resolveEmailImageUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return joinUrl(frontendBaseUrl, url);
        }
        return null;
    }

    private String extractInitial(String value) {
        String text = StringUtils.hasText(value) ? value.trim() : "C";
        return text.substring(0, 1).toUpperCase();
    }

    private String buildResultHtml(String title, String message, boolean success) {
        String badgeColor = success ? "#16a34a" : "#dc2626";
        Map<String, String> brandVariables = mailTemplateSupport.buildBrandVariables();
        String siteName = brandVariables.getOrDefault("siteName", "Chen404 Blog");
        String siteDescription = brandVariables.getOrDefault("siteDescription", "一个写下技术，也收藏温柔日常的小小角落");
        String brandVisual = brandVariables.getOrDefault("brandVisual", "");
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                </head>
                <body style="margin:0; font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif; background: radial-gradient(circle at top, #fff9fb 0%%, #fcfbfd 42%%, #f6f5fb 100%%); padding: 24px; color: #1f2937;">
                  <div style="max-width: 560px; margin: 0 auto;">
                    <div style="margin-bottom: 18px;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="border-collapse:collapse;">
                        <tr>
                          <td style="width:64px; vertical-align:middle; padding-right:12px;">%s</td>
                          <td style="vertical-align:middle; text-align:left;">
                            <div style="font-size:18px; line-height:1.25; color:#5b475c; font-weight:800;">%s</div>
                            <div style="margin-top:4px; font-size:12px; line-height:1.7; color:#9a8899;">%s</div>
                          </td>
                        </tr>
                      </table>
                      <div style="margin-top:14px; height:1px; background:linear-gradient(90deg, rgba(227,198,215,0), rgba(227,198,215,0.92) 20%%, rgba(227,198,215,0.92) 80%%, rgba(227,198,215,0));"></div>
                    </div>
                    <div style="background:#ffffff; border:1px solid rgba(224,197,212,0.36); border-radius:24px; padding:32px 24px; box-shadow:0 16px 42px rgba(102,83,112,0.08); text-align: center;">
                      <div style="display: inline-block; min-width: 92px; padding: 7px 16px; border-radius: 999px; background: %s; color: #ffffff; font-size: 13px; font-weight: 700;">%s</div>
                      <h2 style="margin: 18px 0 10px; font-size: 28px; line-height: 1.25; color: #241b2f;">%s</h2>
                      <p style="margin: 0; line-height: 1.85; color: #64576b; font-size: 15px;">%s</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                mailTemplateSupport.safeAttribute(title),
                brandVisual,
                siteName,
                siteDescription,
                badgeColor,
                success ? "Success" : "Failed",
                mailTemplateSupport.safeText(title),
                mailTemplateSupport.safeText(message)
        );
    }

    private String joinUrl(String baseUrl, String path) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "";
        String suffix = StringUtils.hasText(path) ? path.trim() : "";
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base + suffix.substring(1);
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private String formatEmailTime(LocalDateTime value) {
        LocalDateTime target = value == null ? LocalDateTime.now() : value;
        return EMAIL_TIME_FORMATTER.format(target);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
