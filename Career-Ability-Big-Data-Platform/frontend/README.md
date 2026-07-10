# 数据分析前端

张博源负责的数据大屏和岗位分析页面，使用 Vue 3、Element Plus 与 ECharts。

```bash
npm ci
npm run dev
npm run build
```

开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。
登录模块合并后，将其签发的 `accessToken` 写入 `localStorage`；请求拦截器会自动添加 Bearer Token。
