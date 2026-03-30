package com.hmdp.config;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Component
public class CacheClient {

    @Autowired
    public  StringRedisTemplate stringRedisTemplate;

    //将对象序列化为Json字符串，存入Redis,并设置过期时间
    public void set(String key, Object value, Long expireTime, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, unit);
    }
    //将对象封装成带逻辑过期时间的RedisData，存入Redis
    public void setWithLogicExpireTime(String key, Object value, Long logicExpireTime, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(logicExpireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //将普通对象取出（不带有逻辑过期时间的），同时防缓存穿透
    public <R,ID> R getWithPassThrough(String keyPrefix, ID id, Class<R> clazz, Function<ID,R> dbFallBack, Long expireTime, TimeUnit unit){
        String key=keyPrefix+id ;
        //1.向Redis中查数据
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
            //1.1如果命中，返回
            return JSONUtil.toBean(json,clazz);
        }
        //2.如果没命中，判断是否为""
        if(json!=null){
            //2.1如果为""返回null
            return null;
        }
        //3.如果不为"",向数据库中查询
        R value = dbFallBack.apply(id);
        if(value==null){
            //3.1如果数据库中没有，向Redis中写入""，返回null
            this.set(key,"",expireTime,unit);
            return null;
        }
        //4.如果数据库中有，向Redis中写入，返回
        this.set(key,JSONUtil.toJsonStr(value),expireTime,unit);
        return value;

    }
    private static final ExecutorService CACHE_REBUILD =  Executors.newFixedThreadPool(10);

    //将带有逻辑过期字段的数据从Redis中取出，防止缓存击穿
    public <R,ID> R getWithBreakDown(String keyPrefix, ID id, Class<R> clazz,Function<ID,R> dbFallBack, Long expireTime, TimeUnit unit){
        //1.根据id向缓存中查找
        String key=  keyPrefix+id ;
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            //1.1缓存未命中，说明数据不存在
            return null;
        }
        //2.缓存命中，判断是否过期
        RedisData redisData =  JSONUtil.toBean(json,RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        R value=JSONUtil.toBean(data,clazz);
        LocalDateTime oldExpireTime = redisData.getExpireTime();

        if(oldExpireTime.isAfter(LocalDateTime.now())){
            //2.1未过期，返回
            return  value;
        }

        //3.过期,获取互斥锁
        try {
            if(getLock(RedisConstants.LOCK_SHOP_KEY+ id)){
                //4.获得，二次判断Redis数据是否已更新
                json = stringRedisTemplate.opsForValue().get(key);
                redisData =  JSONUtil.toBean( json, RedisData.class);
                data = (JSONObject)redisData.getData();
                value=JSONUtil.toBean(data,clazz);
                if(redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                    //4.1更新，返回新结果
                    delLock(RedisConstants.LOCK_SHOP_KEY + id);
                    return value;
                }
                //5.未更新，另起一个线程向更新Redis数据
                CACHE_REBUILD.execute(() -> {
                    R newValue = dbFallBack.apply(id);
                    this.setWithLogicExpireTime(key,newValue,expireTime,unit);
                    delLock(RedisConstants.LOCK_SHOP_KEY+ id);
                });
            }
            return  value;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 200, TimeUnit.MILLISECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void delLock(String key){
        stringRedisTemplate.delete( key);
    }
}
