package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public static final long BEGIN_TIMESTAMP=1773513612;

    public static final int SUFFIX_BITS=32;

    public long nextId(String keyPrefix){

        long GAP_STAMP =  LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)-BEGIN_TIMESTAMP;

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long suffix = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix +":" +date);

        return GAP_STAMP<<SUFFIX_BITS | suffix;


    }
}
