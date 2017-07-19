package com.niushencheng.support;

import com.niushencheng.MTLock;

public class RedisLock implements MTLock {
    @Override
    public boolean lock() {
        return false;
    }

    @Override
    public void unlock() {

    }

    @Override
    public String getName() {
        return null;
    }
}
