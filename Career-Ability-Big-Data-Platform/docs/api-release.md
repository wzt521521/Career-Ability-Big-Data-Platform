# API 交付说明

## 1. 认证模型

- 普通业务接口：`Authorization: Bearer <accessToken>`。
- 开放 API：同时需要 `Authorization: Bearer <accessToken>` 与 `X-API-Key: <apiKey>`，且 API Key owner 必须与 Token 用户一致。
- 发行验收集合：`docs/postman/release-core-api.postman_collection.json`。
- 模块验收集合：`docs/postman/黄健熙-认证与开放API.postman_collection.json`。集合变量不再包含默认管理员账号或密码。

## 2. OpenAPI

运行后端后导出固定 OpenAPI JSON：

```bash
curl --fail http://127.0.0.1:8080/v3/api-docs -o release-artifacts/openapi.json
```

GitHub Actions 的 `compose` job 会在真实 Compose 栈启动后导出 `openapi.json` 并作为 `release-acceptance-artifacts` 上传。正式 GitHub Release 必须附带该文件。

## 3. 核心接口范围

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 注册、登录、刷新、登出、会话校验、密码修改 |
| 系统管理 | `/api/admin/users`, `/api/admin/roles`, `/api/admin/operation-logs` | 用户、角色权限、审计日志 |
| 采集管理 | `/api/collect/source`, `/api/collect/task`, `/api/collect/logs` | 数据源、任务、运行日志 |
| 岗位 | `/api/positions` | 分页查询、热门岗位、建议、对比、详情 |
| 统计分析 | `/api/stats`, `/api/dashboard` | 七维统计、大屏数据 |
| 画像 | `/api/profile` | 当前用户画像读写 |
| 推荐 | `/api/recommend` | TOP20 推荐与技能差距 |
| 报告 | `/api/reports` | 模板、异步生成、状态、预览、下载、删除 |
| 开放 API | `/api/open/v1` | 岗位、统计、画像、推荐、报告的双认证访问 |

## 4. 错误与限流

- 业务接口通用限流响应：HTTP `429`，返回 `code=429`。
- 开放 API Key 限流响应：HTTP `429`，同时暴露 `X-RateLimit-*` 头。
- 认证失败：HTTP `401`。
- 权限不足或跨用户访问：HTTP `403` 或不暴露存在性的 `404`。
- 生产错误响应不包含堆栈、绝对路径、Token、API Key 或数据库密码。
