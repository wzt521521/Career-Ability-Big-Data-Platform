package com.career.platform.common.security;

import org.springframework.stereotype.Component;

/**
 * Central policy for services which expose the shared public recruitment dataset.
 *
 * <p>The caller's {@link DataScope} remains meaningful for user-owned resources. For public
 * recruitment records it is deliberately not translated to a predicate because no college/user
 * key exists on the underlying tables. Returning this explicit policy prevents accidental
 * pseudo-isolation in controllers or Open API adapters.</p>
 */
@Component
public class PublicRecruitmentScopePolicy {

    private static final PublicRecruitmentScope SHARED_SCOPE = PublicRecruitmentScope.shared();

    public PublicRecruitmentScope resolve() {
        return SHARED_SCOPE;
    }

}
