package com.niushencheng;

/**
 * ClassName: MTLock <br/>
 * Function: 锁接口 <br/>
 * CreateDate: 2017/7/6 下午2:04 <br/>
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public interface MTLock {

    /** 获取锁 */
    boolean lock();

    /** 释放锁 */
    void unlock();

    /** 获取锁的名称 */
    String getName();
}
