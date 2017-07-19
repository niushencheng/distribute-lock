package com.niushencheng.interceptor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.niushencheng.LockManager;
import com.niushencheng.MTLock;
import com.niushencheng.annotation.LockAnnotationParser;
import com.niushencheng.util.JacksonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.expression.EvaluationContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 切面类，获取MTLock实现类，加锁后执行原有的逻辑，
 * 加锁失败后会抛出异常 {@link LockFailedException},
 * 异常信息为DistributeLock中定义的errorMsg()
 * <p>
 * CreateTime: 2017-07-08 12:40:13
 *
 * @author zicheng.liang
 * @version 1.0
 * @see LockFailedException
 * @see com.niushencheng.annotation.DistributeLock#errorMsg()
 * @since JDK 1.7
 */
public class LockAspectSupport extends AbstractLockInvoker implements InitializingBean, DisposableBean {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final LockOperationExpressionEvaluator evaluator = new LockOperationExpressionEvaluator();

    /** 使用guava local cache，默认大小1024，180s后过期 */
    private final Cache<LockOperationLockKey, LockOperationMetadata> _metadataCache = CacheBuilder.newBuilder()
                                                                                                  .maximumSize(1024L)
                                                                                                  .expireAfterAccess(180,
                                                                                                                     TimeUnit.SECONDS)
                                                                                                  .build();
    /** 使用guava local cache，默认大小1024，180s后过期 */
    private final Cache<MTLock, Lock> _lockCache = CacheBuilder.newBuilder()
                                                               .maximumSize(1024L)
                                                               .expireAfterAccess(180,
                                                                                  TimeUnit.SECONDS)
                                                               .build();

    /** 锁管理 */
    @Setter
    private LockManager lockManager;

    private boolean initialized = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        initialized = true;
    }

    @Override
    public void destroy() throws Exception {
        this._metadataCache.invalidateAll();
        this.evaluator.clear();
    }

    protected Object execute(LockOperationInvoker invoker, Object target, Method method, Object[] args) {
        if (this.initialized) {
            Class<?> targetClass = this.getTargetClass(target);
            List<LockOperation> operations = this.parseAnnotations(targetClass, method, method);
            if (CollectionUtils.isNotEmpty(operations)) {
                return this.execute(invoker, new LockOperationContexts(operations, method, args, target, targetClass));
            }
        }
        return invoker.invoke();
    }

    private Object execute(LockOperationInvoker invoker, LockOperationContexts contexts) {
        List<LockOperationContext> lockOperationContexts = contexts.get(LockOperation.class);
        if (CollectionUtils.isEmpty(lockOperationContexts)) {
            // 没有加锁操作直接执行
            return invoker.invoke();
        }
        // lockContexts中的operation永远只有一个
        LockOperationContext context = lockOperationContexts.get(0);
        LockOperation operation = context.getOperation();

        synchronized (operation) {
            // 避免相同operation重复计算parsedKey
            if (!operation.isParsedKey()) {
                // 需要计算真实缓存中的key, 用作区分细力度锁
                this.evaluateLockKey(context);
            }
        }
        MTLock mtLock = this.lockManager.getLock(context);
        String lockStr = JacksonUtil.toJsonStr(mtLock);

        // 获取分布式锁
        try {
            LOGGER.info("尝试获取分布式锁, thread -->> {}, mtLock -->> {}, timestamp -->> {}",
                        this.threadName(),
                        lockStr,
                        this.timeStamp());
            if (mtLock.lock()) {
                LOGGER.info("成功获取到分布式锁，thread -->> {}, mtLock -->> {}, timestamp -->> {}",
                            this.threadName(),
                            lockStr,
                            this.timeStamp());
                return invoker.invoke();
            }
            LOGGER.warn("获取分布式锁失败了, thread -->> {}, mtLock -->> {}, timestamp -->> {}",
                        this.threadName(),
                        lockStr,
                        this.timeStamp());
            throw new LockFailedException(operation.getErrorMsg());
        } finally {
            LOGGER.info("准备释放分布式锁, thread -->> {}, mtLock -->> {}, timestamp -->> {}",
                        this.threadName(),
                        lockStr,
                        this.timeStamp());
            mtLock.unlock();
        }
    }

    /**
     * 计算真实缓存中的key，用于细力度锁的区分
     *
     * @param context 上下文环境
     */
    private void evaluateLockKey(LockOperationContext context) {
        // 使用spel解析注解中的key的值
        LockOperation operation = context.getOperation();
        Method method = context.metadata.method;

        String key = String.valueOf(context.generateKey(context));
        // 这里为了区分不同的加锁操作，解析的时候提前将name和key提取
        String className = method.getDeclaringClass().getName();
        // totalPrefix = subPrefix + className + methodName + key
        // subPrefix在lockManager中获取
        String name = className + ":"
                + (StringUtils.isBlank(operation.getName())
                ? method.getName()
                : operation.getName()) + ":";

        operation.setName(name);
        operation.setKey(key);
        // 将是否解析过置true
        operation.setParsedKey(true);
    }

    private String threadName() {
        return Thread.currentThread().getName();
    }

    private long timeStamp() {
        return System.currentTimeMillis();
    }

    /**
     * List<LockOperation>的值永远只有一个
     *
     * @param targetClass 目标
     * @param ae          方法
     * @param method      方法
     *
     * @return 锁操作
     */
    private List<LockOperation> parseAnnotations(Class<?> targetClass, AnnotatedElement ae, Method method) {
        List<LockOperation> list = new ArrayList<LockOperation>(1);
        LockOperation lockOperation = LockAnnotationParser.getLockOperation(targetClass, method);
        if (lockOperation != null) {
            list.add(lockOperation);
        }
        return list;
    }

    private Class<?> getTargetClass(Object target) {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
        if (targetClass == null && target != null) {
            targetClass = target.getClass();
        }
        return targetClass;
    }

    private class LockOperationContexts {
        private final MultiValueMap<Class<? extends LockOperation>, LockOperationContext> contexts =
                new LinkedMultiValueMap<Class<? extends LockOperation>, LockOperationContext>();

        LockOperationContexts(List<? extends LockOperation> operations, Method method,
                              Object[] args, Object target, Class<?> targetClass) {
            for (LockOperation operation : operations) {
                this.contexts.add(operation.getClass(),
                                  getOperationContext(operation, method, args, target, targetClass));
            }
        }

        public List<LockOperationContext> get(Class<? extends LockOperation> operationClass) {
            List<LockOperationContext> result = this.contexts.get(operationClass);
            return (result != null
                    ? result
                    : Collections.<LockOperationContext>emptyList());
        }
    }

    public LockOperationContext getOperationContext(
            LockOperation operation, Method method, Object[] args, Object target, Class<?> targetClass) {

        LockOperationMetadata metadata = this.getCacheOperationMetadata(operation, method, targetClass);
        return new LockOperationContext(metadata, args, target);
    }

    private LockOperationMetadata getCacheOperationMetadata(LockOperation operation, Method method, Class<?> targetClass) {
        LockOperationLockKey lockKey = new LockOperationLockKey(operation, method, targetClass);
        LockOperationMetadata metadata = _metadataCache.getIfPresent(lockKey);
        if (metadata == null) {
            metadata = new LockOperationMetadata(operation, method, targetClass, new SimpleKeyGenerator());
            _metadataCache.put(lockKey, metadata);
        }
        return metadata;
    }

    /**
     * Metadata of a cache operation that does not depend on a particular invocation
     * which makes it a good candidate for caching.
     */
    protected static class LockOperationMetadata {

        private final LockOperation operation;

        private final Method method;

        private final Class<?> targetClass;

        private final KeyGenerator keyGenerator;


        public LockOperationMetadata(LockOperation operation, Method method, Class<?> targetClass,
                                     KeyGenerator keyGenerator) {
            this.operation = operation;
            this.method = method;
            this.targetClass = targetClass;
            this.keyGenerator = keyGenerator;
        }
    }

    @Data
    @EqualsAndHashCode
    public class LockOperationContext implements LockOperationInvocationContext<LockOperation> {

        private final LockOperationMetadata metadata;

        private final Object[] args;

        private final Object target;

        private final AnnotatedElementKey methodCacheKey;

        public LockOperationContext(LockOperationMetadata metadata, Object[] args, Object target) {
            this.metadata = metadata;
            this.args = extractArgs(metadata.method, args);
            this.target = target;
            this.methodCacheKey = new AnnotatedElementKey(metadata.method, metadata.targetClass);
        }

        @Override
        public LockOperation getOperation() {
            return this.metadata.operation;
        }

        @Override
        public Object getTarget() {
            return this.target;
        }

        @Override
        public Method getMethod() {
            return this.metadata.method;
        }

        @Override
        public Object[] getArgs() {
            return this.args;
        }

        private Object[] extractArgs(Method method, Object[] args) {
            if (!method.isVarArgs()) {
                return args;
            }
            Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
            Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
            System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
            System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
            return combinedArgs;
        }


        /**
         * Compute the key for the given lock operation.
         *
         * @return the generated key, or {@code null} if none can be generated
         */
        protected Object generateKey(LockOperationContext result) {
            if (StringUtils.isNotBlank(this.metadata.operation.getKey())) {
                EvaluationContext evaluationContext = createEvaluationContext(result);
                return evaluator.key(this.metadata.operation.getKey(), this.methodCacheKey, evaluationContext);
            }
            return this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
        }

        private EvaluationContext createEvaluationContext(Object result) {
            return evaluator.createEvaluationContext(new ArrayList<MTLock>(), this.metadata.method, this.args,
                                                     this.target, this.metadata.targetClass, result);
        }

    }

    private static class LockOperationLockKey {

        private final LockOperation lockOperation;

        private final AnnotatedElementKey methodCacheKey;

        private LockOperationLockKey(LockOperation lockOperation, Method method, Class<?> targetClass) {
            this.lockOperation = lockOperation;
            this.methodCacheKey = new AnnotatedElementKey(method, targetClass);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LockOperationLockKey that = (LockOperationLockKey) o;

            if (!lockOperation.equals(that.lockOperation)) return false;
            return methodCacheKey.equals(that.methodCacheKey);
        }

        @Override
        public int hashCode() {
            int result = lockOperation.hashCode();
            result = 31 * result + methodCacheKey.hashCode();
            return result;
        }
    }
}
