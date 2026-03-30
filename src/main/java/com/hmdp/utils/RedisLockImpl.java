package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements Lock{
    private static final String LOCK_PREFIX = "lock:";
    private final String lockValue= UUID.randomUUID().toString()+Thread.currentThread().getId();
    private final String lockKey;
    private final StringRedisTemplate stringRedisTemplate;
    private final long expireSeconds;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    public RedisLockImpl(String keyPrefix, StringRedisTemplate stringRedisTemplate,long expireSeconds) {
        this.lockKey = LOCK_PREFIX + keyPrefix;
        this.stringRedisTemplate = stringRedisTemplate;
        this.expireSeconds = expireSeconds;
    }
    @Override
    public boolean tryLock() {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void delLock() {

         stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);

    }
}
