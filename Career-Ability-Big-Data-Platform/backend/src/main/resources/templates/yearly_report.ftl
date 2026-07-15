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
  .section h2 { font-size: 16pt; color: #1a1a2e; border-bottom: 2px solid #E6A23C; padding-bottom: 6px; margin-bottom: 16px; }
  .section h3 { font-size: 13pt; color: #555; margin: 16px 0 8px; }
  table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 10pt; }
  th { background: #E6A23C; color: white; padding: 8px 10px; text-align: left; }
  td { padding: 6px 10px; border-bottom: 1px solid #e8e8e8; }
  tr:nth-child(even) { background: #fdf8f0; }
  .kpi-row { margin: 16px 0; }
  .kpi-item { display: inline-block; width: 30%; padding: 8px; vertical-align: top; }
  .kpi-item .value { font-size: 18pt; font-weight: bold; color: #E6A23C; }
  .kpi-item .label { font-size: 10pt; color: #888; }
  .highlight { background: #fef3e2; padding: 12px 16px; border-left: 4px solid #E6A23C; margin: 12px 0; }
  .footer { margin-top: 48px; padding-top: 16px; border-top: 1px solid #ddd; font-size: 9pt; color: #aaa; text-align: center; }
</style>
</head>
<body>

<div class="cover">
  <h1>${reportTitle!""}</h1>
  <div class="subtitle">年度就业市场趋势报告</div>
  <div class="meta">
    <p>数据范围：${timeRangeStart!""} ~ ${timeRangeEnd!""}</p>
    <p>生成时间：${generateTime!""}</p>
    <p>职业能力大数据服务平台</p>
  </div>
</div>

<#if emptyData!false>
<div class="section"><p>当前筛选范围暂无岗位数据，以下统计表为空。</p></div>
</#if>

<#assign overview = overview!{} >
<#assign trends = trends!{} >
<#assign salary = salary!{} >
<#assign skills = skills!{} >
<#assign city = city!{} >
<#assign company = company!{} >
<#assign positions = positions!{} >

<div class="section">
  <h2>一、年度概要</h2>
  <div class="highlight">
    <p>截至报告生成时，平台共收录 <strong>${overview.totalPositions!0}</strong> 个岗位，
    涉及 <strong>${overview.activeCompanies!0}</strong> 家企业。
    整体平均薪资为 <strong>${overview.averageSalary!0} K/月</strong>。</p>
  </div>
  <div class="kpi-row">
    <div class="kpi-item"><div class="value">${positions.monthlyGrowthRate!0}%</div><div class="label">月环比增长率</div></div>
    <div class="kpi-item"><div class="value">${trends.monthOverMonth!0}%</div><div class="label">月度趋势变化</div></div>
    <div class="kpi-item"><div class="value">${trends.yearOverYear!0}%</div><div class="label">年度同比变化</div></div>
  </div>
</div>

<div class="section">
  <h2>二、月度趋势（近12月）</h2>
  <table>
    <tr><th>月份</th><th>新增岗位数</th></tr>
    <#list trends.monthly![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#list>
  </table>
</div>

<div class="section">
  <h2>三、薪资趋势对比</h2>
  <h3>各城市薪资 TOP 15</h3>
  <table>
    <tr><th>城市</th><th>岗位数</th><th>平均薪资(K)</th></tr>
    <#list city.salaryComparison![] as item>
    <#if item_index < 15>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td><td>${item.averageSalary!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<div class="section">
  <h2>四、年度热门技能 TOP 30</h2>
  <table>
    <tr><th>排名</th><th>技能</th><th>需求频次</th></tr>
    <#list skills.topSkills![] as item>
    <#if item_index < 30>
    <tr><td>${item_index + 1}</td><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<div class="section">
  <h2>五、行业分布</h2>
  <table>
    <tr><th>行业</th><th>岗位数</th><th>企业数</th></tr>
    <#assign industryMap = {} >
    <#list company.industryDistribution![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td><td>--</td></tr>
    </#list>
  </table>
</div>

<div class="section">
  <h2>六、企业规模分布</h2>
  <table>
    <tr><th>规模</th><th>岗位数</th></tr>
    <#list company.sizeDistribution![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#list>
  </table>
</div>

<div class="footer">
  <p>本报告由职业能力大数据服务平台自动生成 | ${generateTime!""}</p>
</div>

</body>
</html>
