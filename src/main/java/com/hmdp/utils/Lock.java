package com.hmdp.utils;

public interface Lock {
    /**
     * 根据前缀获取锁
     * @param keyPrefix
     * @return
     */
    boolean tryLock();

    /**
     * 根据key释放锁
     * @param key
     */
    void delLock();
}
