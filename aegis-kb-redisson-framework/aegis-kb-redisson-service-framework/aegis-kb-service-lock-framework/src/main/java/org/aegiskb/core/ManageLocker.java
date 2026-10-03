package org.aegiskb.core;

import org.aegiskb.servicelock.LockType;
import org.aegiskb.servicelock.ServiceLocker;
import org.aegiskb.servicelock.impl.RedissonFairLocker;
import org.aegiskb.servicelock.impl.RedissonReadLocker;
import org.aegiskb.servicelock.impl.RedissonReentrantLocker;
import org.aegiskb.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static org.aegiskb.servicelock.LockType.Fair;
import static org.aegiskb.servicelock.LockType.Read;
import static org.aegiskb.servicelock.LockType.Reentrant;
import static org.aegiskb.servicelock.LockType.Write;

public class ManageLocker {

    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();

    public ManageLocker(RedissonClient redissonClient){
        cacheLocker.put(Reentrant,new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair,new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write,new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read,new RedissonReadLocker(redissonClient));
    }

    public ServiceLocker getReentrantLocker(){
        return cacheLocker.get(Reentrant);
    }

    public ServiceLocker getFairLocker(){
        return cacheLocker.get(Fair);
    }

    public ServiceLocker getWriteLocker(){
        return cacheLocker.get(Write);
    }

    public ServiceLocker getReadLocker(){
        return cacheLocker.get(Read);
    }
}
