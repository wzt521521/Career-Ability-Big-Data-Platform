<#ftl output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>${reportTitle!""}</title>
<style>
  @page { size: A4; margin: 2cm; }
  body { font-family: "Noto Sans SC", sans-serif; font-size: 12pt; color: #333; line-height: 1.8; }
  .cover { text-align: center; padding: 120px 0 60px; }
  .cover h1 { font-size: 28pt; color: #1a1a2e; margin-bottom: 24px; }
  .cover .subtitle { font-size: 14pt; color: #666; margin-bottom: 48px; }
  .cover .meta { font-size: 11pt; color: #999; }
  .section { margin-top: 36px; page-break-inside: avoid; }
  .section h2 { font-size: 16pt; color: #1a1a2e; border-bottom: 2px solid #409EFF; padding-bottom: 6px; margin-bottom: 16px; }
  .section h3 { font-size: 13pt; color: #555; margin: 16px 0 8px; }
  table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 10pt; }
  th { background: #409EFF; color: white; padding: 8px 10px; text-align: left; }
  td { padding: 6px 10px; border-bottom: 1px solid #e8e8e8; }
  tr:nth-child(even) { background: #f9f9f9; }
  .kpi-row { display: flex; justify-content: space-between; margin: 16px 0; }
  .kpi-box { flex: 1; text-align: center; padding: 16px; margin: 0 8px; background: #f5f7fa; border-radius: 6px; }
  .kpi-box .value { font-size: 22pt; font-weight: bold; color: #409EFF; }
  .kpi-box .label { font-size: 10pt; color: #888; margin-top: 4px; }
  .footer { margin-top: 48px; padding-top: 16px; border-top: 1px solid #ddd; font-size: 9pt; color: #aaa; text-align: center; }
</style>
</head>
<body>

<!-- 封面 -->
<div class="cover">
  <h1>${reportTitle!""}</h1>
  <div class="subtitle">月度就业市场分析报告</div>
  <div class="meta">
    <p>数据范围：${timeRangeStart!""} ~ ${timeRangeEnd!""}</p>
    <p>生成时间：${generateTime!""}</p>
    <p>职业能力大数据服务平台</p>
  </div>
</div>

<#if emptyData!false>
<div class="section"><p>当前筛选范围暂无岗位数据，以下统计表为空。</p></div>
</#if>

<!-- 概述 -->
<#assign overview = overview!{} >
<div class="section">
  <h2>一、市场概览</h2>
  <div class="kpi-row">
    <div class="kpi-box"><div class="value">${overview.totalPositions!0}</div><div class="label">岗位总量</div></div>
    <div class="kpi-box"><div class="value">${overview.newThisMonth!0}</div><div class="label">本月新增</div></div>
    <div class="kpi-box"><div class="value">${overview.averageSalary!0}</div><div class="label">平均月薪(K)</div></div>
    <div class="kpi-box"><div class="value">${overview.activeCompanies!0}</div><div class="label">活跃企业</div></div>
  </div>
</div>

<!-- 岗位分类 -->
<#assign positions = positions!{} >
<div class="section">
  <h2>二、热门岗位排行</h2>
  <table>
    <tr><th>排名</th><th>岗位名称</th><th>招聘数量</th></tr>
    <#list positions.hotPositions![] as item>
    <#if item_index < 20>
    <tr><td>${item_index + 1}</td><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<!-- 薪资分析 -->
<#assign salary = salary!{} >
<div class="section">
  <h2>三、薪资分析</h2>
  <p>整体平均薪资：<strong>${salary.average!0} K/月</strong>，中位数：<strong>${salary.median!0} K/月</strong></p>
  <h3>薪资分布</h3>
  <table>
    <tr><th>薪资区间</th><th>岗位数</th></tr>
    <#list salary.distribution![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#list>
  </table>
  <h3>高薪岗位 TOP 10</h3>
  <table>
    <tr><th>岗位名称</th><th>平均薪资(K)</th></tr>
    <#list salary.topPositions![] as item>
    <#if item_index < 10>
    <tr><td>${item.name!""}</td><td>${item.averageSalary!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<!-- 技能需求 -->
<#assign skills = skills!{} >
<div class="section">
  <h2>四、热门技能需求 TOP 20</h2>
  <table>
    <tr><th>排名</th><th>技能名称</th><th>需求岗位数</th></tr>
    <#list skills.topSkills![] as item>
    <#if item_index < 20>
    <tr><td>${item_index + 1}</td><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<!-- 城市分布 -->
<#assign city = city!{} >
<div class="section">
  <h2>五、城市分布 TOP 15</h2>
  <table>
    <tr><th>排名</th><th>城市</th><th>岗位数</th><th>平均薪资(K)</th></tr>
    <#list city.ranking![] as item>
    <#if item_index < 15>
    <tr><td>${item_index + 1}</td><td>${item.name!""}</td><td>${item.value!0}</td><td>${item.averageSalary!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<!-- 学历分布 -->
<#assign education = education!{} >
<div class="section">
  <h2>六、学历要求分布</h2>
  <table>
    <tr><th>学历</th><th>岗位数</th><th>平均薪资(K)</th></tr>
    <#list education.distribution![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td><td>${item.averageSalary!0}</td></tr>
    </#list>
  </table>
</div>

<div class="footer">
  <p>本报告由职业能力大数据服务平台自动生成 | ${generateTime!""}</p>
</div>

</body>
</html>
