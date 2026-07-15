# 数据与第三方声明

## 样本数据

- `data/kaggle_jobs_500.csv` 是用于实训演示和 CI 验收的岗位样本文件。
- 发行文档不声明该文件为实时 Kaggle 导入，也不用于生产数据授权声明。
- 导入脚本按不可信输入处理 CSV/Excel：限制路径、格式、字段映射和清洗边界。

## 字体

- PDF 中文渲染使用 Noto Sans SC。
- 字体文件在后端镜像构建时从 Google Fonts 固定提交下载，并校验 SHA-256。
- Noto Sans SC 使用 SIL Open Font License，允许随发行物再分发。

## PDF 组件

- `com.itextpdf:html2pdf` 已从发行范围移除，避免 AGPL/商业许可风险。
- 当前 PDF 渲染使用 `openhtmltopdf-pdfbox`。

## SBOM 与许可证

正式 GitHub Release 必须附带：

- 后端 CycloneDX SBOM。
- 前端 CycloneDX SBOM 与生产依赖许可证清单。
- Python 数据管道 CycloneDX SBOM 与许可证清单。
- 镜像扫描结果或风险登记。

高危或严重漏洞如果无法立即修复，必须在 Release notes 中记录影响、不可达原因、补偿控制和复查期限。
