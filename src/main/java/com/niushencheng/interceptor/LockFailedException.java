package com.niushencheng.interceptor;

/**
 * 获取分布式锁失败后的异常
 * <p>
 * CreateTime: 2017-07-08 12:41:13
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public class LockFailedException extends RuntimeException {
    LockFailedException(String message) {
        super(message);
    }
}
