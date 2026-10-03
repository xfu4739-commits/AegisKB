package org.aegiskb.config;

import org.aegiskb.constant.LockInfoType;
import org.aegiskb.core.ManageLocker;
import org.aegiskb.lockinfo.LockInfoHandle;
import org.aegiskb.lockinfo.factory.LockInfoHandleFactory;
import org.aegiskb.lockinfo.impl.ServiceLockInfoHandle;
import org.aegiskb.servicelock.aspect.ServiceLockAspect;
import org.aegiskb.servicelock.factory.ServiceLockFactory;
import org.aegiskb.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

public class ServiceLockAutoConfiguration {

    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }

    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }

    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }

    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }

    @Bean
    public ServiceLockTool serviceLockUtil(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
