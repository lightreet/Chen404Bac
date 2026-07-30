package com.chen404.service;

import com.chen404.domain.dto.ReaderBookUpdateCommand;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderBookPreviewVO;
import com.chen404.domain.dto.ReaderChapterVO;
import com.chen404.domain.dto.ReaderPreferenceCommand;
import com.chen404.domain.dto.ReaderPreferenceVO;
import com.chen404.domain.dto.ReaderProgressCommand;
import com.chen404.domain.dto.ReaderProgressVO;
import com.chen404.domain.dto.ReaderSearchResultVO;
import com.chen404.domain.dto.ReaderTocItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 私人书架、阅读正文、阅读进度与阅读偏好服务。
 */
public interface ReaderLibraryService {

    /**
     * 预解析小说元数据，供导入表单自动回填。
     */
    ReaderBookPreviewVO previewBook(MultipartFile file, String encoding, Long userId);

    ReaderBookVO importBook(
            MultipartFile file,
            String title,
            String author,
            String description,
            String encoding,
            String visibility,
            Long coverFileId,
            Long userId
    );

    List<ReaderBookVO> listBooks(Long userId);

    ReaderBookVO getBook(Long bookId, Long userId);

    ReaderBookVO updateBook(Long bookId, ReaderBookUpdateCommand command, Long userId);

    void deleteBook(Long bookId, Long userId);

    List<ReaderTocItemVO> getToc(Long bookId, Long userId);

    ReaderChapterVO getChapter(Long bookId, Long chapterId, Long userId);

    List<ReaderSearchResultVO> search(Long bookId, String keyword, Long userId);

    ReaderProgressVO getProgress(Long bookId, Long userId);

    ReaderProgressVO saveProgress(Long bookId, ReaderProgressCommand command, Long userId);

    void clearProgress(Long bookId, Long userId);

    ReaderPreferenceVO getPreference(Long userId);

    ReaderPreferenceVO savePreference(ReaderPreferenceCommand command, Long userId);

    ReaderAssetPayload getAsset(Long bookId, Long assetId, Long userId);

    record ReaderAssetPayload(String fileName, String mediaType, byte[] data, boolean publicVisible) {
    }
}
