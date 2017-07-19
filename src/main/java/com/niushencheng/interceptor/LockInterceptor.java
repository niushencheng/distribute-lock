package com.niushencheng.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * 切面类，切入需要加分布式锁的类
 * <p>
 * CreateTime: 2017-07-08 12:41:48
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class LockInterceptor extends LockAspectSupport {

    @Pointcut("@annotation(com.niushencheng.annotation.DistributeLock)")
    public void annotation() {}

    @Around("annotation()")
    public Object annotation(final ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();
        LockOperationInvoker invoker = new LockOperationInvoker() {
            @Override
            public Object invoke() throws ThrowableWrapper {
                try {
                    return pjp.proceed();
                } catch (Throwable e) {
                    throw new ThrowableWrapper(e);
                }
            }
        };

        try {
            return super.execute(invoker, pjp.getTarget(), method, pjp.getArgs());
        } catch (LockOperationInvoker.ThrowableWrapper e) {
            throw e.getOriginal();
        }
    }
}
