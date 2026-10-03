package org.aegiskb.config;

import org.aegiskb.lease.RedisLeaseManager;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

public class ServiceLeaseAutoConfiguration {

    @Bean
    public RedisLeaseManager redisLeaseManager(RedissonClient redissonClient) {
        return new RedisLeaseManager(redissonClient);
    }
}
