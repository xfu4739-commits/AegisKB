package org.aegiskb.repeatexecutelimit.aspect;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aegiskb.constant.LockInfoType;
import org.aegiskb.exception.SuperAgentFrameException;
import org.aegiskb.handle.RedissonDataHandle;
import org.aegiskb.locallock.LocalLockCache;
import org.aegiskb.lockinfo.LockInfoHandle;
import org.aegiskb.lockinfo.factory.LockInfoHandleFactory;
import org.aegiskb.repeatexecutelimit.annotion.RepeatExecuteLimit;
import org.aegiskb.servicelock.LockType;
import org.aegiskb.servicelock.ServiceLocker;
import org.aegiskb.servicelock.factory.ServiceLockFactory;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.aegiskb.repeatexecutelimit.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static org.aegiskb.repeatexecutelimit.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

/**
 * 防重复幂等 切面
 */
@Slf4j
@Aspect
@Order(-11)
@AllArgsConstructor
public class RepeatExecuteLimitAspect {

    private final LocalLockCache localLockCache;

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;

    private final RedissonDataHandle redissonDataHandle;

    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
        long durationTime = repeatLimit.durationTime();
        String message = repeatLimit.message();
        Object obj;
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        String lockName = lockInfoHandle.getLockName(joinPoint,repeatLimit.name(), repeatLimit.keys());
        String repeatFlagName = PREFIX_NAME + lockName;
        String flagObject = redissonDataHandle.get(repeatFlagName);
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new SuperAgentFrameException(message);
        }
        ReentrantLock localLock = localLockCache.getLock(lockName,true);
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            throw new SuperAgentFrameException(message);
        }
        try {
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Fair);
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            if (result) {
                try{
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new SuperAgentFrameException(message);
                    }
                    obj = joinPoint.proceed();
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlagName,SUCCESS_FLAG,durationTime,TimeUnit.SECONDS);
                        }catch (Exception e) {
                            log.error("getBucket error",e);
                        }
                    }
                    return obj;
                } finally {
                    lock.unlock(lockName);
                }
            }else{
                throw new SuperAgentFrameException(message);
            }
        }finally {
            localLock.unlock();
        }
    }
}
