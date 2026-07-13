package com.career.platform.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "技能差距分析结果")
public class GapAnalysisResponse {

    @Schema(description = "岗位ID")
    private final Long positionId;

    @Schema(description = "岗位名称")
    private final String title;

    @Schema(description = "匹配的技能")
    private final List<String> matchedSkills;

    @Schema(description = "缺失的技能（建议学习）")
    private final List<String> missingSkills;

    @Schema(description = "学生已掌握但岗位不需要的技能")
    private final List<String> extraSkills;

    @Schema(description = "五维得分明细")
    private final Map<String, Double> scoreBreakdown;

    @Schema(description = "综合匹配得分")
    private final double totalScore;

    @Schema(description = "学习建议")
    private final String suggestion;

    public GapAnalysisResponse(Long positionId, String title,
                               List<String> matchedSkills, List<String> missingSkills,
                               List<String> extraSkills, Map<String, Double> scoreBreakdown,
                               double totalScore, String suggestion) {
        this.positionId = positionId;
        this.title = title;
        this.matchedSkills = List.copyOf(matchedSkills);
        this.missingSkills = List.copyOf(missingSkills);
        this.extraSkills = List.copyOf(extraSkills);
        this.scoreBreakdown = Map.copyOf(scoreBreakdown);
        this.totalScore = Math.round(totalScore * 10000.0) / 10000.0;
        this.suggestion = suggestion;
    }

    public Long getPositionId() { return positionId; }
    public String getTitle() { return title; }
    public List<String> getMatchedSkills() { return matchedSkills; }
    public List<String> getMissingSkills() { return missingSkills; }
    public List<String> getExtraSkills() { return extraSkills; }
    public Map<String, Double> getScoreBreakdown() { return scoreBreakdown; }
    public double getTotalScore() { return totalScore; }
    public String getSuggestion() { return suggestion; }
}
