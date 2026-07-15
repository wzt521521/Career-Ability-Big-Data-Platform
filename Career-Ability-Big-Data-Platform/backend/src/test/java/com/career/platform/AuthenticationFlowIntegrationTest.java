package com.career.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.career.platform.auth.entity.SysPermission;
import com.career.platform.auth.entity.SysRole;
import com.career.platform.auth.entity.SysUser;
import com.career.platform.auth.repository.SysOperationLogRepository;
import com.career.platform.auth.repository.SysPermissionRepository;
import com.career.platform.auth.repository.SysRoleRepository;
import com.career.platform.auth.repository.SysUserRepository;
import com.career.platform.openapi.repository.ApiCallLogRepository;
import com.career.platform.openapi.repository.ApiKeyRepository;
import com.career.platform.openapi.entity.ApiKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiCallLogRepository apiCallLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        apiCallLogRepository.deleteAll();
        apiKeyRepository.deleteAll();
        operationLogRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        seedAccessModel();
    }

    @Test
    void allowsLoginFromDocumentedDevelopmentOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://127.0.0.1:5173"))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"));
    }

    @Test
    void exposesUnauthenticatedLivenessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void completesAuthenticationAdministrationAndOpenApiFlow() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));

        postJson(
                        "/api/auth/register",
                        "{\"username\":\"student1\",\"password\":\"secret1\",\"roleCode\":\"ROLE_STUDENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("student1"));

        JsonNode studentLogin = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"student1\",\"password\":\"secret1\"}"));
        String studentAccessToken = studentLogin.get("accessToken").asText();
        String studentRefreshToken = studentLogin.get("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(studentAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("student1"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_STUDENT"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(studentAccessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        JsonNode adminLogin = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"));
        String adminAccessToken = adminLogin.get("accessToken").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(adminAccessToken))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        MvcResult createdKeyResult = postJson(
                        "/api/admin/api-keys",
                        "{\"appName\":\"integration-test\",\"rateLimit\":100}",
                        adminAccessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").isNotEmpty())
                .andReturn();
        String apiKey = objectMapper.readTree(createdKeyResult.getResponse().getContentAsString())
                .path("data")
                .path("apiKey")
                .asText();

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("available"));
        mockMvc.perform(get("/api/open/v1/positions")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
        mockMvc.perform(get("/api/open/v1/skills/hot")
                        .param("limit", "5")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope.value").value("PUBLIC_RECRUITMENT"));
        mockMvc.perform(get("/api/open/v1/cities/ranking")
                        .param("limit", "5")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/open/v1/salary/trends")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());

        JsonNode refreshed = responseData(postJson(
                        "/api/auth/refresh",
                        "{\"refreshToken\":\"" + studentRefreshToken + "\"}"));
        String refreshedAccessToken = refreshed.get("accessToken").asText();
        String refreshedRefreshToken = refreshed.get("refreshToken").asText();

        postJson(
                        "/api/auth/refresh",
                        "{\"refreshToken\":\"" + studentRefreshToken + "\"}")
                .andExpect(status().isUnauthorized());

        postJson(
                        "/api/auth/logout",
                        "{\"refreshToken\":\"" + refreshedRefreshToken + "\"}",
                        refreshedAccessToken)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(refreshedAccessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bindsEachOpenApiKeyToItsJwtOwnerBeforeControllerExecution() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        String apiKey = responseData(postJson(
                        "/api/admin/api-keys",
                        "{\"appName\":\"owner-binding\",\"rateLimit\":100}",
                        adminAccessToken))
                .get("apiKey")
                .asText();

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        postJson(
                        "/api/auth/register",
                        "{\"username\":\"keyother\",\"password\":\"secret1\",\"roleCode\":\"ROLE_STUDENT\"}")
                .andExpect(status().isOk());
        String otherAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"keyother\",\"password\":\"secret1\"}"))
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(otherAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/open/v1/positions")
                        .header("Authorization", bearer(otherAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void requiresAnApiKeyForOpenApiRequestsBehindAContextPath() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        String apiKey = responseData(postJson(
                        "/api/admin/api-keys",
                        "{\"appName\":\"context-path\",\"rateLimit\":100}",
                        adminAccessToken))
                .get("apiKey")
                .asText();

        mockMvc.perform(get("/gateway/api/open/v1/platform/status")
                        .contextPath("/gateway")
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/gateway/api/open/v1/platform/status")
                        .contextPath("/gateway")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("available"));
    }

    @Test
    void letsStudentsManageTheirOwnApiKeysWithoutApiAuditAccess() throws Exception {
        postJson(
                        "/api/auth/register",
                        "{\"username\":\"studentkey\",\"password\":\"secret1\",\"roleCode\":\"ROLE_STUDENT\"}")
                .andExpect(status().isOk());
        String studentAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"studentkey\",\"password\":\"secret1\"}"))
                .get("accessToken")
                .asText();
        JsonNode createdKey = responseData(postJson(
                "/api/admin/api-keys",
                "{\"appName\":\"student-open-api\",\"rateLimit\":100}",
                studentAccessToken));

        mockMvc.perform(get("/api/admin/api-keys")
                        .header("Authorization", bearer(studentAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/admin/api-call-logs")
                        .header("Authorization", bearer(studentAccessToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(studentAccessToken))
                        .header("X-API-Key", createdKey.get("apiKey").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void scopesApiKeyManagementAndCallHistoryToTheKeyOwner() throws Exception {
        String ownerAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        JsonNode createdKey = responseData(postJson(
                "/api/admin/api-keys",
                "{\"appName\":\"owner-only\",\"rateLimit\":100}",
                ownerAccessToken));

        SysRole adminRole = roleRepository.findByRoleCode("ROLE_ADMIN").orElseThrow();
        SysUser otherApiUser = new SysUser();
        otherApiUser.setUsername("otherapi");
        otherApiUser.setPassword(passwordEncoder.encode("secret1"));
        otherApiUser.setStatus(1);
        otherApiUser.setRoles(Set.of(adminRole));
        userRepository.save(otherApiUser);
        String otherAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"otherapi\",\"password\":\"secret1\"}"))
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/admin/api-keys")
                        .header("Authorization", bearer(otherAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(ownerAccessToken))
                        .header("X-API-Key", createdKey.get("apiKey").asText()))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/api-keys/{id}/status", createdKey.get("id").asLong())
                        .header("Authorization", bearer(otherAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/api-call-logs")
                        .header("Authorization", bearer(otherAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void locksAccountAfterFiveFailedLogins() throws Exception {
        postJson(
                        "/api/auth/register",
                        "{\"username\":\"limited1\",\"password\":\"secret1\",\"roleCode\":\"ROLE_STUDENT\"}")
                .andExpect(status().isOk());

        for (int attempt = 0; attempt < 5; attempt++) {
            postJson(
                            "/api/auth/login",
                            "{\"username\":\"limited1\",\"password\":\"wrong-password\"}")
                    .andExpect(status().isUnauthorized());
        }

        postJson(
                        "/api/auth/login",
                        "{\"username\":\"limited1\",\"password\":\"secret1\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void importsUsersFromExcelAndReportsInvalidRows() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/admin/users/import-template")
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentAsByteArray().length > 1000));

        MockMultipartFile workbook = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                userImportWorkbook());
        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(workbook)
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.importedRows").value(1))
                .andExpect(jsonPath("$.data.failedRows").value(2))
                .andExpect(jsonPath("$.data.errors.length()").value(2));

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.existsByUsername("batch001"));
    }

    @Test
    void rejectsUnsupportedAndOversizedUserImportUploads() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "users.xlsx", MediaType.TEXT_PLAIN_VALUE, "not an Excel workbook".getBytes());
        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(textFile)
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isBadRequest());

        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[(2 * 1024 * 1024) + 1]);
        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(oversized)
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2MB")));
    }

    @Test
    void rejectsPrivilegedSelfRegistrationAndDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer stale-invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk());

        postJson(
                        "/api/auth/register",
                        "{\"username\":\"student2\",\"password\":\"secret1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_STUDENT"));

        postJson(
                        "/api/auth/register",
                        "{\"username\":\"student2\",\"password\":\"secret1\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        postJson(
                        "/api/auth/register",
                        "{\"username\":\"admin02\",\"password\":\"secret1\",\"roleCode\":\"ROLE_ADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void preventsSelfAssignedCollegeAndUnauthorizedCollectManagement() throws Exception {
        postJson(
                        "/api/auth/register",
                        "{\"username\":\"student3\",\"password\":\"secret1\","
                                + "\"college\":\"Impersonated College\"}")
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNull(
                userRepository.findByUsername("student3").orElseThrow().getCollege());

        String studentAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"student3\",\"password\":\"secret1\"}"))
                .get("accessToken")
                .asText();

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", bearer(studentAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"Student Three\",\"college\":\"Other College\"}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNull(
                userRepository.findByUsername("student3").orElseThrow().getCollege());

        mockMvc.perform(get("/api/collect/source")
                        .header("Authorization", bearer(studentAccessToken)))
                .andExpect(status().isForbidden());
        postJson("/api/collect/source", "{}", studentAccessToken)
                .andExpect(status().isForbidden());

        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        mockMvc.perform(get("/api/collect/source")
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void immediatelyRejectsTokensAndLoginForDisabledUser() throws Exception {
        postJson(
                        "/api/auth/register",
                        "{\"username\":\"disabled1\",\"password\":\"secret1\"}")
                .andExpect(status().isOk());
        JsonNode login = responseData(postJson(
                "/api/auth/login",
                "{\"username\":\"disabled1\",\"password\":\"secret1\"}"));
        String accessToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();

        SysUser user = userRepository.findByUsername("disabled1").orElseThrow();
        user.setStatus(0);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isUnauthorized());
        postJson(
                        "/api/auth/refresh",
                        "{\"refreshToken\":\"" + refreshToken + "\"}")
                .andExpect(status().isForbidden());
        postJson(
                        "/api/auth/login",
                        "{\"username\":\"disabled1\",\"password\":\"secret1\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesApiKeyHashingStateExpirationAndRateLimit() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();

        JsonNode limitedKeyResponse = responseData(postJson(
                "/api/admin/api-keys",
                "{\"appName\":\"limited-key\",\"rateLimit\":1}",
                adminAccessToken));
        String limitedKey = limitedKeyResponse.get("apiKey").asText();
        ApiKey limitedEntity = apiKeyRepository.findById(limitedKeyResponse.get("id").asLong())
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(limitedKey, limitedEntity.getApiKeyHash());
        org.junit.jupiter.api.Assertions.assertEquals(64, limitedEntity.getApiKeyHash().length());

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", limitedKey))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        "0",
                        result.getResponse().getHeader("X-RateLimit-Remaining")));
        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", limitedKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        limitedEntity.setStatus(0);
        apiKeyRepository.saveAndFlush(limitedEntity);
        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", limitedKey))
                .andExpect(status().isForbidden());

        JsonNode expiringKeyResponse = responseData(postJson(
                "/api/admin/api-keys",
                "{\"appName\":\"expired-key\",\"rateLimit\":100}",
                adminAccessToken));
        ApiKey expiredEntity = apiKeyRepository.findById(expiringKeyResponse.get("id").asLong())
                .orElseThrow();
        expiredEntity.setExpireTime(LocalDateTime.now().minusMinutes(1));
        apiKeyRepository.saveAndFlush(expiredEntity);
        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", expiringKeyResponse.get("apiKey").asText()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/open/v1/platform/status")
                        .header("Authorization", bearer(adminAccessToken))
                        .header("X-API-Key", "not-a-valid-api-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesOpenPersonalDataWhenTheApiKeyOwnerLacksTheFeaturePermission() throws Exception {
        String accessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"api-only\",\"password\":\"api-only-secret\"}"))
                .get("accessToken")
                .asText();
        String apiKey = responseData(postJson(
                        "/api/admin/api-keys",
                        "{\"appName\":\"permission-negative\",\"rateLimit\":100}",
                        accessToken))
                .get("apiKey")
                .asText();

        mockMvc.perform(get("/api/open/v1/recommendations")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", bearer(accessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/open/v1/reports")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", bearer(accessToken))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void operationLogsDoNotPersistPasswords() throws Exception {
        String adminAccessToken = responseData(postJson(
                        "/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .get("accessToken")
                .asText();
        postJson(
                        "/api/admin/users",
                        "{\"username\":\"audited1\",\"password\":\"never-log-this\","
                                + "\"realName\":\"Audited User\",\"roleCodes\":[\"ROLE_STUDENT\"]}",
                        adminAccessToken)
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(operationLogRepository.findAll().stream()
                .allMatch(log -> log.getParams() == null
                        || (!log.getParams().contains("never-log-this")
                                && !log.getParams().contains("admin123"))));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body)
            throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(
            String path,
            String body,
            String accessToken) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode responseData(org.springframework.test.web.servlet.ResultActions action)
            throws Exception {
        MvcResult result = action.andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] userImportWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            String[] headers = {
                "username", "password", "realName", "email", "phone", "college", "roleCodes"
            };
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            addImportRow(sheet.createRow(1), "batch001", "secret1", "Valid User");
            addImportRow(sheet.createRow(2), "batch001", "secret1", "Duplicate User");
            addImportRow(sheet.createRow(3), "x", "short", "Invalid User");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void addImportRow(Row row, String username, String password, String realName) {
        row.createCell(0).setCellValue(username);
        row.createCell(1).setCellValue(password);
        row.createCell(2).setCellValue(realName);
        row.createCell(3).setCellValue(username + "@example.com");
        row.createCell(4).setCellValue("13800000000");
        row.createCell(5).setCellValue("Computer College");
        row.createCell(6).setCellValue("ROLE_STUDENT");
    }

    private void seedAccessModel() {
        SysPermission dashboard = permission("Dashboard", "dashboard:view", "menu", 1L);
        SysPermission userRead = permission("User read", "user:read", "menu", 2L);
        SysPermission userCreate = permission("User create", "user:create", "button", 3L);
        SysPermission userUpdate = permission("User update", "user:update", "button", 4L);
        SysPermission userDelete = permission("User delete", "user:delete", "button", 5L);
        SysPermission roleRead = permission("Role read", "role:read", "menu", 6L);
        SysPermission roleUpdate = permission("Role update", "role:update", "button", 7L);
        SysPermission logRead = permission("Log read", "log:read", "menu", 8L);
        SysPermission apiView = permission("API audit", "api:view", "menu", 9L);
        SysPermission apiKeyManage = permission("API Key management", "api:key:manage", "menu", 10L);
        SysPermission collectView = permission("Collection view", "collect:view", "menu", 10L);
        SysPermission collectToggle = permission("Collection management", "collect:toggle", "button", 11L);
        Set<SysPermission> allPermissions = new LinkedHashSet<>(permissionRepository.saveAll(Set.of(
                dashboard,
                userRead,
                userCreate,
                userUpdate,
                userDelete,
                roleRead,
                roleUpdate,
                logRead,
                apiView,
                apiKeyManage,
                collectView,
                collectToggle)));

        SysRole adminRole = role("Administrator", "ROLE_ADMIN", allPermissions);
        SysRole studentRole = role("Student", "ROLE_STUDENT", Set.of(dashboard, apiKeyManage));
        SysRole apiOnlyRole = role("API-only", "ROLE_API_ONLY", Set.of(apiKeyManage));
        roleRepository.saveAll(Set.of(adminRole, studentRole, apiOnlyRole));

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("Administrator");
        admin.setStatus(1);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        SysUser apiOnly = new SysUser();
        apiOnly.setUsername("api-only");
        apiOnly.setPassword(passwordEncoder.encode("api-only-secret"));
        apiOnly.setRealName("API Only");
        apiOnly.setStatus(1);
        apiOnly.setRoles(Set.of(apiOnlyRole));
        userRepository.save(apiOnly);
    }

    private SysPermission permission(String name, String code, String type, Long order) {
        SysPermission permission = new SysPermission();
        permission.setPermissionName(name);
        permission.setPermissionCode(code);
        permission.setParentId(0L);
        permission.setType(type);
        permission.setSortOrder(order.intValue());
        return permission;
    }

    private SysRole role(String name, String code, Set<SysPermission> permissions) {
        SysRole role = new SysRole();
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setDescription(name);
        role.setPermissions(new LinkedHashSet<>(permissions));
        return role;
    }
}
