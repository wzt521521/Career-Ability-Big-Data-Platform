# 同学C — 张博源 — 数据分析与可视化 环境要求

> **职责范围：** 岗位查询 API（分页搜索、详情、热门岗位）、Spring Boot 岗位模块、后期多维度统计分析（岗位/薪资/学历/技能/地域/企业/趋势 7 大维度）、Vue 3 数据大屏与可视化图表（ECharts）。

---

## 一、全局通用环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **Git** | >= 2.30 | 版本控制 | `git --version` |
| **Docker** | >= 24.0 | 容器运行时 | `docker --version` |
| **Docker Compose** | >= v2.20 | 多容器编排 | `docker compose version` |
| **Postman** 或 **Apifox** | 最新版 | 接口调试 | 启动后能发送请求即可 |
| **VS Code** | 最新版 | 前端开发 + 通用编辑器 | — |
| **Navicat** 或 **DBeaver** | 任意 | SQL 统计查询调试 + 数据验证 | 能连接 `localhost:3306` 即可 |

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
| **Maven** | >= 3.8 | 项目构建 | `mvn --version` |
| **Node.js** | >= 18 LTS | 前端构建（中期） | `node --version` |
| **npm** | >= 9 | 前端包管理（中期） | `npm --version` |

---

## 三、后端技术栈

### 3.1 前期（岗位查询 API）

| 层面 | 技术 | 用途 |
|------|------|------|
| 应用框架 | Spring Boot 2.7+ | 岗位查询服务 |
| 持久层 | Spring Data JPA + Hibernate | job_position / job_company 表 ORM 查询 |
| 数据库 | MySQL 8.0 | 岗位数据存储 |
| 缓存 | Redis 7（Spring Data Redis） | 热门岗位缓存 |

### 3.2 中期（数据分析）

| 层面 | 技术 | 用途 |
|------|------|------|
| 定时任务 | Spring `@Scheduled` | 每日凌晨离线分析计算 |
| 统计表 | MySQL `stat_*` 系列表 | 预计算统计结果持久化 |
| 缓存策略 | Redis 7 | 热点统计缓存（30min TTL） |

---

## 四、前端技术栈（中期用到，前期仅安装环境）

| 层面 | 技术 | 用途 |
|------|------|------|
| 框架 | Vue 3（Composition API） | 前端框架 |
| 构建工具 | Vite | 开发服务器 + 打包 |
| UI 组件库 | Element Plus | 表单、表格、筛选面板 |
| 可视化 | ECharts 5 | 折线图 / 柱状图 / 饼图 / 雷达图 / 词云 |
| 中国地图 | ECharts + 中国 GeoJSON | 城市分布热力图 |
| 状态管理 | Pinia | 筛选条件 / 图表数据 |
| HTTP 客户端 | Axios | API 请求 |

---

## 五、API 接口清单（C 负责实现）

### 前期（必须）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/positions?keyword=&page=1&size=20` | 岗位分页搜索（模糊匹配 title + companyName） | 是 |
| GET | `/api/positions/{id}` | 岗位详情（含企业信息 + skills 数组） | 是 |
| GET | `/api/positions/hot?limit=20` | 热门岗位（按 create_time 倒序） | 是 |

### 中期（统计分析接口，前期仅预留）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats/overview` | 就业概览仪表盘数据 |
| GET | `/api/stats/salary` | 薪资分析（均值/区间/排行/城市对比） |
| GET | `/api/stats/skills` | 技能分析（词频 TOP 30 / 趋势 / 关联） |
| GET | `/api/stats/education` | 学历分析（分布 / 交叉对比） |
| GET | `/api/stats/city` | 城市分析（排名 / 薪资对比 / 热力图） |
| GET | `/api/stats/industry` | 行业分析（分布 / 趋势） |
| GET | `/api/stats/trends` | 趋势分析（日增量 / 月变化 / 同比环比） |

---

## 六、分析维度总览（中期离线计算）

| 维度 | 统计内容 | 输出 |
|------|---------|------|
| 岗位分析 | 总量、分类、热门 TOP 20、月度增长率 | `stat_position` |
| 薪资分析 | 均值、中位数、区间分布、高薪 TOP 20、城市对比 | `stat_salary` |
| 学历分析 | 比例、学历×岗位数、学历×薪资 | `stat_education` |
| 技能分析 | 热门词频 TOP 30、技能组合关联、月度趋势 | `stat_skill` |
| 地域分析 | 城市排名 TOP 10、城市×薪资、热力图数据 | `stat_city` |
| 企业分析 | 规模分布、行业分类、招聘活跃度排名 | `stat_company` |
| 趋势分析 | 日增量变化、月度/季度趋势、同比环比 | `stat_trend` |

---

## 七、开发工具

| 工具 | 用途 |
|------|------|
| **IntelliJ IDEA** | Spring Boot 后端开发 |
| **VS Code** + Volar 插件 | Vue 3 前端开发（中期） |
| **Postman** | 岗位查询接口自测 |
| **Navicat / DBeaver** | SQL 统计查询调试 + 数据验证 |

---

## 八、环境验证清单

- [ ] `java --version` 输出 11 或 17
- [ ] `mvn --version` 正常
- [ ] `node --version` ≥ 18（前期可暂不验证前端）
- [ ] `cd backend && mvn clean compile` 能通过
- [ ] Postman `GET /api/positions?keyword=Java&page=1&size=10`（带 Token）返回分页数据
- [ ] Postman `GET /api/positions/1`（带 Token）返回完整岗位 JSON（含 skills 和企业信息）
- [ ] Postman `GET /api/positions/hot?limit=20`（带 Token）返回最新岗位列表
- [ ] Navicat 能执行 `SELECT COUNT(*) FROM job_position` 并看到 A 导入的数据
- [ ] 中期验证：`SELECT COUNT(*) FROM stat_position` 有统计结果数据
