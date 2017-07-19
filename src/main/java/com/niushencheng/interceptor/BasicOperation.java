package com.niushencheng.interceptor;

import java.util.Set;

/**
 * 基础操作
 * <p>
 * CreateTime: 2017-07-08 12:46:21
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
public interface BasicOperation {

    Set<String> getLockNames();
}
