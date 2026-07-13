package com.career.platform.recommend.service;

import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import com.career.platform.profile.entity.StudentProfile;
import com.career.platform.profile.repository.ProfileRepository;
import com.career.platform.recommend.dto.GapAnalysisResponse;
import com.career.platform.recommend.dto.RecommendationResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecommendService {

    private static final Map<String, Integer> EDUCATION_LEVEL = Map.of(
            "博士", 5, "硕士", 4, "本科", 3, "大专", 2, "不限", 1
    );

    // 权重按项目规划：技能 40% + 城市 20% + 学历 15% + 薪资 15% + 专业 10%
    private static final double SKILL_WEIGHT = 0.40;
    private static final double CITY_WEIGHT = 0.20;
    private static final double EDUCATION_WEIGHT = 0.15;
    private static final double SALARY_WEIGHT = 0.15;
    private static final double MAJOR_WEIGHT = 0.10;

    private final ProfileRepository profileRepository;
    private final PositionRepository positionRepository;
    private final CacheManager cacheManager;

    public RecommendService(ProfileRepository profileRepository,
                           PositionRepository positionRepository,
                           CacheManager cacheManager) {
        this.profileRepository = profileRepository;
        this.positionRepository = positionRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * 为用户推荐匹配的岗位，按综合得分降序排列。
     * <p>
     * 推荐结果按 userId 缓存（通过 {@code CacheManager} 手动管理，TTL 由 Redis 配置控制，
     * 默认 1h）。画像更新时 {@code ProfileServiceImpl} 主动清除该用户的缓存。
     * </p>
     */
    public List<RecommendationResponse> recommend(Long userId, int page, int size) {
        // 尝试从缓存获取完整推荐列表
        List<RecommendationResponse> all = getCachedResult(userId);
        if (all == null) {
            all = computeAll(userId);
            putCachedResult(userId, all);
        }

        // 内存分页
        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, to);
    }

    private List<RecommendationResponse> getCachedResult(Long userId) {
        Cache cache = cacheManager.getCache("recommend");
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(userId);
        if (wrapper == null) return null;
        @SuppressWarnings("unchecked")
        List<RecommendationResponse> result = (List<RecommendationResponse>) wrapper.get();
        return result;
    }

    private void putCachedResult(Long userId, List<RecommendationResponse> result) {
        Cache cache = cacheManager.getCache("recommend");
        if (cache != null) {
            cache.put(userId, result);
        }
    }

    /**
     * 计算全部岗位的加权得分并降序排列（不含缓存逻辑）。
     */
    private List<RecommendationResponse> computeAll(Long userId) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先创建就业画像"));

        List<JobPosition> positions = positionRepository.findAllWithCompany();
        if (positions.isEmpty()) {
            return List.of();
        }

        List<String> studentSkills = normalizeSkills(profile.getSkills());
        List<String> preferredCities = parseCities(profile.getPreferredCity());

        List<RecommendationResponse> results = new ArrayList<>();
        for (JobPosition position : positions) {
            List<String> positionSkills = normalizeSkills(position.getSkills());

            double skillScore = calcSkillScore(studentSkills, positionSkills);
            double cityScore = calcCityScore(preferredCities, position.getCity());
            double educationScore = calcEducationScore(profile.getEducation(), position.getEducation());
            double salaryScore = calcSalaryScore(profile.getSalaryMin(), profile.getSalaryMax(),
                    position.getSalaryMin(), position.getSalaryMax());
            double majorScore = calcMajorScore(profile.getMajor(), position.getTitle());

            double total = skillScore * SKILL_WEIGHT
                    + cityScore * CITY_WEIGHT
                    + educationScore * EDUCATION_WEIGHT
                    + salaryScore * SALARY_WEIGHT
                    + majorScore * MAJOR_WEIGHT;

            Set<String> matched = new HashSet<>(studentSkills);
            matched.retain(new HashSet<>(positionSkills));
            Set<String> unmatched = new HashSet<>(positionSkills);
            unmatched.removeAll(new HashSet<>(studentSkills));

            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("skill", round(skillScore));
            breakdown.put("city", round(cityScore));
            breakdown.put("education", round(educationScore));
            breakdown.put("salary", round(salaryScore));
            breakdown.put("major", round(majorScore));

            results.add(new RecommendationResponse(position, total,
                    new ArrayList<>(matched), new ArrayList<>(unmatched), breakdown));
        }

        results.sort(Comparator.comparingDouble(RecommendationResponse::getScore).reversed());
        return results;
    }

    /**
     * 获取推荐结果总数。
     */
    public long count(Long userId) {
        profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先创建就业画像"));
        return positionRepository.count();
    }

    /**
     * 技能差距分析：针对指定岗位，分析学生的技能匹配情况。
     */
    public GapAnalysisResponse gapAnalysis(Long userId, Long positionId) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先创建就业画像"));

        JobPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "岗位不存在"));

        List<String> studentSkills = normalizeSkills(profile.getSkills());
        List<String> positionSkills = normalizeSkills(position.getSkills());
        List<String> preferredCities = parseCities(profile.getPreferredCity());

        Set<String> matched = new HashSet<>(studentSkills);
        matched.retain(new HashSet<>(positionSkills));
        Set<String> missing = new HashSet<>(positionSkills);
        missing.removeAll(new HashSet<>(studentSkills));
        Set<String> extra = new HashSet<>(studentSkills);
        extra.removeAll(new HashSet<>(positionSkills));

        double skillScore = calcSkillScore(studentSkills, positionSkills);
        double cityScore = calcCityScore(preferredCities, position.getCity());
        double educationScore = calcEducationScore(profile.getEducation(), position.getEducation());
        double salaryScore = calcSalaryScore(profile.getSalaryMin(), profile.getSalaryMax(),
                position.getSalaryMin(), position.getSalaryMax());
        double majorScore = calcMajorScore(profile.getMajor(), position.getTitle());

        double total = skillScore * SKILL_WEIGHT + cityScore * CITY_WEIGHT
                + educationScore * EDUCATION_WEIGHT + salaryScore * SALARY_WEIGHT
                + majorScore * MAJOR_WEIGHT;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("skill", round(skillScore));
        breakdown.put("city", round(cityScore));
        breakdown.put("education", round(educationScore));
        breakdown.put("salary", round(salaryScore));
        breakdown.put("major", round(majorScore));

        String suggestion = buildSuggestion(missing);

        return new GapAnalysisResponse(position.getId(), position.getTitle(),
                new ArrayList<>(matched), new ArrayList<>(missing), new ArrayList<>(extra),
                breakdown, total, suggestion);
    }

    // ---- 五维得分计算 ----

    double calcSkillScore(List<String> studentSkills, List<String> positionSkills) {
        if (studentSkills.isEmpty() || positionSkills.isEmpty()) {
            return 0.0;
        }
        Set<String> studentSet = new HashSet<>(studentSkills);
        Set<String> positionSet = new HashSet<>(positionSkills);

        Set<String> intersection = new HashSet<>(studentSet);
        intersection.retainAll(positionSet);

        Set<String> union = new HashSet<>(studentSet);
        union.addAll(positionSet);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    double calcCityScore(List<String> preferredCities, String positionCity) {
        if (preferredCities.isEmpty() || positionCity == null || positionCity.isBlank()) {
            return 0.0;
        }
        String normalized = positionCity.trim();
        for (String city : preferredCities) {
            if (city.contains(normalized) || normalized.contains(city)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    double calcEducationScore(String studentEducation, String positionEducation) {
        int studentLevel = toEducationLevel(studentEducation);
        int positionLevel = toEducationLevel(positionEducation);
        if (positionLevel == 1) return 1.0; // 岗位不限学历
        if (studentLevel >= positionLevel) return 1.0; // 学历满足或超出要求
        int diff = positionLevel - studentLevel;
        return Math.max(0.0, 1.0 - diff / 4.0);
    }

    double calcSalaryScore(Integer studentMin, Integer studentMax,
                          Integer positionMin, Integer positionMax) {
        if (studentMin == null && studentMax == null) return 0.0;
        if (positionMin == null && positionMax == null) return 0.0;

        int sMin = studentMin != null ? studentMin : 0;
        int sMax = studentMax != null ? studentMax : Integer.MAX_VALUE;
        int pMin = positionMin != null ? positionMin : 0;
        int pMax = positionMax != null ? positionMax : Integer.MAX_VALUE;

        int overlapStart = Math.max(sMin, pMin);
        int overlapEnd = Math.min(sMax, pMax);

        if (overlapStart >= overlapEnd) return 0.0;

        double overlap = overlapEnd - overlapStart;
        double studentRange = Math.max(1, sMax - sMin);
        double positionRange = Math.max(1, pMax - pMin);
        double maxRange = Math.max(studentRange, positionRange);

        return Math.min(1.0, overlap / maxRange);
    }

    double calcMajorScore(String studentMajor, String positionTitle) {
        if (studentMajor == null || studentMajor.isBlank()) return 0.0;
        if (positionTitle == null || positionTitle.isBlank()) return 0.0;

        String major = studentMajor.trim().toLowerCase(Locale.ROOT);
        String title = positionTitle.trim().toLowerCase(Locale.ROOT);

        // 提取专业关键词（按常见分隔符拆分）
        Set<String> majorWords = tokenize(major);
        Set<String> titleWords = tokenize(title);

        if (majorWords.isEmpty() || titleWords.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(majorWords);
        intersection.retainAll(titleWords);

        return (double) intersection.size() / Math.max(majorWords.size(), titleWords.size());
    }

    // ---- 辅助方法 ----

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) return List.of();
        return skills.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> parseCities(String preferredCity) {
        if (preferredCity == null || preferredCity.isBlank()) return List.of();
        return Arrays.stream(preferredCity.split("[,，、\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private int toEducationLevel(String education) {
        if (education == null || education.isBlank()) return 1;
        return EDUCATION_LEVEL.entrySet().stream()
                .filter(e -> education.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(1);
    }

    private Set<String> tokenize(String text) {
        // 按常见分隔符和每2-4个字符切分中文
        Set<String> tokens = new HashSet<>();
        // 按非字母数字分割
        String[] parts = text.split("[^a-z0-9\\u4e00-\\u9fa5]+");
        for (String part : parts) {
            if (part.length() >= 2) tokens.add(part);
            // 中文：补充2-3字片段
            if (part.length() >= 4) {
                for (int i = 0; i <= part.length() - 2; i++) {
                    tokens.add(part.substring(i, Math.min(i + 3, part.length())));
                }
            }
        }
        return tokens;
    }

    private String buildSuggestion(Set<String> missingSkills) {
        if (missingSkills.isEmpty()) {
            return "你的技能完全匹配该岗位要求，继续保持！";
        }
        StringBuilder sb = new StringBuilder("建议学习以下技能以提升竞争力：");
        List<String> sorted = new ArrayList<>(missingSkills);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < Math.min(sorted.size(), 10); i++) {
            if (i > 0) sb.append("、");
            sb.append(sorted.get(i));
        }
        if (missingSkills.size() > 10) {
            sb.append("等").append(missingSkills.size()).append("项技能");
        }
        sb.append("。");
        return sb.toString();
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
