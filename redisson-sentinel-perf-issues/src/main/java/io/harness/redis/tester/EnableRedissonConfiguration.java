package io.harness.redis.tester;

import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "redis.checker.driver", havingValue = "redisson")
@ImportAutoConfiguration(RedissonAutoConfiguration.class)
public class EnableRedissonConfiguration {
}
