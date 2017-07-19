package com.niushencheng.interceptor;

import org.springframework.util.Assert;

/**
 * 基础的LockOperation对应的invoker，设置了一个异常处理器，
 * 遇到异常会将内容直接抛出
 * <p>
 * CreateTime: 2017-07-07 01:32:08
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public abstract class AbstractLockInvoker {
    /** 异常处理器 */
    private LockErrorHandler errorHandler;

    protected AbstractLockInvoker(LockErrorHandler errorHandler) {
        Assert.notNull(errorHandler, "ErrorHandler must not be null");
        this.errorHandler = errorHandler;
    }

    protected AbstractLockInvoker() {
        this(new SimpleLockErrorHandler());
    }

    public LockErrorHandler getErrorHandler() {
        return errorHandler;
    }
}
