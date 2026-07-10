# 数据分析后端

本模块实现张博源负责的岗位查询、七维统计分析、Redis 热点缓存和每日离线计算。

## 本地运行

```bash
mvn test
mvn spring-boot:run
```

默认连接 `localhost:3307/career_ability` 和 `localhost:6379`。容器内连接信息由根目录
`docker-compose.yml` 注入；容器环境同时设置 `SPRING_CACHE_TYPE=redis`，统计缓存 TTL 为 30 分钟。

认证由同学 B 的 JWT 过滤器接入。当前模块已启用方法级鉴权，所有岗位、统计和大屏接口均要求
认证；在 B 的模块合并前，Spring Boot 会提供临时的 HTTP Basic 开发账号。

## 接口

- `/api/positions`：岗位多条件筛选、分页和排序
- `/api/positions/{id}`、`/hot`、`/search/suggest`：详情、最新岗位和搜索建议
- `/api/stats/*`：岗位、薪资、学历、技能、城市、企业和趋势统计
- `/api/analysis/*`：按岗位名称聚合的薪资、技能、城市和学历分析
- `/api/dashboard/*`：数据大屏聚合接口

统计接口接受可选的 `startDate`、`endDate`、`city`、`position` 和 `industry` 查询参数。
离线任务默认每天 `02:00` 执行，可通过 `ANALYTICS_CRON` 覆盖。
