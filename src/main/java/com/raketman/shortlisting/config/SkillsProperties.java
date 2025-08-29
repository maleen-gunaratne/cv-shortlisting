package com.raketman.shortlisting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "skills")

public class SkillsProperties {

    private Map<String, Set<String>> categories;

}
