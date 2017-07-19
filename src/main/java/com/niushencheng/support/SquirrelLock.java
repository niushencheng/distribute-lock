package com.niushencheng.support;

import com.niushencheng.MTLock;
import com.niushencheng.interceptor.LockArgIllegalException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用美团的松鼠作为存储锁信息
 * <p>
 * CreateTime: 2017-07-07 09:05:39
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
@Data
@ToString
@EqualsAndHashCode
public class SquirrelLock implements MTLock {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final String name;

    private final String key;

    private final long timeout; // ms

    private final long expire;  // ms

    private final String errorMsg;

    private final String lockKey;

    private final SquirrelClient squirrelClient;

    private volatile boolean cancel = false;

    private volatile boolean locked = false;
    // 是否进入加锁主逻辑
    private volatile boolean entered = false;

    private long MAX_TIMEOUT = 600 * 1000;  // 10min

    /** take lock，用于tryLock锁定时 */
    private final Lock takeLock = new ReentrantLock();

    /** condition，用于休眠唤醒 */
    private final Condition condition = takeLock.newCondition();

    /** 方法执行主锁 */
    private final Lock mainLock = new ReentrantLock();

    SquirrelLock(String name, String key, long timeout, long expire, String errorMsg, SquirrelClient squirrelClient) {
        this.name = name;
        this.key = key;
        this.timeout = timeout;
        this.expire = expire;
        this.errorMsg = errorMsg;
        this.lockKey = name + key;    // lock的key为拼接的
        this.squirrelClient = squirrelClient;
        this.checkArgs();
    }

    /**
     * 检验参数
     */
    private void checkArgs() {
        if (StringUtils.isBlank(name)) {
            throw new LockArgIllegalException("DistributeLock的name不能为空！");
        }
        if (StringUtils.isBlank(errorMsg)) {
            throw new LockArgIllegalException("DistributeLock的errorMsg不能为空！");
        }
        if (timeout <= 0) {
            throw new LockArgIllegalException("DistributeLock的timeout必需为正数！");
        }
        if (expire < 0) {
            throw new LockArgIllegalException("DistributeLock的expire不能为负数！");
        }
    }

    @Override
    public boolean lock() {
        try {
            LOGGER.info("尝试获取mainLock, thread -->>{}, lockKey -->> {}, timestamp -->> {}",
                        this.currentThread(),
                        this.lockKey,
                        this.timeStamp());
            // 注意！！！！！！
            // 本处的mainLock是为了保证一台服务器中同时只有一个需要加锁的方法执行所用的
            // 可以在LockAspectSupport加锁逻辑中使用synchronized处理，但是会出现timeout不准的问题
            // 如果使用ReentrantLock只能在本方法成功加锁后才可以执行unlock操作
            this.entered = mainLock.tryLock(this.timeout, TimeUnit.MILLISECONDS);
            if (!this.entered) {
                // 在指定的timeout中都没有能成功获取锁，认为失败
                LOGGER.warn("在指定的timeout:{}, 中没有成功获取mainLock, thread -->>{}, lockKey -->> {}, timestamp -->> {}",
                            this.timeout,
                            this.lockKey,
                            this.currentThread(),
                            this.timeStamp());
                return false;
            }
            LOGGER.info("成功获取到了mainLock, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                        this.currentThread(),
                        this.lockKey,
                        this.timeStamp());

            this.locked = false;
            this.tryLock();
            if (this.locked) {
                LOGGER.info("分布式锁成功加锁, thread -->>{}, lockKey -->> {}, timestamp-->>{}",
                            this.currentThread(),
                            this.lockKey,
                            this.timeStamp());
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            LOGGER.warn("当前线程被中断了thread -->>{}, lockKey -->> {}, timestamp -->> {}",
                        this.currentThread(),
                        this.lockKey,
                        this.timeStamp());
            LOGGER.warn("被中断的消息", e);
            return false;
        }
    }

    private String currentThread() {
        return Thread.currentThread().getName();
    }

    private long timeStamp() {
        return System.currentTimeMillis();
    }

    @Override
    public void unlock() {
        try {
            if (this.locked) {
                boolean flag = squirrelClient.del(CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY, lockKey);
                if (flag) {
                    LOGGER.info("成功从松鼠中移除一条记录, thread -->> {}, lockKey -->> {}, category -->> {}, timestamp -->> {}",
                                this.currentThread(),
                                this.lockKey,
                                CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                                this.timeStamp());
                } else {
                    LOGGER.error("释放分布式锁失败了!, thread -->> {}, lockKey -->> {}, category -->> {}, timestamp -->> {}",
                                 this.currentThread(),
                                 this.lockKey,
                                 CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                                 this.timeStamp());
                }
                this.locked = false;
                this.cancel = false;
                return;
            }
            LOGGER.info("没有获取到分布式锁，无需移除, thread -->> {}, lockKey -->> {}, category -->> {}, timestamp -->> {}",
                        this.currentThread(),
                        this.lockKey,
                        CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                        this.timeStamp());

        } finally {
            if (this.entered) {
                mainLock.unlock();
                LOGGER.info("释放mainLock, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                            this.currentThread(),
                            this.lockKey,
                            this.timeStamp());
            }
        }
    }

    /**
     * 执行锁操作的具体逻辑
     */
    private void tryLock() {
        takeLock.lock();
        try {
            long startTime = System.currentTimeMillis();
            int tryTimes = 0;
            while (!this.locked) {
                LOGGER.info("正在进行第{}次尝试, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                            ++tryTimes,
                            this.currentThread(),
                            this.lockKey,
                            this.timeStamp());
                if (cancel) {   // 通过unlock取消的锁
                    LOGGER.warn("锁被取消了, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                                this.currentThread(),
                                this.lockKey,
                                this.timeStamp());
                    return;
                }

                // 检查是否获取锁超时
                long timeStamp = System.currentTimeMillis();
                if (timeout <= 0) {
                    return;
                }
                if (timeStamp > 0 && timeStamp - startTime >= timeout) {
                    LOGGER.warn(
                            "当前时间大于获取锁超时时间, thread -->> {}, lockKey -->> {}, 差值 -->> {}, timeout -->> {}, startTime -->> {}, timestamp -->> {}",
                            this.currentThread(),
                            this.lockKey,
                            timeStamp - startTime,
                            this.timeout,
                            startTime,
                            this.timeStamp());
                    return;
                }
                if (timeStamp - startTime > MAX_TIMEOUT) {
                    break;
                }

                // 检查旧的值是否已经过期
                long expireTime = expire < 1
                        ? Long.MAX_VALUE
                        : timeStamp + this.expire;
                long oldValue = this.retrieveOldValue();

                if (oldValue > 0 && timeStamp > oldValue) {
                    LOGGER.warn(
                            "缓存中的锁已经失效了, thread -->> {}, lockKey -->> {}, 差值 -->> {}, oldValue -->> {}, timestamp -->> {}",
                            this.currentThread(),
                            this.lockKey,
                            timeStamp - oldValue,
                            oldValue,
                            this.timeStamp());
                    if (squirrelClient.del(CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY, lockKey)) {
                        LOGGER.info("成功从松鼠中移除一条记录, thread -->> {}, lockKey -->> {}, category -->> {}, timestamp -->> {}",
                                    this.currentThread(),
                                    this.lockKey,
                                    CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                                    this.timeStamp());
                    } else {
                        Logs.ERROR.error(
                                "松鼠中没有原先key对应的值, thread -->> {}, lockKey -->> {}, category -->> {}, timestamp -->> {}",
                                this.currentThread(),
                                this.lockKey,
                                CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                                this.timeStamp());
                    }

                }

                // setnx 设置成功后会返回true
                this.locked = squirrelClient.string.add(CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY,
                                                        lockKey,
                                                        expireTime,
                                                        (int) (expire / 1000));

                if (this.locked) {
                    LOGGER.info("成功在松鼠中添加了一条记录, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                                this.currentThread(),
                                this.lockKey,
                                this.timeStamp());
                    return;
                }

                // 开始轮询
                try {
                    if (timeout >= 4 * 1000) {
                        // 大于4秒小于120秒的，每次轮询时间在2~10秒之间
                        long waitTime = ((Number) Math.floor(Math.sqrt(timeout / 1000))).longValue() * 1000;
                        condition.await(waitTime, TimeUnit.MILLISECONDS);
                    } else {
                        // 小于4秒的，每次轮询时间在1秒左右
                        long waitTime = timeout / 2000;
                        condition.await(waitTime != 0
                                                ? waitTime
                                                : timeout, TimeUnit.MILLISECONDS);
                    }

                } catch (InterruptedException e) {
                    LOGGER.warn("lock 被中断了, thread -->> {}, lockKey -->> {}, timestamp -->> {}",
                                this.currentThread(),
                                this.lockKey,
                                this.timeStamp());
                }
            }
        } finally {
            condition.signalAll();
            takeLock.unlock();
        }

    }

    private long retrieveOldValue() {
        Object value = squirrelClient.get(CategoryConstants.PASS_DISTRIBUTE_LOCK_CATEGORY, lockKey);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.valueOf(value.toString());
        }
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }
}
