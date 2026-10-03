package org.aegiskb.config;

import org.aegiskb.constant.LockInfoType;
import org.aegiskb.handle.RedissonDataHandle;
import org.aegiskb.locallock.LocalLockCache;
import org.aegiskb.lockinfo.LockInfoHandle;
import org.aegiskb.lockinfo.factory.LockInfoHandleFactory;
import org.aegiskb.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import org.aegiskb.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import org.aegiskb.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;

public class RepeatExecuteLimitAutoConfiguration {

    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }

    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
