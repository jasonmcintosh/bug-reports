package io.harness.redis.tester;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Setter;
import lombok.extern.java.Log;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.jcache.configuration.RedissonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static io.harness.redis.tester.RedisTestServceApplication.LEASE_DURATION;

@Setter
@Log
@Component
@ConditionalOnProperty(value = "redis.checker.driver", havingValue = "redisson")
public class RedisHealthChecker extends AbstractHealthIndicator {

    private final RedissonClient client;
    private final Cache<String, Integer> cache;
    private final MeterRegistry meterRegistry;
    private final int timeoutSeconds;

    @Autowired
    public RedisHealthChecker(RedissonClient client, MeterRegistry meterRegistry, @Value("${redis.checker.timeoutSeconds:0}") int timeoutSeconds) {
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.timeoutSeconds = timeoutSeconds;
        log.info("Setting timeout seconds to " + timeoutSeconds);
        MutableConfiguration<String, Integer> jcacheConfig = new MutableConfiguration<>();
        jcacheConfig.setTypes(String.class, Integer.class);
        jcacheConfig.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES));
        jcacheConfig.setStatisticsEnabled(true);
        jcacheConfig.setManagementEnabled(true);

        Configuration<String, Integer> config = RedissonConfiguration.fromInstance(client, jcacheConfig);
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        cache = manager.createCache("hCache/pmsEventsCacheDeleteTest", config);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // Check locks
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String uuid = UUID.randomUUID().toString();
        String lockId = "lock:HEALTH_CHECK - " + uuid;
        client.getLock(lockId).lock(LEASE_DURATION, TimeUnit.SECONDS);
        stopWatch.stop();
        meterRegistry.timer("lockTimeMs").record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);


        builder.withDetail("lockTimeMs", stopWatch.getTotalTimeMillis()).withDetail("lockId", lockId);
        log.info("Lock time ms:" + stopWatch.getTotalTimeMillis());
        client.getLock(lockId).unlock();
        stopWatch = new StopWatch();
        // Eval cache next...
        try {
            stopWatch.start();
            Boolean locked;
            if (client.getConfig().isSentinelConfig()) {
                log.fine("[RedisSentinelMode]: Trying Async lock");
                locked = client.getLock(lockId).tryLockAsync(timeoutSeconds, LEASE_DURATION, TimeUnit.SECONDS).get(timeoutSeconds, TimeUnit.SECONDS);
                log.fine("[RedisSentinelMode]: Async lock acquired successfully");
            } else {
                locked = client.getLock(lockId).tryLock(timeoutSeconds, LEASE_DURATION, TimeUnit.SECONDS);
            }
            client.getLock(lockId).unlock();
            stopWatch.stop();
            meterRegistry.timer("asyncLockDurationMs").record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
            log.info("Async lock time ms:" + stopWatch.getTotalTimeMillis());

            builder.withDetail("asyncLockStatus", locked);
            builder.withDetail("asyncLockDurationMs", stopWatch.getTotalTimeMillis());
            builder.up();
        }catch (Exception e) {
            builder.down();
            log.log(Level.SEVERE, "Unable to get a lock!", e);
        }


        stopWatch = new StopWatch();
        stopWatch.start();
        boolean succeeded = cache.putIfAbsent("pmsEventsCacheDeleteAfterTest"+uuid, 1);
        stopWatch.stop();
        meterRegistry.timer("putIfAbsent").record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
        builder.withDetail("putIfAbsentMs", stopWatch.getTotalTimeMillis());
        log.info("Cache put time ms:" + stopWatch.getTotalTimeMillis());


        if (!succeeded) {
            log.severe("Unable to write key pmsEventsCacheDeleteAfterTest"+uuid);
            builder.down();
            builder.withDetail("pmsEventsCacheDeleteAfterTest"+uuid, succeeded);
        }
    }
}
