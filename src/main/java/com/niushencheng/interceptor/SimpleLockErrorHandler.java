package com.niushencheng.interceptor;

/**
 * 一个简单的异常处理器，并不处理异常，只是上报 {@link LockErrorHandler}
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public class SimpleLockErrorHandler implements LockErrorHandler {
    @Override
    public void handleLockError() {
        
    }

    @Override
    public void handleUnlockError() {

    }
}
