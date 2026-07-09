# 同学B — 黄健熙 — 用户权限与API体系 环境要求

> **职责范围：** Spring Boot common 公共模块（统一响应、异常处理、Security 配置、JWT 工具、Redis 工具）、用户认证模块（注册/登录/Token 刷新/个人信息）、RBAC 权限体系（用户/角色/权限管理）、Vue 3 前端骨架搭建（登录页、注册页、主布局、路由守卫、Axios 拦截器）。

---

## 一、全局通用环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **Git** | >= 2.30 | 版本控制 | `git --version` |
| **Docker** | >= 24.0 | 容器运行时 | `docker --version` |
| **Docker Compose** | >= v2.20 | 多容器编排 | `docker compose version` |
| **Postman** 或 **Apifox** | 最新版 | 接口调试 | 启动后能发送请求即可 |
| **VS Code** | 最新版 | 前端开发 + 通用编辑器 | — |
| **Navicat** 或 **DBeaver** | 任意 | 用户表数据验证 | 能连接 `localhost:3306` 即可 |

### 项目仓库克隆

```bash
git clone <仓库地址>
cd 实训项目
```

### Docker 环境验证

```bash
docker run --rm hello-world
```

---

## 二、专属基础环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **JDK** | >= 11（建议 17） | Java 编译运行 | `java --version` |
| **Maven** | >= 3.8 | 项目构建与依赖管理 | `mvn --version` |
| **Node.js** | >= 18 LTS | 前端构建 | `node --version` |
| **npm** | >= 9 | 前端包管理 | `npm --version` |

---

## 三、后端技术栈

| 层面 | 技术 | 用途 |
|------|------|------|
| 应用框架 | Spring Boot 2.7+ | 整体后端框架 |
| 安全框架 | Spring Security | 认证与授权 |
| 令牌机制 | JWT（jjwt 库） | 无状态 Token 签发与校验（accessToken 2h + refreshToken 7d） |
| 密码加密 | BCrypt（spring-security-crypto） | 密码哈希 |
| 持久层 | Spring Data JPA + Hibernate | sys_user / sys_role / sys_permission 及关联表 ORM |
| 缓存 | Redis 7（Spring Data Redis） | Token 缓存 + 登录限流 |
| 数据库 | MySQL 8.0 | 用户数据持久化 |
| 日志 | AOP（spring-aop） | `@Log` 注解自动记录关键操作 |

### 负责的数据库表

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户（username / password / email / college / status） |
| `sys_role` | 角色定义（5 个角色：管理员 / 数据分析员 / 教师 / 学院管理员 / 学生） |
| `sys_permission` | 权限定义（菜单 / 按钮 / API 三级粒度） |
| `sys_user_role` | 用户-角色关联 |
| `sys_role_permission` | 角色-权限关联 |

---

## 四、前端技术栈

| 层面 | 技术 | 用途 |
|------|------|------|
| 框架 | Vue 3（Composition API） | 前端框架 |
| 构建工具 | Vite | 开发服务器 + 打包 |
| UI 组件库 | Element Plus | 表单、表格、布局 |
| 状态管理 | Pinia | Token / 用户信息 / 侧边栏状态 |
| 路由 | Vue Router 4 | 页面路由 + 导航守卫（beforeEach 检查 Token） |
| HTTP 客户端 | Axios | API 请求 + 请求拦截器（附加 Token）+ 响应拦截器（401 自动刷新/跳转登录） |
| 图标 | Element Plus Icons | 菜单图标 |

### 前期页面

| 路由 | 组件 | 认证要求 | 说明 |
|------|------|---------|------|
| `/login` | LoginView | 否 | 账号密码登录，已登录自动跳转 `/` |
| `/register` | RegisterView | 否 | 注册页 |
| `/` | MainLayout > HomeView | 是 | 主布局骨架 + 欢迎页，中期替换为数据大屏 |

---

## 五、API 接口清单（B 负责实现）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 登录，返回 accessToken + refreshToken + userInfo | 否 |
| POST | `/api/auth/refresh` | 刷新 Token | 否（凭 refreshToken） |
| GET | `/api/auth/me` | 获取当前用户信息 | 是 |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": 1710000000000
}
```

---

## 六、开发工具

| 工具 | 用途 |
|------|------|
| **IntelliJ IDEA**（Ultimate 或 Community + Spring 插件） | Spring Boot 后端开发 |
| **VS Code** + Volar 插件 | Vue 3 前端开发 |
| **Postman** | 认证接口自测 |
| **Navicat / DBeaver** | 用户表数据验证 |

---

## 七、前端项目结构（前期）

```
frontend/
├── package.json
├── vite.config.js
├── index.html
└── src/
    ├── main.js                       # 入口，注册 Element Plus + Router + Pinia
    ├── App.vue                       # 根组件
    ├── router/index.js               # 路由表 + 导航守卫
    ├── store/
    │   ├── user.js                   # Pinia：Token、用户信息、登录状态
    │   └── app.js                    # Pinia：侧边栏折叠、面包屑
    ├── utils/request.js              # Axios 实例：baseURL、拦截器
    ├── layout/MainLayout.vue         # 主布局：顶部导航 + 左侧菜单骨架
    ├── views/
    │   ├── login/LoginView.vue       # 登录页
    │   ├── register/RegisterView.vue # 注册页
    │   └── home/HomeView.vue         # 空白主页
    └── components/common/
        └── PageContainer.vue         # 页面容器组件
```

---

## 八、环境验证清单

- [ ] `java --version` 输出 11 或 17
- [ ] `mvn --version` 正常
- [ ] `node --version` ≥ 18，`npm --version` ≥ 9
- [ ] `cd backend && mvn clean compile` 能通过（等 A 建好骨架后）
- [ ] `cd frontend && npm install && npm run dev` 能启动 Vite 开发服务器
- [ ] Postman `POST /api/auth/register` 能成功注册账号，MySQL `sys_user` 表有记录
- [ ] Postman `POST /api/auth/login` 返回 accessToken + refreshToken + userInfo
- [ ] Postman `GET /api/auth/me` 携带 Token 返回当前用户信息
- [ ] 浏览器访问 `http://localhost` → 显示登录页 → 登录后跳转主页
- [ ] 清除 LocalStorage 后刷新页面，自动跳转到登录页（401 拦截）
- [ ] 刷新页面不退出（Token 持久化到 LocalStorage）
