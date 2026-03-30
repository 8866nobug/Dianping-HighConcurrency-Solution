package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreashLoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public  RefreashLoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从Header中取token,从Redis中取UserDTO,判断是否为空
        String token = request.getHeader("authorization");
        String key= RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if(token == null||userMap.isEmpty()){

            return  true;
        }
        //将userMap转为Use人DTO对象并存入ThreadLocalList
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), true);
        UserHolder.saveUser(userDTO);
        //刷新token时间
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //放行
        return true;
    }
}
