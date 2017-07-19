package com.niushencheng.interceptor;

import java.lang.reflect.Method;

public interface LockOperationInvocationContext<O extends BasicOperation> {

    /**
     * Return the cache operation.
     */
    O getOperation();

    /**
     * Return the target instance on which the method was invoked.
     */
    Object getTarget();

    /**
     * Return the method which was invoked.
     */
    Method getMethod();

    /**
     * Return the argument list used to invoke the method.
     */
    Object[] getArgs();

}

