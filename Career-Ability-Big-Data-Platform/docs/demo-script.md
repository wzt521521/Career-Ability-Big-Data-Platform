# 验收演示脚本

## 场景一：教师查看就业趋势

1. 使用教师账号登录。
2. 打开 `数据大屏`，确认岗位总量、薪资、技能、学历、城市分布加载成功。
3. 打开 `岗位分析`，执行岗位分页查询和筛选。
4. 说明数据来源：`data/kaggle_jobs_500.csv` 经 Redis raw queue、ETL 清洗后写入 MySQL。

## 场景二：学生获取岗位推荐

1. 使用学生账号登录。
2. 打开 `个人资料`，填写专业、技能、学历、期望城市和薪资。
3. 打开 `岗位推荐`。
4. 验证推荐结果不超过全局 TOP20，展示匹配百分比、匹配技能和缺失技能。
5. 选择岗位进入技能差距分析。

## 场景三：管理员生成并下载报告

1. 使用管理员或分析员账号登录。
2. 打开 `报告中心`，选择月度、年度或技能模板。
3. 设置日期范围和维度，提交异步生成。
4. 轮询状态至 `COMPLETED`。
5. 预览并下载 PDF，确认中文标题和表格文本可复制。
6. 重建后端容器后再次下载同一报告，确认历史报告仍可访问。

## 自动化复现

```bash
docker compose up -d --build --wait
docker compose exec -T python-etl python scripts/verify_compose_pipeline.py --csv /data/kaggle_jobs_500.csv
python scripts/verify_compose_release.py --base-url http://127.0.0.1:8080
python scripts/verify_compose_reports.py --compose-file docker-compose.yml
python scripts/verify_compose_browser.py --base-url http://127.0.0.1 --output-dir release-artifacts/browser-screenshots
```
