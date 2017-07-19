package com.niushencheng.support;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.niushencheng.LockManager;
import com.niushencheng.MTLock;
import com.niushencheng.interceptor.LockAspectSupport;
import com.niushencheng.interceptor.LockOperation;
import com.niushencheng.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SquirrelLockManager implements LockManager {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /** 存储lockOperation, 默认大小1024，超时180s */
    private final Cache<LockOperation, SquirrelLock> _lockCache = CacheBuilder.newBuilder()
                                                                              .maximumSize(1024L)
                                                                              .expireAfterAccess(180,
                                                                                                 TimeUnit.SECONDS)
                                                                              .build();

    private SquirrelClient squirrelClient;

    private final String lockPrefix = "MTLock:";

    @Override
    public synchronized MTLock getLock(LockAspectSupport.LockOperationContext context) {
        LockOperation operation = context.getOperation();
        SquirrelLock lock = _lockCache.getIfPresent(operation);
        if (lock == null) {
            LOGGER.info("没有从_lockCache中获取到squirrelLock..., thread -->> {}, operation-->>{}",
                        this.threadName(),
                        JacksonUtil.toJsonStr(operation));
            lock = new SquirrelLock(lockPrefix + operation.getName(),
                                    operation.getKey(),
                                    operation.getTimeout(),
                                    operation.getExpire(),
                                    operation.getErrorMsg(),
                                    squirrelClient);
            _lockCache.put(operation, lock);
        } else {
            LOGGER.info("从_lockCache中获取到squirrelLock..., thread -->> {}, operation-->>{}",
                        this.threadName(),
                        JacksonUtil.toJsonStr(operation));
        }

        return lock;
    }

    private String threadName() {
        return Thread.currentThread().getName();
    }
}
