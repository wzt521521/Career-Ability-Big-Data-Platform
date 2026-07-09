# 同学D — 王致远 — 报告生成与岗位推荐 环境要求

> **职责范围：** 学生画像 CRUD API、就业分析报告自动生成（PDF / Word / Excel）、报告模板管理、岗位推荐引擎（基于内容的加权匹配算法）、Vue 3 报告中心与推荐页面。

---

## 一、全局通用环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **Git** | >= 2.30 | 版本控制 | `git --version` |
| **Docker** | >= 24.0 | 容器运行时 | `docker --version` |
| **Docker Compose** | >= v2.20 | 多容器编排 | `docker compose version` |
| **Postman** 或 **Apifox** | 最新版 | 接口调试 | 启动后能发送请求即可 |
| **VS Code** | 最新版 | 前端开发 + 通用编辑器 | — |
| **Navicat** 或 **DBeaver** | 任意 | 画像表数据验证 | 能连接 `localhost:3306` 即可 |

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

### 3.1 前期（画像 CRUD）

| 层面 | 技术 | 用途 |
|------|------|------|
| 应用框架 | Spring Boot 2.7+ | 画像管理服务 |
| 持久层 | Spring Data JPA + Hibernate | student_profile 表 ORM |
| 数据库 | MySQL 8.0 | 画像数据持久化 |
| 安全 | 从 Token 解析 user_id | 画像与用户一对一绑定 |

### 3.2 中期（报告生成 + 推荐引擎）

| 层面 | 技术 | 用途 |
|------|------|------|
| 模板引擎 | Freemarker | 报告模板数据填充 |
| PDF 导出 | iText 或 Apache PDFBox | PDF 文件生成 |
| Excel 导出 | EasyExcel | Excel 格式报告 |
| Word 导出 | Apache POI | Word 格式报告 |
| 异步处理 | Spring `@Async` + 线程池 | 大型报告异步生成，避免阻塞请求 |
| 缓存 | Redis 7（Spring Data Redis） | 推荐结果缓存（1h TTL） |

---

## 四、推荐算法规格（中期实现）

| 维度 | 权重 | 匹配方法 | 说明 |
|------|------|---------|------|
| 技能匹配 | 40% | Jaccard 相似系数 | `|学生技能 ∩ 岗位要求| / |学生技能 ∪ 岗位要求|` |
| 城市匹配 | 20% | 精确 + 周边降权 | 同城满分，同省 0.7，其他 0.3 |
| 学历匹配 | 15% | 精确 + 降级匹配 | 匹配 1.0，学生学历高于要求 0.8，低于要求 0.4 |
| 薪资匹配 | 15% | 区间重叠度 | 期望区间与岗位薪资区间的重叠比例 |
| 专业匹配 | 10% | 名称模糊匹配 | 专业名称关键词相似度 |

> 综合得分 = Σ(维度得分 × 权重)，按降序排列返回 TOP 20。

---

## 五、前端技术栈（中期用到，前期仅安装环境）

| 层面 | 技术 | 用途 |
|------|------|------|
| 框架 | Vue 3（Composition API） | 前端框架 |
| 构建工具 | Vite | 开发服务器 + 打包 |
| UI 组件库 | Element Plus | 表单、卡片、弹窗、步骤条 |
| 可视化 | ECharts 5 | 技能差距分析雷达图、匹配度仪表盘 |
| 状态管理 | Pinia | 画像状态 / 推荐列表 / 报告列表 |
| HTTP 客户端 | Axios | API 请求 |

---

## 六、API 接口清单（D 负责实现）

### 前期（必须）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| PUT | `/api/profile` | 创建或更新当前用户画像（user_id 从 Token 获取） | 是 |
| GET | `/api/profile` | 获取当前用户画像，未创建返回 null | 是 |

### 中期（报告 + 推荐）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reports?page=&size=` | 报告列表 |
| POST | `/api/reports/generate` | 生成报告（模板类型 + 时间范围） |
| GET | `/api/reports/{id}` | 报告详情 |
| GET | `/api/reports/{id}/download?format=pdf` | 下载报告文件 |
| GET | `/api/recommend?limit=20` | 获取个性化岗位推荐（基于当前用户画像） |
| POST | `/api/recommend/refresh` | 强制刷新推荐缓存 |

---

## 七、报告内容结构（中期）

```
封面：标题 + 时间范围 + 生成日期
第一章：行业概况（岗位总量、活跃企业数、数据来源）
第二章：岗位统计（分类/热门/增长率）
第三章：薪资分析（均值/分布/高薪排行/城市对比）
第四章：技能需求分析（词频 TOP 30 / 趋势 / 关联）
第五章：学历与经验分析（分布/交叉对比）
第六章：地域分析（城市排名/热力图）
第七章：趋势预测（同比/环比/展望）
第八章：教学建议（基于数据的课程改革建议）
```

支持导出格式：**PDF**（iText/PDFBox）、**Word**（Apache POI）、**Excel**（EasyExcel）

---

## 八、开发工具

| 工具 | 用途 |
|------|------|
| **IntelliJ IDEA** | Spring Boot 后端开发 |
| **VS Code** + Volar 插件 | Vue 3 前端开发（中期） |
| **Postman** | 画像接口自测，中期验证推荐/报告接口 |
| **Navicat / DBeaver** | 画像表数据验证，推荐结果数据抽查 |

---

## 九、环境验证清单

- [ ] `java --version` 输出 11 或 17
- [ ] `mvn --version` 正常
- [ ] `node --version` ≥ 18（前期可暂不验证前端）
- [ ] `cd backend && mvn clean compile` 能通过
- [ ] Postman `PUT /api/profile`（带 Token）能正常创建画像
- [ ] Postman `GET /api/profile`（带 Token）能返回刚创建的画像数据
- [ ] 再次 `PUT /api/profile` 能正常更新画像字段
- [ ] Navicat 查询 `SELECT * FROM student_profile` 能看到画像记录
- [ ] 中期验证：Postman `GET /api/recommend?limit=10` 返回匹配的岗位推荐列表
- [ ] 中期验证：Postman `POST /api/reports/generate` 能触发异步报告生成
