package com.loanflow.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Global test configuration to prevent the entire test suite from failing
 * when an external Redis server is not available. This is automatically 
 * picked up by @SpringBootTest via component scanning in the test classpath.
 */
@Configuration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory mockRedisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}
