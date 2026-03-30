package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hmdp.config.CacheClient;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Shop queryById(Long id) {


        return   cacheClient.getWithBreakDown(RedisConstants.CACHE_SHOP_KEY,id,Shop.class, this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
    }


    public Shop queryByIdWithMutex(Long id) {
        //根据id向缓存中查找
        String key= RedisConstants.CACHE_SHOP_KEY+id ;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopJson)){
            //缓存命中，返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson!=null){
            return null;
        }
        //未命中，获取互斥锁，同一时间只有一个线程执行数据库查询重写
        String lock=  RedisConstants.LOCK_SHOP_KEY +id;
        try {
            if(getLock(lock)){
                //获得锁
                //再次判断Redis是否有数据，因为Redis可能在此之前就被重写了
                shopJson = stringRedisTemplate.opsForValue().get(key);
                if(StrUtil.isNotBlank(shopJson)){
                    Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                    return shop;
                }else if(shopJson!=null){
                    return null;
                }else{
                    //如果Redis依然没有数据，查询数据库并重写
                    Shop shop = lambdaQuery().eq(Shop::getId, id).one();
                    //数据库未查到， 向Redis中写入null,防止缓存穿透
                    if(shop==null){
                        stringRedisTemplate.opsForValue().set(key,"");
                        stringRedisTemplate.expire(key,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                        return null;
                    }
                    //数据库查到，写入缓存
                    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
                    stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TTL+ RandomUtil.randomLong(0,5), TimeUnit.MINUTES);
                    return  shop;
                }
            }else{
                Thread.sleep(50);
                return  queryByIdWithMutex(id);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            delLock(lock);
        }
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("店铺id==null");
        }
        //更新数据库
        updateById(shop);
        //删除对应缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
    public boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 200, TimeUnit.MILLISECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void delLock(String key){
        stringRedisTemplate.delete( key);
    }


}
