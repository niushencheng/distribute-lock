package com.niushencheng.interceptor;

import com.niushencheng.MTLock;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Spel被解析对象
 * <p>
 * CreateTime: 2017-07-08 12:40:51
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
class LockExpressionRootObject {

    private final List<? extends MTLock> locks;

    private final Method method;

    private final Object[] args;

    private final Object target;

    private final Class<?> targetClass;


    public LockExpressionRootObject(
            List<? extends MTLock> locks, Method method, Object[] args, Object target, Class<?> targetClass) {

        Assert.notNull(method, "Method is required");
        Assert.notNull(targetClass, "targetClass is required");
        this.method = method;
        this.target = target;
        this.targetClass = targetClass;
        this.args = args;
        this.locks = locks;
    }


    public List<? extends MTLock> getCaches() {
        return this.locks;
    }

    public Method getMethod() {
        return this.method;
    }

    public String getMethodName() {
        return this.method.getName();
    }

    public Object[] getArgs() {
        return this.args;
    }

    public Object getTarget() {
        return this.target;
    }

    public Class<?> getTargetClass() {
        return this.targetClass;
    }

}
