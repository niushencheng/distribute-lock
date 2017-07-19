package com.niushencheng.interceptor;

/**
 * Lock参数检验
 * <p>
 * CreateTime: 2017-07-08 09:31:08
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public class LockArgIllegalException extends RuntimeException {
    public LockArgIllegalException(String msg) {
        super(msg);
    }
}
