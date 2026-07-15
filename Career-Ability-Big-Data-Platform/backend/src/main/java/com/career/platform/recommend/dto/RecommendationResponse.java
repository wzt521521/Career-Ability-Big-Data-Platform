package com.career.platform.recommend.dto;

import com.career.platform.position.entity.JobPosition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "岗位推荐结果")
public class RecommendationResponse {

    @Schema(description = "岗位ID")
    private final Long positionId;

    @Schema(description = "岗位名称")
    private final String title;

    @Schema(description = "企业名称")
    private final String companyName;

    @Schema(description = "企业规模")
    private final String companySize;

    @Schema(description = "所属行业")
    private final String industry;

    @Schema(description = "最低月薪（K）")
    private final Integer salaryMin;

    @Schema(description = "最高月薪（K）")
    private final Integer salaryMax;

    @Schema(description = "城市")
    private final String city;

    @Schema(description = "学历要求")
    private final String education;

    @Schema(description = "经验要求")
    private final String experience;

    @Schema(description = "岗位技能要求")
    private final List<String> skills;

    @Schema(description = "综合匹配得分（0~1）")
    private final double score;

    @Schema(description = "匹配百分比（0~100）")
    private final int matchPercent;

    @Schema(description = "匹配的技能")
    private final List<String> matchedSkills;

    @Schema(description = "未匹配的技能")
    private final List<String> unmatchedSkills;

    @Schema(description = "各维度得分明细")
    private final Map<String, Double> scoreBreakdown;

    public RecommendationResponse(JobPosition position, double score,
                                  List<String> matchedSkills, List<String> unmatchedSkills,
                                  Map<String, Double> scoreBreakdown) {
        this(position.getId(), position.getTitle(), position.getCompany() == null ? null : position.getCompany().getCompanyName(),
                position.getCompany() == null ? null : position.getCompany().getCompanySize(),
                position.getCompany() == null ? null : position.getCompany().getIndustry(),
                position.getSalaryMin(), position.getSalaryMax(), position.getCity(), position.getEducation(),
                position.getExperience(), position.getSkills(), score, matchedSkills, unmatchedSkills, scoreBreakdown);
    }

    @JsonCreator
    public RecommendationResponse(@JsonProperty("positionId") Long positionId,
                                  @JsonProperty("title") String title,
                                  @JsonProperty("companyName") String companyName,
                                  @JsonProperty("companySize") String companySize,
                                  @JsonProperty("industry") String industry,
                                  @JsonProperty("salaryMin") Integer salaryMin,
                                  @JsonProperty("salaryMax") Integer salaryMax,
                                  @JsonProperty("city") String city,
                                  @JsonProperty("education") String education,
                                  @JsonProperty("experience") String experience,
                                  @JsonProperty("skills") List<String> skills,
                                  @JsonProperty("score") double score,
                                  @JsonProperty("matchedSkills") List<String> matchedSkills,
                                  @JsonProperty("unmatchedSkills") List<String> unmatchedSkills,
                                  @JsonProperty("scoreBreakdown") Map<String, Double> scoreBreakdown) {
        this.positionId = positionId;
        this.title = title;
        this.companyName = companyName;
        this.companySize = companySize;
        this.industry = industry;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.city = city;
        this.education = education;
        this.experience = experience;
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.score = Math.round(score * 10000.0) / 10000.0;
        this.matchPercent = (int) Math.round(score * 100);
        this.matchedSkills = matchedSkills == null ? List.of() : List.copyOf(matchedSkills);
        this.unmatchedSkills = unmatchedSkills == null ? List.of() : List.copyOf(unmatchedSkills);
        this.scoreBreakdown = scoreBreakdown == null ? Map.of() : Map.copyOf(scoreBreakdown);
    }

    public Long getPositionId() { return positionId; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public String getCompanySize() { return companySize; }
    public String getIndustry() { return industry; }
    public Integer getSalaryMin() { return salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public String getCity() { return city; }
    public String getEducation() { return education; }
    public String getExperience() { return experience; }
    public List<String> getSkills() { return skills; }
    public double getScore() { return score; }
    public int getMatchPercent() { return matchPercent; }
    public List<String> getMatchedSkills() { return matchedSkills; }
    public List<String> getUnmatchedSkills() { return unmatchedSkills; }
    public Map<String, Double> getScoreBreakdown() { return scoreBreakdown; }
}
