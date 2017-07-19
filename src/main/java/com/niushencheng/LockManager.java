package com.niushencheng;

import com.niushencheng.interceptor.LockAspectSupport;

/**
 * ClassName: LockManager <br/>
 * Function:  <br/>
 * CreateDate: 2017/7/6 下午2:07 <br/>
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public interface LockManager {

    /**
     * 获取锁实例
     *
     * @param context 上下文环境
     *
     * @return 锁对象
     */
    MTLock getLock(LockAspectSupport.LockOperationContext context);
}
