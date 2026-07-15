package com.career.platform.openapi.controller;

import com.career.platform.openapi.dto.CreateApiKeyRequest;
import com.career.platform.openapi.dto.UpdateApiKeyStatusRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiKeyManagementControllerTest {

    @Test
    void protectsEveryKeyManagementEndpointWithTheDedicatedPermission() throws Exception {
        assertPermission("list", String.class, int.class, int.class);
        assertPermission("create", CreateApiKeyRequest.class);
        assertPermission("updateStatus", Long.class, UpdateApiKeyStatusRequest.class);
        assertPermission("delete", Long.class);
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ApiKeyManagementController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('api:key:manage')", preAuthorize.value());
    }
}
