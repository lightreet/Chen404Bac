package com.chen404.controller;

import com.chen404.exception.ForbiddenException;
import com.chen404.exception.GlobalExceptionHandler;
import com.chen404.service.TravelMobileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证新增 HTTP 批次契约、凭证传递和禁止缓存，不启动外部服务。 */
class TravelMobileUploadControllerTest {
    private static final String SESSION = "11111111-1111-1111-1111-111111111111";
    private static final String BATCH = "batch-request-0001";
    private static final String TOKEN = "test-only-upload-token";
    private TravelMobileUploadService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(TravelMobileUploadService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TravelMobileUploadController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void statusShouldPassBatchHeartbeatAndPreventCaching() throws Exception {
        mvc.perform(get("/upload/travel-mobile/public/" + SESSION).param("batchId", BATCH)
                        .header("X-Upload-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).mobileStatus(SESSION, TOKEN, BATCH);
    }

    @Test
    void batchStartAndEndShouldPassOnlyDedicatedUploadCredential() throws Exception {
        String path = "/upload/travel-mobile/public/" + SESSION + "/batches/" + BATCH;
        mvc.perform(post(path).header("X-Upload-Token", TOKEN)).andExpect(status().isOk());
        mvc.perform(delete(path).header("X-Upload-Token", TOKEN)).andExpect(status().isOk());
        verify(service).beginBatch(SESSION, TOKEN, BATCH);
        verify(service).endBatch(SESSION, TOKEN, BATCH);
    }

    @Test
    void missingUploadCredentialShouldRemainForbidden() throws Exception {
        doThrow(new ForbiddenException("上传凭证无效")).when(service).beginBatch(SESSION, null, BATCH);
        mvc.perform(post("/upload/travel-mobile/public/" + SESSION + "/batches/" + BATCH))
                .andExpect(status().isForbidden());
    }
}
