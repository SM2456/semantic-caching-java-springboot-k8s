package com.acme.cache.core;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisIndexInitializer {
    @Bean
    ApplicationRunner init(RedisSemanticStore store) {
        return args -> store.ensureIndex();
    }
}
