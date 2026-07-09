# B（黄健熙）前期开工计划 — 用户权限与 API 体系

> A 第1天结束后你就能开工。你的活分两条线：后端安全 + 前端骨架，两条线都直接影响 C 和 D。

---

## 你的开工时机

```
A 第1天结束 → 你开工（拿到 init.sql 建表脚本）
A 第4天结束 → 你接入 Docker 开发环境
```

**你在 A 的第2天即可开始写代码。**前期一共 6 天有效工作时间。

---

## 第1天（A 的第2天）：common 模块 + JPA 实体

**上午：Spring Boot 工程初始化**

- 创建 `backend/` 父 POM + `common/` 子模块
- 写 `ApiResponse.java`（统一响应 `{code, message, data, timestamp}`）
- 写 `BusinessException.java` + `GlobalExceptionHandler.java`（@RestControllerAdvice）
- 写 `RedisUtil.java`（封装 StringRedisTemplate 常用操作）
- 父 POM 用 `<dependencyManagement>` 统一版本

**下午：认证相关 JPA 实体**

- 对着 A 的建表脚本写实体类：
  - `SysUser.java`（对应 sys_user 表）
  - `SysRole.java`（对应 sys_role 表）
  - `SysPermission.java`（对应 sys_permission 表）
- 写对应的 Spring Data JPA Repository
- 实体和 Repository 放在 `module-auth/` 下

**今天的交付物：**
- Spring Boot 工程可编译启动
- `ApiResponse` + 全局异常处理器就绪
- 认证实体 + Repository 可用了
- **C 和 D 可以参考你的 common 模块结构和实体写法**

---

## 第2天：Spring Security + JWT 核心

**上午：JWT 工具类**

- 写 `JwtTokenProvider.java`：
  - `generateAccessToken(userId)` → 2h 有效期
  - `generateRefreshToken(userId)` → 7d 有效期
  - `validateToken(token)` → 校验签名 + 过期
  - `getUserIdFromToken(token)` → 解析用户 ID
- 写 `UserDetailsServiceImpl.java`：从数据库加载用户 + 角色 + 权限

**下午：Security 配置**

- 写 `SecurityConfig.java`：
  - 放行 `/api/auth/login`、`/api/auth/register`
  - 其余接口要求认证
  - 注入自定义 Filter
- 写 `JwtAuthenticationFilter.java`（继承 OncePerRequestFilter）：
  - 从 Header 取 Token
  - 校验 → 解析 → 存入 SecurityContext

**今天的交付物：**
- JWT 签发 + 校验逻辑完成
- Security 过滤器链路跑通
- Postman 不带 Token 访问受保护接口 → 401

---

## 第3天：认证接口（登录/注册/刷新/查自己）

**全天：AuthController**

- `POST /api/auth/register`：用户名 4-20 位、密码 6-20 位、BCrypt 加密存库
- `POST /api/auth/login`：验密 → 签发双 Token → 返回用户信息 + 角色 + 权限列表
- `POST /api/auth/refresh`：用 refreshToken 换新的 accessToken
- `GET /api/auth/me`：从 Token 解析 user_id → 查库返回当前用户信息

**验证方式：** Postman 逐个接口调通

**今天的交付物：**
- 4 个认证接口全部可用
- Postman 可走通：注册 → 登录 → 拿 Token → 查自己 → Token 过期后刷新
- **C 和 D 可以引用你的 JWT 工具类，在自己的接口上获取当前用户了**

---

## 第4天：前端骨架 + 登录页

**上午：Vue 3 工程初始化**

- `npm create vite@latest frontend`，装 Element Plus + Vue Router + Pinia + Axios
- 写 `src/utils/request.js`（Axios 实例 + 请求拦截器自动带 Token + 响应拦截器 401 处理 + refreshToken 自动刷新）
- 写 `src/store/user.js`（Pinia：存储 token/userInfo/permissions）
- 写 `src/router/index.js`（路由表：`/login`、`/register`、`/`）
- 写路由守卫：没 Token → 强制跳 `/login`

**下午：登录页**

- 写 `LoginView.vue`：账号 / 密码 / 登录按钮 / 去注册链接
- 登录成功 → 存 Token → 跳主页
- 写 `RegisterView.vue`：账号 / 密码 / 角色选择下拉框 / 注册按钮
- 写 `MainLayout.vue`（主布局骨架：顶栏 + 左侧空菜单 + `<router-view />`）
- 写 `HomeView.vue`（空白页："欢迎使用职业能力大数据服务平台"）
- 写 `PageContainer.vue`（公共组件：标题 + 内容区，给 C/D 中期复用）

**今天的交付物：**
- `npm run dev` 能打开前端
- 登录页 → 输入账号密码 → 调后端 → 拿到 Token → 跳主页 → 刷新不退出
- 清除 LocalStorage → 自动跳回登录页
- **C 和 D 的前端页面可以在这个骨架里开发了**

---

## 第5天：登录限流 + 操作日志 AOP

**上午：Redis 滑动窗口登录限流**

- 同一个 IP/用户名，5 次登录失败 → 锁定 15 分钟
- Key 设计：`login:fail:{username}`，用 Redis 的 ZSET 做滑动窗口
- 登录成功 → 清空计数

**下午：AOP 操作日志**

- 自定义 `@Log` 注解（参数：模块名、操作类型）
- 写切面类：拦截带 `@Log` 的方法 → 记录（操作人/时间/IP/操作内容）→ 写 MySQL 日志表
- 前期先做注解和切面，日志表中期再建（DDL 由 A 补充），前期打到文件即可

**今天的交付物：**
- 暴力破解防护生效
- `@Log` 注解可用，C 和 D 可以在自己的关键方法上加了

---

## 第6天：等待联调 + 辅助 C/D 接入

**今天的主要工作：**

- 帮 C 和 D 在各自的模块中引入 common 依赖
- 帮 C 和 D 在他们的 Controller 上加 Security 注解
- 确认 C 的岗位接口带 Token 能正常访问
- 确认 D 的画像接口从 Token 中正确获取 user_id
- 配合 A 做全链路验收（12 项中的 5、6、7、11、12 项由你主导）

**今天的交付物：**
- C 和 D 的接口已接入 Security 认证
- 前端登录全流程可用
- 你负责的 5 项验收全部通过

---

## 你与其他人的协作节点

```
A第1天结束 → 你开工（有表结构了）
你第1天结束 → C、D 参考 common 模块结构
你第3天结束 → C、D 用 JwtTokenProvider 获取当前用户
你第4天结束 → C、D 在 Vue 骨架中开发各自页面
你第5天结束 → C、D 在方法上加 @Log 注解
你第6天     → 全员联调
```

**你的每一块都是地基——common 模块是后端地基，Vue 骨架是前端地基。质量直接影响 C 和 D 的开发效率。**
