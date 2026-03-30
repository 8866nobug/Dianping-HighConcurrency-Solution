package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        //查缓存
        String key= RedisConstants.CACHE_SHOPTYPE_KEY;
        List<String> shopTypes = stringRedisTemplate.opsForList().range(key, 0, -1);
        //命中，返回
        if(shopTypes!=null && !shopTypes.isEmpty()){
            List<ShopType> shopTypeList=new ArrayList<>();
            shopTypes.forEach(shopType->{
                ShopType type = JSONUtil.toBean(shopType, ShopType.class);
                shopTypeList.add(type);
            });
            return shopTypeList;
        }
        //未命中，查数据库
        List<ShopType> shopTypeList =  lambdaQuery().orderByAsc(ShopType::getId).list();
        //未查到，返回null
        if(shopTypeList==null || shopTypeList.isEmpty()){
            return null;
        }
        //查到，写缓存
        shopTypeList.forEach(shopType->{
            String jsonStr = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().rightPush(key, jsonStr);
        });
        //返回

        return shopTypeList;
    }
}
