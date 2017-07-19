package com.niushencheng.annotation;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.niushencheng.interceptor.LockOperation;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;

/**
 * 解析注解
 *
 * CreateTime: 2017-07-10 20:57:34
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public abstract class LockAnnotationParser {

    /** 使用guava local cache，默认大小1024，180s后过期 */
    private static final Cache<AnnotatedElementKey, LockOperation> _operationCache = CacheBuilder.newBuilder()
                                                                                                 .maximumSize(1024L)
                                                                                                 .expireAfterAccess(180,
                                                                                                                    TimeUnit.SECONDS)
                                                                                                 .build();

    private static LockOperation parseLockAnnotation(Class<?> targetClass, Method method) {
        return parseAnnotation(targetClass, method);
    }

    private static LockOperation parseAnnotation(Class<?> targetClass, Method method) {
        // 只允许public的方法
        if (!Modifier.isPublic(method.getModifiers())) {
            return null;
        }
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
        specificMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);

        return findLockOperation(specificMethod);
    }

    private static LockOperation findLockOperation(Method specificMethod) {
        DistributeLock dl = specificMethod.getAnnotation(DistributeLock.class);
        return new LockOperation(dl.name(), dl.key(), dl.errorMsg(), dl.timeout(), dl.expire());
    }

    // 注意！！！
    // 此处需要加锁，避免每次过来拿到的是不同的LockOperation
    public synchronized static LockOperation getLockOperation(Class<?> targetClass, Method method) {
        AnnotatedElementKey lockKey = getLockKey(targetClass, method);
        LockOperation lockOperation = _operationCache.getIfPresent(lockKey);
        if (lockOperation == null) {
            lockOperation = parseLockAnnotation(targetClass, method);
            _operationCache.put(lockKey, lockOperation);
        }
        return lockOperation;
    }

    private static AnnotatedElementKey getLockKey(Class<?> targetClass, Method method) {
        return new AnnotatedElementKey(method, targetClass);
    }


    private LockAnnotationParser() {}

}
