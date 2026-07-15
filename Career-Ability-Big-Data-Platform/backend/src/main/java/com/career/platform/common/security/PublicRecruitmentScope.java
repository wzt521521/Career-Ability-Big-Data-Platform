package com.career.platform.common.security;

/**
 * Describes the data boundary for the public recruitment dataset.
 *
 * <p>Job and aggregate-statistics tables contain public vacancy data only. They do not carry a
 * college or an owner column, so projecting {@link DataScopeType#COLLEGE} or
 * {@link DataScopeType#SELF} onto them would claim isolation that the data model cannot enforce.
 * Profile, recommendation, and report records still use their applicable user/college scopes.</p>
 */
public record PublicRecruitmentScope(
        String value,
        boolean collegeFilteringApplicable,
        boolean selfFilteringApplicable,
        String reason) {

    public static final String VALUE = "PUBLIC_RECRUITMENT";

    public static PublicRecruitmentScope shared() {
        return new PublicRecruitmentScope(
                VALUE,
                false,
                false,
                "岗位及统计数据来自公开招聘来源，当前模型没有学院或用户归属字段。");
    }
}
