package com.raketman.shortlisting.config;


import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("fuzzyMatches");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)  // TTL = 3s
                .maximumSize(1000));
        return cacheManager;
    }
}
