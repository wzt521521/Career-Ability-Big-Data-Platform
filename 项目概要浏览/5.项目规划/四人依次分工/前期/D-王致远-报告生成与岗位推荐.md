# D（王致远）前期开工计划 — 报告生成与岗位推荐

> 你前期的活是最少的——只有一个画像 CRUD。报告生成和推荐引擎全是中期的。但这不代表你可以晚开工，因为画像接口是验收项之一。

---

## 你的开工时机

```
A 第1天结束 → 拿到 init.sql，可以写 JPA 实体
B 第1天结束 → 参考 common 模块结构写自己的模块
A 第3天结束 → Docker 就绪，你的接口可以在容器里跑
B 第3天结束 → 认证好了，你可以从 Token 里拿 user_id
B 第4天结束 → 前端骨架有了，后期接页面
```

**前期大概 3 天有效工作量。其余时间用来熟悉 Freemarker、iText、Jaccard 算法——为中期做准备。**

---

## 第1天（A 的第2天）：JPA 实体 + Repository

**上午：创建模块 + 写实体**

- 在 `backend/module-profile/` 下建子模块
- 对着 A 的建表脚本写实体类：
  - `StudentProfile.java`（对应 student_profile 表）
  - 注意 `skills` 是 JSON 类型
  - `user_id` 是唯一约束（一个用户一份画像）

**下午：Repository**

- `ProfileRepository.java`：
  - `findByUserId(Long userId)` → 查当前用户的画像
  - `save(StudentProfile profile)` → 创建或更新

**今天的交付物：**
- module-profile 可编译
- Repository 就绪

---

## 第2天：画像 API

**上午：ProfileService**

- `getProfile(Long userId)`：调 Repository 查画像
- `saveOrUpdate(Long userId, ProfileRequest request)`：
  - 先查是否存在 → 存在就更新 → 不存在就新建
  - user_id 从 Token 中获取，不允许客户端传

**下午：ProfileController**

- `PUT /api/profile`
  - 请求体：`{ major, skills, education, preferredCity, salaryMin, salaryMax }`
  - user_id 从 JWT Token 中解析
  - 存在则更新，不存在则创建
- `GET /api/profile`
  - 从 Token 中解析 user_id
  - 返回画像对象，未创建时返回 `data: null`

**今天的交付物：**
- 画像 CRUD 接口可用
- Postman 可调通：PUT 创建 → GET 查询返回一致数据
- **这是前期的验收项之一（第10项）**

---

## 第3天：接入认证 + 等待联调

**上午：接入 Security**

- 依赖 B 的 common 模块
- Controller 上通过 `@AuthenticationPrincipal` 或 SecurityContext 获取当前用户
- Postman 不带 Token → 401，带 Token → 从 Token 中提取 user_id

**下午：中期准备（学习）**

前期你只有画像 CRUD 这一点活。剩下的时间别浪费，提前做中期准备：

1. **Freemarker 模板语法**：学 `.ftl` 文件怎么写（变量插值、条件判断、循环列表）
2. **iText PDF 生成**：跑一个 Hello World，理解 Document → Paragraph → Table 的基本用法
3. **Jaccard 相似度算法**：
   - 公式：`J(A,B) = |A ∩ B| / |A ∪ B|`
   - 你的五维加权版本就是给每个维度算一个 Jaccard，再加权求和
   - 技能维度：岗位要求技能集合 vs 学生已有技能集合，算交集/并集
   - 城市维度：意向城市列表 vs 岗位所在城市，匹配得满分
   - 学历维度：学历层级映射（博士>硕士>本科>大专），档位差越小分越高
   - 薪资维度：期望区间和岗位区间的重叠比例
   - 专业维度：专业名称的文本相似度或关键词匹配
4. **EasyExcel / Apache POI**：简单了解，后期报告多格式导出用

**今天的交付物：**
- 画像接口已接入 Security
- 对中期技术栈有基本了解

---

## 你与其他人的协作节点

```
A第1天结束 → 你开始写实体和 Repository（跟 B、C 同时开工）
你第2天结束 → 画像接口可调（是比较早完成的）
B第3天结束 → 你接入 Security
你第3天     → 学习中期技术栈（Freemarker / iText / Jaccard）
第7天       → 全链路联调（画像接口是验收项第10项）
```

---

## 额外建议

你的前期最轻松，但中期的报告生成是整个项目对外展示的"门面"——答辩时一份自动生成的 PDF 报告比什么都有说服力。

**建议你现在就开始做的事情：**

1. 用 Word 画三份报告的"效果图"：
   - 《月度就业分析报告》——封面 + 目录 + 统计表格 + 图表占位
   - 《年度趋势报告》——折线图趋势 + 同比环比分析
   - 《技能需求报告》——词云 + 技能排行 + 行业分布
2. 拿着效果图去问 C：这些数据你能不能查出来？接口返回什么格式？
3. 这样到中期时，你只需要把 Word 效果图翻译成 Freemarker 模板就行，不用从零想布局。

**前期活少是好事，但不能闲着——你是全队"蓄力最久、中期爆发"的角色。**
