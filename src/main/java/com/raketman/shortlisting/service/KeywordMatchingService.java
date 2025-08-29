package com.raketman.shortlisting.service;

import com.raketman.shortlisting.config.SkillsProperties;
import com.raketman.shortlisting.entity.CV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KeywordMatchingService {

    private static final Logger log = LoggerFactory.getLogger(KeywordMatchingService.class);

    @Value("${cv.keywords.required:java,spring}")
    private String requiredKeywordsStr;

    @Value("${cv.keywords.optional:aws,docker,microservices}")
    private String optionalKeywordsStr;

    @Value("${cv.keywords.excluded:intern,internship,fresher}")
    private String excludedKeywordsStr;

    @Value("${cv.matching.mode:AND}")
    private String matchingMode;

    @Value("${cv.matching.threshold:70}")
    private int matchingThreshold;

    private  final Map<String, Set<String>> SKILL_CATEGORIES;

    public KeywordMatchingService(SkillsProperties properties) {
        this.SKILL_CATEGORIES = properties.getCategories();
        log.info("Loaded {} skill categories from configuration", SKILL_CATEGORIES.size());
    }

    public Set<String> extractSkills(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptySet();
        }

        String contentLower = content.toLowerCase();

        Set<String> foundSkills = SKILL_CATEGORIES.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(variation -> containsSkill(contentLower, variation)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        log.debug("Extracted {} skills from CV content", foundSkills.size());
        return foundSkills;
    }

    public boolean matchesCriteria(CV cv) {
        if (cv == null || cv.getContent() == null) {
            return false;
        }

        Set<String> cvSkills = Optional.ofNullable(cv.getSkills()).orElse(Collections.emptySet());
        String content = cv.getContent().toLowerCase();

        log.info("CV Skills found: {}", cvSkills);
        log.info("Required keywords: {}", parseKeywords(requiredKeywordsStr));
        log.info("Matching mode: {}", matchingMode);

        if (containsExcludedKeywords(content)) {
            log.debug("CV [{}] rejected due to excluded keywords", cv.getFileName());
            return false;
        }

        Set<String> requiredKeywords = parseKeywords(requiredKeywordsStr);
        Set<String> optionalKeywords = parseKeywords(optionalKeywordsStr);

        switch (matchingMode.trim().toUpperCase()) {
            case "AND":
                return cvSkills.containsAll(requiredKeywords);
            case "OR":
                return requiredKeywords.stream().anyMatch(cvSkills::contains);
            case "WEIGHTED":
                return matchesWeighted(cvSkills, requiredKeywords, optionalKeywords);
            default:
                log.warn("Unknown matching mode '{}', defaulting to AND", matchingMode);
                return cvSkills.containsAll(requiredKeywords);
        }
    }

    private boolean matchesWeighted(Set<String> cvSkills, Set<String> requiredKeywords, Set<String> optionalKeywords) {
        int totalScore = requiredKeywords.stream().mapToInt(req -> cvSkills.contains(req) ? 10 : 0).sum()
                + optionalKeywords.stream().mapToInt(opt -> cvSkills.contains(opt) ? 5 : 0).sum();

        int maxPossibleScore = requiredKeywords.size() * 10 + optionalKeywords.size() * 5;

        if (maxPossibleScore == 0) {
            return false;
        }

        int percentage = (totalScore * 100) / maxPossibleScore;
        log.debug("Weighted score: {}% (threshold: {}%)", percentage, matchingThreshold);

        return percentage >= matchingThreshold;
    }

    private boolean containsExcludedKeywords(String content) {
        return parseKeywords(excludedKeywordsStr).stream()
                .anyMatch(excluded -> containsSkill(content, excluded));
    }


    private boolean containsSkill(String content, String skill) {
        String pattern = "\\b" + Pattern.quote(skill.toLowerCase()) + "\\b";
        return Pattern.compile(pattern).matcher(content).find();
    }


    private Set<String> parseKeywords(String keywordsStr) {
        return Optional.ofNullable(keywordsStr)
                .map(str -> Arrays.stream(str.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }


    public Map<String, Set<String>> getAvailableSkills() {
        return SKILL_CATEGORIES;
    }

    public void addSkillCategory(String skillName, Set<String> variations) {
        Objects.requireNonNull(skillName, "Skill name must not be null");
        Objects.requireNonNull(variations, "Variations must not be null");

        Set<String> normalized = variations.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Map<String, Set<String>> updated = new HashMap<>(SKILL_CATEGORIES);
        updated.put(skillName.toLowerCase(), normalized);

        log.info("Added/Updated skill category '{}' with {} variations", skillName, normalized.size());
    }

    public Map<String, Object> getMatchingConfiguration() {
        return Map.of(
                "requiredKeywords", parseKeywords(requiredKeywordsStr),
                "optionalKeywords", parseKeywords(optionalKeywordsStr),
                "excludedKeywords", parseKeywords(excludedKeywordsStr),
                "matchingMode", matchingMode,
                "matchingThreshold", matchingThreshold
        );
    }
}
