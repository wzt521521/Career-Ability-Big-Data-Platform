# C（张博源）前期开工计划 — 数据分析与可视化

> 你的等待时间比 A 和 B 长，但一旦开工，推进速度快。前期只做岗位查询接口，大屏和图表是中期的事。

---

## 你的开工时机

```
A 第1天结束 → 拿到 init.sql，可以写 JPA 实体（但还跑不起来，A 的 Docker 没好）
B 第1天结束 → 参考 common 模块的结构写自己的模块
A 第3天结束 → ETL 有数据入库了，你的查询接口可以真正测通
B 第3天结束 → 认证好了，你可以给接口加 Token 保护
B 第4天结束 → 前端骨架有了，后面可以接页面
```

**实际情况：A 第1天结束你就可以开始写实体和 Repository，A 第3天结束后你的接口才真正有数据可查。前期大概 4 天有效工作量。**

---

## 第1天（A 的第2天）：JPA 实体 + Repository

**上午：创建模块 + 写实体**

- 在 `backend/module-position/` 下建子模块
- 对着 A 的建表脚本写实体类：
  - `JobPosition.java`（对应 job_position 表，注意 skills 和 welfare 是 JSON 类型，用 JPA `@Convert` 或 `@Column(columnDefinition = "JSON")`）
  - `JobCompany.java`（对应 job_company 表）
- Position 和 Company 是一对多关系：`@ManyToOne` + `@JoinColumn(name = "company_id")`

**下午：Repository + Service**

- `PositionRepository.java`：
  - `findByTitleContainingOrCompanyCompanyNameContaining(keyword, keyword, Pageable)` → 关键字模糊搜索
  - `findById(Long id)` → 详情查询
  - `findTopNByOrderByCreateTimeDesc(int n)` → 最新岗位（前期用这个替代热门）
- `CompanyRepository.java`：简单的 CRUD
- `PositionService.java`：封装查询逻辑，处理分页、关键字搜索

**今天的交付物：**
- module-position 可编译
- Repository 方法定义完毕
- 等 A 的 Docker 和 ETL 就绪后直接联调

---

## 第2天：岗位查询 API

**上午：PositionController**

- `GET /api/positions?keyword=&page=1&size=20`
  - 关键字模糊匹配岗位名和公司名
  - 分页返回，每条含岗位信息 + 关联的公司信息
- `GET /api/positions/{id}`
  - 返回单条岗位完整信息
- `GET /api/positions/hot?limit=20`
  - 按创建时间倒序取最新 N 条

**下午：自测 + 联调准备**

- 先用 Postman 调自己的接口，确认能编译通过、参数正确
- 如果 A 的 Docker 和 ETL 已经跑通：
  - 查 MySQL 确认有数据
  - Postman 调接口验证返回真实数据
- 如果 A 还没就绪：
  - 用 `src/test/resources/` 下的测试数据 + `@DataJpaTest` 写单元测试先验证 Repository

**今天的交付物：**
- 3 个岗位查询接口可用
- 等 A 的数据入库后立即可验证

---

## 第3天：接入认证 + 等待数据

**上午：接入 Security**

- 在 `module-position` 的 pom.xml 中依赖 `common` 模块
- Controller 上加 `@PreAuthorize` 或从 SecurityContext 获取当前用户
- Postman 不带 Token → 401，带 Token → 200

**下午：数据验证**

- 等 A 的小规模样本数据入库
- 调自己的接口验证返回的 JSON 字段完整、格式正确
- 检查：skills 数组有没有、company 信息对不对、薪资字段解析是否正确
- 发现问题反馈给 A 调整 ETL 脚本

**今天的交付物：**
- 接口已接入 Security 认证
- 用真实数据验证过接口返回正确

---

## 第4天：B 的前端骨架出来后

**学习 + 准备工作，实际的页面开发在中期：**

前期你的前端工作为 0——大屏和岗位分析页都是中期的活。但这不代表闲着：

1. 熟悉 B 搭好的 Vue 3 骨架结构（router、store、request.js 怎么用）
2. 学习 ECharts 5 的基本用法（重点看折线图、柱状图、饼图、中国地图）
3. 在脑子里画好数据大屏的布局草稿
4. 想好岗位分析页的筛选器交互逻辑

**中期你才会动手写页面，前期把后端接口打磨好就行了。**

---

## 你与其他人的协作节点

```
A第1天结束 → 你开始写实体和 Repository
A第3天结束 → 你的接口有真实数据可测
B第3天结束 → 你接入 Security 认证
B第4天结束 → 你熟悉前端骨架（页面中期再做）
第7天     → 全链路联调（你的岗位接口是验收项 8、9）
```

**你的前期核心任务只有一句话：把岗位查询接口写好、写稳。中期你的戏份才真正开始。**
