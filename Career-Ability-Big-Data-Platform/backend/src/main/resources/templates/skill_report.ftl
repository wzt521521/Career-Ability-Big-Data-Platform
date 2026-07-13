<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${reportTitle!""}</title>
<style>
  @page { size: A4; margin: 2cm; }
  body { font-family: SimSun, serif; font-size: 12pt; color: #333; line-height: 1.8; }
  .cover { text-align: center; padding: 120px 0 60px; }
  .cover h1 { font-size: 28pt; color: #1a1a2e; margin-bottom: 24px; }
  .cover .subtitle { font-size: 14pt; color: #666; margin-bottom: 48px; }
  .cover .meta { font-size: 11pt; color: #999; }
  .section { margin-top: 36px; page-break-inside: avoid; }
  .section h2 { font-size: 16pt; color: #1a1a2e; border-bottom: 2px solid #67C23A; padding-bottom: 6px; margin-bottom: 16px; }
  .section h3 { font-size: 13pt; color: #555; margin: 16px 0 8px; }
  table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 10pt; }
  th { background: #67C23A; color: white; padding: 8px 10px; text-align: left; }
  td { padding: 6px 10px; border-bottom: 1px solid #e8e8e8; }
  tr:nth-child(even) { background: #f4faf0; }
  .summary-box { background: #f0f9eb; padding: 12px 16px; border-left: 4px solid #67C23A; margin: 12px 0; }
  .skill-bar { display: flex; align-items: center; margin: 4px 0; }
  .skill-bar .name { width: 120px; font-size: 10pt; }
  .skill-bar .bar { flex: 1; height: 14px; background: #e0e0e0; border-radius: 7px; overflow: hidden; }
  .skill-bar .fill { height: 100%; background: #67C23A; border-radius: 7px; }
  .skill-bar .count { width: 60px; font-size: 10pt; text-align: right; color: #888; }
  .footer { margin-top: 48px; padding-top: 16px; border-top: 1px solid #ddd; font-size: 9pt; color: #aaa; text-align: center; }
</style>
</head>
<body>

<div class="cover">
  <h1>${reportTitle!""}</h1>
  <div class="subtitle">技能需求深度分析报告</div>
  <div class="meta">
    <p>数据范围：${timeRangeStart!""} ~ ${timeRangeEnd!""}</p>
    <p>生成时间：${generateTime!""}</p>
    <p>职业能力大数据服务平台</p>
  </div>
</div>

<#assign skills = skills!{} >
<#assign education = education!{} >
<#assign salary = salary!{} >

<div class="section">
  <h2>一、技能需求概况</h2>
  <div class="summary-box">
    <p>本报告分析了 <strong>${skills.totalTaggedPositions!0}</strong> 个标注了技能要求的岗位，
    覆盖全部行业和城市。以下为详细分析结果。</p>
  </div>
</div>

<div class="section">
  <h2>二、热门技能需求 TOP 30</h2>
  <table>
    <tr><th>排名</th><th>技能名称</th><th>需求岗位数</th><th>需求占比</th></tr>
    <#assign totalTagged = skills.totalTaggedPositions!1 >
    <#list skills.topSkills![] as item>
    <#if item_index < 30>
    <tr>
      <td>${item_index + 1}</td>
      <td><strong>${item.name!""}</strong></td>
      <td>${item.value!0}</td>
      <td>${(item.value!0 * 100 / totalTagged)?string("0.0")}%</td>
    </tr>
    </#if>
    </#list>
  </table>
</div>

<div class="section">
  <h2>三、技能组合关联 TOP 15</h2>
  <p style="font-size:10pt;color:#888;">经常同时出现在同一岗位的技能组合</p>
  <table>
    <tr><th>技能组合</th><th>共同出现次数</th></tr>
    <#list skills.associations![] as item>
    <#if item_index < 15>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td></tr>
    </#if>
    </#list>
  </table>
</div>

<div class="section">
  <h2>四、学历与技能关系</h2>
  <p style="font-size:10pt;color:#888;">不同学历要求的岗位数量及对应平均薪资</p>
  <table>
    <tr><th>学历要求</th><th>岗位数</th><th>平均薪资(K)</th></tr>
    <#list education.distribution![] as item>
    <tr><td>${item.name!""}</td><td>${item.value!0}</td><td>${item.averageSalary!0}</td></tr>
    </#list>
  </table>
</div>

<div class="section">
  <h2>五、技能学习建议</h2>
  <#assign topSkills = skills.topSkills![] >
  <div class="summary-box">
    <p><strong>建议优先学习以下高需求技能：</strong></p>
    <p>
    <#list topSkills as item>
    <#if item_index < 10>
    <#if item_index gt 0>、</#if>${item.name!""}
    </#if>
    </#list>
    </p>
    <p style="margin-top:8px;font-size:10pt;color:#888;">
      以上技能涵盖了当前就业市场中最受企业青睐的技术方向，
      掌握这些技能将显著提升求职竞争力。
    </p>
  </div>
</div>

<div class="footer">
  <p>本报告由职业能力大数据服务平台自动生成 | ${generateTime!""}</p>
</div>

</body>
</html>
