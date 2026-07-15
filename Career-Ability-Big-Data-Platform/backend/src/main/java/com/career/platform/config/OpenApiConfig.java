package com.career.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String API_KEY_AUTH = "apiKeyAuth";

    @Bean
    public OpenAPI platformOpenApi() {
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access Token returned by /api/auth/login or /api/auth/refresh");
        SecurityScheme apiKeyAuth = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key")
                .description("API Key shown once when it is created");

        return new OpenAPI()
                .info(new Info()
                        .title("职业能力大数据服务平台 API")
                        .description("认证、权限管理、采集、分析、画像、推荐、报告和第三方开放接口。错误统一使用 ApiResponse 信封；开放接口同时需要 Bearer Token 与 X-API-Key。")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerAuth)
                        .addSecuritySchemes(API_KEY_AUTH, apiKeyAuth));
    }

    @Bean
    public OpenApiCustomiser openApiSecurityCustomiser() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (path.startsWith("/api/open/v1/")) {
                SecurityRequirement doubleAuthentication = new SecurityRequirement()
                        .addList(BEARER_AUTH)
                        .addList(API_KEY_AUTH);
                pathItem.readOperations().forEach(operation -> operation.setSecurity(List.of(doubleAuthentication)));
            }
            pathItem.readOperations().forEach(operation -> documentCommonResponses(operation,
                    path.startsWith("/api/open/v1/")));
        });
    }

    private void documentCommonResponses(Operation operation, boolean openApi) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        addResponse(responses, "400", "请求参数或请求体不合法");
        addResponse(responses, "401", "缺少、过期或已撤销的认证凭证");
        addResponse(responses, "403", "当前主体没有访问权限");
        addResponse(responses, "500", "服务端内部错误，不返回堆栈或敏感字段");
        if (openApi) {
            io.swagger.v3.oas.models.responses.ApiResponse response =
                    new io.swagger.v3.oas.models.responses.ApiResponse().description("API Key 调用频率超限");
            response.addHeaderObject("X-RateLimit-Limit", rateLimitHeader("当前 API Key 的速率限制"));
            response.addHeaderObject("X-RateLimit-Remaining", rateLimitHeader("当前窗口剩余请求数"));
            responses.putIfAbsent("429", response);
        }
    }

    private void addResponse(ApiResponses responses, String code, String description) {
        responses.putIfAbsent(code, new io.swagger.v3.oas.models.responses.ApiResponse().description(description));
    }

    private Header rateLimitHeader(String description) {
        return new Header().description(description).schema(new Schema<>().type("integer"));
    }
}
