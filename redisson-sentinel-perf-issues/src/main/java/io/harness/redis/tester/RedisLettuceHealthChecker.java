package io.harness.redis.tester;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static io.harness.redis.tester.RedisTestServceApplication.LEASE_DURATION;

@Setter
@Log
@Component
@ConditionalOnProperty(value = "redis.checker.driver", havingValue = "lettuce")
public class RedisLettuceHealthChecker extends AbstractHealthIndicator {

    private final Cache cache;
    private final MeterRegistry meterRegistry;
    private final RedisLockRegistry lockRegistry;
    private final int timeoutSeconds;

    @Autowired
    public RedisLettuceHealthChecker(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry, @Value("${redis.checker.timeoutSeconds:1}") int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        log.info("Setting timeout seconds to " + timeoutSeconds);
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory).cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()).build();
        this.cache = cacheManager.getCache("hCache/pmsEventsCacheDeleteTestSpring");
        this.lockRegistry = new RedisLockRegistry(connectionFactory, "hCache/pmsEventsCacheDeleteTestSpring", 30 * 1000);
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // Check locks
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String uuid = UUID.randomUUID().toString();
        String lockId = "lock:HEALTH_CHECK - " + uuid;
        Lock lock = lockRegistry.obtain(lockId);
        log.info("Lock time ms:" + stopWatch.getTotalTimeMillis());
        if (lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
            lock.unlock();
        }else {
            builder.down().withDetail("tryLockFialure", false).withDetail("lockId", lockId);
        }
        builder.withDetail("lockTimeMs", stopWatch.getTotalTimeMillis()).withDetail("lockId", lockId);
        stopWatch = new StopWatch();
        stopWatch.start();
        cache.putIfAbsent("pmsEventsCacheDeleteTestSpring" + uuid, 1);
        stopWatch.stop();
        meterRegistry.timer("putIfAbsent").record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
        builder.withDetail("putIfAbsent", stopWatch.getTotalTimeMillis());
        log.info("Cache put time ms:" + stopWatch.getTotalTimeMillis());
        builder.up();
    }
}
