package com.career.platform.position.controller;

import com.career.platform.common.GlobalExceptionHandler;
import com.career.platform.common.PageResponse;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.service.PositionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PositionController.class)
@Import(GlobalExceptionHandler.class)
class PositionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PositionService service;

    @Test
    @WithMockUser
    void wrapsPageUsingTheSharedContract() throws Exception {
        when(service.search(any())).thenReturn(new PageResponse<PositionResponse>(List.of(), 0, 0, 1, 20));
        mvc.perform(get("/api/positions").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @WithMockUser
    void mapsValidationErrorsToCode400() throws Exception {
        when(service.latest(0)).thenThrow(new IllegalArgumentException("limit 必须在 1 到 100 之间"));
        mvc.perform(get("/api/positions/hot").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/positions"))
                .andExpect(status().isUnauthorized());
    }
}
