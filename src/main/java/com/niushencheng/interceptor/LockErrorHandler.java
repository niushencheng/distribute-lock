package com.niushencheng.interceptor;

/**
 * 加锁异常处理器，默认使用 {@link SimpleLockErrorHandler}
 * <p>
 * CreateTime: 2017-07-08 12:40:28
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public interface LockErrorHandler {

    void handleLockError();

    void handleUnlockError();
}
