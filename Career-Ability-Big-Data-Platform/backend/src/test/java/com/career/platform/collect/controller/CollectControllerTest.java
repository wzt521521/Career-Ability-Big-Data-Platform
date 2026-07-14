package com.career.platform.collect.controller;

import com.career.platform.collect.dto.CollectSourceResponse;
import com.career.platform.collect.dto.CollectSourceRequest;
import com.career.platform.collect.dto.CollectTaskRequest;
import com.career.platform.collect.dto.CollectTaskResponse;
import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;
import com.career.platform.collect.service.CollectLogService;
import com.career.platform.collect.service.CollectSourceService;
import com.career.platform.collect.service.CollectTaskRuntimeService;
import com.career.platform.collect.service.CollectTaskService;
import com.career.platform.common.GlobalExceptionHandler;
import com.career.platform.common.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CollectSourceController.class, CollectTaskController.class, CollectLogController.class})
@Import({GlobalExceptionHandler.class, MethodSecurityConfig.class})
class CollectControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CollectSourceService sourceService;

    @MockBean
    private CollectTaskService taskService;

    @MockBean
    private CollectTaskRuntimeService runtimeService;

    @MockBean
    private CollectLogService logService;

    @Test
    @WithMockUser(authorities = "collect:view")
    void rejectsOversizedCollectionPage() throws Exception {
        mvc.perform(get("/api/collect/source").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(sourceService);
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void createsSourceFromValidatedDto() throws Exception {
        when(sourceService.create(any(CollectSourceRequest.class))).thenReturn(sourceResponse());

        mvc.perform(post("/api/collect/source")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"招聘样本\",\"sourceType\":\"FILE\","
                                + "\"filePath\":\"/data/kaggle_jobs_500.csv\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sourceName").value("招聘样本"))
                .andExpect(jsonPath("$.data.filePath").value("/data/kaggle_jobs_500.csv"));

        verify(sourceService).create(any(CollectSourceRequest.class));
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void rejectsSourcePathOutsideManagedVolume() throws Exception {
        mvc.perform(post("/api/collect/source")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"非法路径\",\"sourceType\":\"FILE\","
                                + "\"filePath\":\"/etc/passwd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(sourceService);
    }

    @Test
    @WithMockUser(authorities = "collect:view")
    void doesNotAllowViewPermissionToCreateTask() throws Exception {
        mvc.perform(post("/api/collect/task")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTaskJson()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(authorities = "collect:view")
    void rejectsUnauthorizedInvalidSourceRequestBeforeValidation() throws Exception {
        mvc.perform(post("/api/collect/source")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sourceService);
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void rejectsInvalidCronBeforeTaskService() throws Exception {
        mvc.perform(post("/api/collect/task")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"taskName\":\"每日导入\","
                                + "\"cronExpression\":\"not-a-cron\",\"status\":\"SCHEDULED\",\"maxRetries\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void rejectsUnsupportedTaskStatusBeforeTaskService() throws Exception {
        mvc.perform(post("/api/collect/task")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"taskName\":\"每日导入\","
                                + "\"cronExpression\":\"0 0 2 * * *\",\"status\":\"DELETED\",\"maxRetries\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void acceptsLegacyTaskPayloadButDoesNotExposeEntity() throws Exception {
        when(taskService.create(any(CollectTaskRequest.class))).thenReturn(taskResponse());

        mvc.perform(post("/api/collect/task")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTaskJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.retryCount").value(0));

        verify(taskService).create(any(CollectTaskRequest.class));
    }

    @Test
    @WithMockUser(authorities = "collect:view")
    void rejectsInvalidLogPageSize() throws Exception {
        mvc.perform(get("/api/collect/log/by-task/1").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(logService);
    }

    @Test
    @WithMockUser(authorities = "collect:view")
    void rejectsInvalidLogTaskFilter() throws Exception {
        mvc.perform(get("/api/collect/log").param("taskId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(logService);
    }

    @Test
    @WithMockUser(authorities = "collect:toggle")
    void startsTaskThroughRuntimeService() throws Exception {
        when(runtimeService.run(8L)).thenReturn(taskResponse());

        mvc.perform(post("/api/collect/task/8/run").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        verify(runtimeService).run(8L);
    }

    private String validTaskJson() {
        // retryCount is a historical frontend field. The request DTO intentionally ignores it.
        return "{\"sourceId\":1,\"taskName\":\"每日导入\",\"cronExpression\":\"0 0 2 * * *\","
                + "\"status\":\"SCHEDULED\",\"maxRetries\":3,\"retryCount\":99}";
    }

    private CollectSourceResponse sourceResponse() {
        CollectSource source = new CollectSource();
        source.setId(7L);
        source.setSourceName("招聘样本");
        source.setSourceType("FILE");
        source.setFilePath("/data/kaggle_jobs_500.csv");
        source.setStatus(1);
        return CollectSourceResponse.from(source);
    }

    private CollectTaskResponse taskResponse() {
        CollectTask task = new CollectTask();
        task.setId(8L);
        task.setSourceId(1L);
        task.setTaskName("每日导入");
        task.setCronExpression("0 0 2 * * *");
        task.setStatus("SCHEDULED");
        task.setRetryCount(0);
        task.setMaxRetries(3);
        return CollectTaskResponse.from(task);
    }
}
