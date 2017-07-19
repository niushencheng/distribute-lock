package com.niushencheng.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁
 *
 * 缓存中的真实的key为:
 *      KeyPrefix + simpleClassName + {@link #name} + {@link #key}
 *
 * {@link #key}推荐使用单一值
 *      e.g: #list[0] #userId
 *
 * 如果需要修改squirrel缓存中key的前缀，请前往
 * {@link com.meituan.csc.paas.lock.support.SquirrelLockManager#lockPrefix}
 *
 * CreateTime: 2017-07-08 12:57:20
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributeLock {

    /**
     * 锁名称, 默认为被加锁的方法名
     * 缓存中真实的key为：
     * lockPrefix + simpleClassName + name(默认为methodName) + key(解析后的key)
     */
    String name() default "";

    /** 锁的key, 支持spring el 表达式 */
    String key() default "";

    /** 获取锁失败后的消息 */
    String errorMsg() default "获取分布式锁失败";

    /** 获取锁超时的消息, 默认20秒超时 */
    long timeout() default 20 * 1000;   // unit -->> ms

    /** 锁超时时间，默认120秒超时 */
    long expire() default 120 * 1000;   // unit -->> ms
}
