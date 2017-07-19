package com.niushencheng.interceptor;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 加锁操作信息
 * <p>
 * CreateTime: 2017-07-08 12:46:50
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
@Data
@EqualsAndHashCode
public class LockOperation implements BasicOperation {

    /** 名称 */
    private String name;

    /** key */
    private String key;

    /** 错误消息 */
    private String errorMsg;

    /** 获取锁的超时时间 */
    private Long timeout;

    /** 锁过期时间 */
    private Long expire;

    /** 标记key是否被解析过 */
    private boolean parsedKey = false;

    @Override
    public Set<String> getLockNames() {
        return null;
    }

    public LockOperation(String name, String key, String errorMsg, Long timeout, Long expire) {
        this.name = name;
        this.key = key;
        this.errorMsg = errorMsg;
        this.timeout = timeout;
        this.expire = expire;
    }

    public LockOperation() {
    }

}
