package com.niushencheng.support;

import com.niushencheng.LockManager;
import com.niushencheng.MTLock;
import com.niushencheng.interceptor.LockAspectSupport;

public class RedisLockManager implements LockManager {
    @Override
    public MTLock getLock(LockAspectSupport.LockOperationContext context) {
        return null;
    }
}
