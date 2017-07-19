package com.niushencheng.interceptor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.niushencheng.MTLock;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

class LockOperationExpressionEvaluator extends LockExpressionEvaluator {

    /**
     * Indicate that there is no result variable.
     */
    public static final Object NO_RESULT = new Object();

    /**
     * Indicate that the result variable cannot be used at all.
     */
    public static final Object RESULT_UNAVAILABLE = new Object();

    /**
     * The name of the variable holding the result object.
     */
    public static final String RESULT_VARIABLE = "result";

    // shared param discoverer since it caches data internally
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** 使用guava local cache，默认大小1024，180s后过期 */
    private final Cache<ExpressionKey, Expression> _keyCache = CacheBuilder.newBuilder()
                                                                           .maximumSize(1024L)
                                                                           .expireAfterAccess(180,
                                                                                              TimeUnit.SECONDS)
                                                                           .build();

    /** 使用guava local cache，默认大小1024，180s后过期 */
    private final Cache<AnnotatedElementKey, Method> _targetMethodCache = CacheBuilder.newBuilder()
                                                                                      .maximumSize(1024L)
                                                                                      .expireAfterAccess(180,
                                                                                              TimeUnit.SECONDS)
                                                                                      .build();

    /**
     * Create an {@link EvaluationContext} without a return value.
     *
     * @see #createEvaluationContext(List, Method, Object[], Object, Class, Object)
     */
    public EvaluationContext createEvaluationContext(List<? extends MTLock> locks,
                                                     Method method, Object[] args, Object target, Class<?> targetClass) {

        return createEvaluationContext(locks, method, args, target, targetClass, NO_RESULT);
    }

    /**
     * Create an {@link EvaluationContext}.
     *
     * @param locks       the current caches
     * @param method      the method
     * @param args        the method arguments
     * @param target      the target object
     * @param targetClass the target class
     * @param result      the return value (can be {@code null}) or
     *                    {@link #NO_RESULT} if there is no return at this time
     *
     * @return the evaluation context
     */
    public EvaluationContext createEvaluationContext(List<? extends MTLock> locks,
                                                     Method method, Object[] args, Object target, Class<?> targetClass, Object result) {

        LockExpressionRootObject rootObject = new LockExpressionRootObject(locks,
                                                                           method, args, target, targetClass);
        Method targetMethod = getTargetMethod(targetClass, method);
        LockEvaluationContext evaluationContext = new LockEvaluationContext(rootObject,
                                                                            targetMethod,
                                                                            args,
                                                                            this.paramNameDiscoverer);
        if (result == RESULT_UNAVAILABLE) {
            evaluationContext.addUnavailableVariable(RESULT_VARIABLE);
        } else if (result != NO_RESULT) {
            evaluationContext.setVariable(RESULT_VARIABLE, result);
        }
        return evaluationContext;
    }

    /** 这里可以不用加锁，因为缓存中重复put对执行过程没有影响 */
    public Object key(String keyExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        return getExpression(this._keyCache, methodKey, keyExpression).getValue(evalContext);
    }

    /**
     * Clear all caches.
     */
    void clear() {
        this._keyCache.invalidateAll();
        this._targetMethodCache.invalidateAll();
    }

    /** 这里可以不用加锁，因为缓存中重复put对执行过程没有影响 */
    private Method getTargetMethod(Class<?> targetClass, Method method) {
        AnnotatedElementKey methodKey = new AnnotatedElementKey(method, targetClass);
        Method targetMethod = this._targetMethodCache.getIfPresent(methodKey);
        if (targetMethod == null) {
            targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            if (targetMethod == null) {
                targetMethod = method;
            }
            this._targetMethodCache.put(methodKey, targetMethod);
        }
        return targetMethod;
    }


}
