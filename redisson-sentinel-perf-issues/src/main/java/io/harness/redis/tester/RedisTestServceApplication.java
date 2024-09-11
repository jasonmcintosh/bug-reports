package io.harness.redis.tester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@EnableRedisRepositories

public class RedisTestServceApplication {
	public static final int LEASE_DURATION = 1;

	public static void main(String[] args) {
		SpringApplication.run(RedisTestServceApplication.class, args);
	}

}
