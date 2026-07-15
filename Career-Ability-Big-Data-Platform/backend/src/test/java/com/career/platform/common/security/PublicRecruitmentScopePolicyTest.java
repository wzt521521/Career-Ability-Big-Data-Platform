package com.career.platform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicRecruitmentScopePolicyTest {

    private final PublicRecruitmentScopePolicy policy = new PublicRecruitmentScopePolicy();

    @Test
    void documentsTheSharedPublicRecruitmentDatasetWithoutAcceptingCallerScope() {
        PublicRecruitmentScope scope = policy.resolve();

        assertThat(scope.value()).isEqualTo("PUBLIC_RECRUITMENT");
        assertThat(scope.collegeFilteringApplicable()).isFalse();
        assertThat(scope.selfFilteringApplicable()).isFalse();
        assertThat(scope.reason()).contains("没有学院或用户归属字段");
    }
}
