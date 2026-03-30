package com.hmdp.service.impl;

import com.hmdp.utils.RedisIdWorker;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Autowired
    private RedisIdWorker redisIdWorker;

    private static final String ORDER_PREFIX ="order";
    private static final String LOCK_PREFIX ="lock:seckill:";
    @Autowired
    private VoucherOrderServiceImpl voucherOrderServiceImpl;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static BlockingQueue<VoucherOrder> voucherOrderQueue=new ArrayBlockingQueue<>(1024*1024);
    private static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

    }

    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new SeckillTask());
    }

    class SeckillTask implements Runnable{
        @Override
        public void run() {
            //从阻塞队列中取出订单，将订单保存，同时修改库存
            try {
                VoucherOrder voucherOrder = voucherOrderQueue.take();
                voucherOrderServiceImpl.save(voucherOrder);
                lambdaUpdate().eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId()).gt(SeckillVoucher::getStock,0).setSql("stock = stock - 1 ").update();
            } catch (Exception e) {
                 log.info("子线程，写入订单异常！");
            }

        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本校验秒杀资格
        long theResult = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());

        int result= (int) theResult;
        if(result !=0){
            //如果为0，判断失败原因，返回相应信息
            if(result==3){
                System.out.println("警告，请求不存在的数据");
                return Result.fail("警告，请求不存在的数据");}
            return Result.fail(result==1? "库存不足":"一人一单");
        }
        //2如果不为0则生成订单id，将创建订单的任务加入阻塞队列
        //2.1创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        long orderId = redisIdWorker.nextId(ORDER_PREFIX);
        voucherOrder.setId(orderId);
        voucherOrderQueue.add(voucherOrder);
        //3返回订单id
        return Result.ok(orderId);



    }
//未优化的秒杀逻辑，校验秒杀资格和秒杀成功后的写入数据操作为一个线程，同时校验秒杀资格从数据库中查询数据，效率很低
   /* @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券信息
        SeckillVoucher seckillVoucher = lambdaQuery().eq(SeckillVoucher::getVoucherId, voucherId).one();
        //判断是否开始
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始！");
        }
        //判断是否过期
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束！");
        }

        if(seckillVoucher.getStock()<1){
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
      //RedisLockImpl redisLock = new RedisLockImpl("Seckill:" + userId.toString(), stringRedisTemplate,10);
        RLock lock =  redissonClient.getLock(LOCK_PREFIX + userId);
        boolean success = lock.tryLock();
        if(success){
            try {
                //获取代理对象，开启事务
                ISeckillVoucherService proxy = (ISeckillVoucherService)AopContext.currentProxy();

                return proxy.createVoucherOrder(voucherId);
            } catch (IllegalStateException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
        return Result.fail("用户超卖！");



    }*/

    @Transactional
    @Override
    public  Result  createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Integer count = voucherOrderServiceImpl.lambdaQuery().eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId).count();
        if(count>0){
            return Result.fail("用户超买");
        }
        //扣减库存
        boolean success = lambdaUpdate().eq(SeckillVoucher::getVoucherId, voucherId).gt(SeckillVoucher::getStock,0).setSql("stock = stock - 1 ").update();
        if(!success){
            return Result.fail("库存不足！");
        }
        //创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        long orderId = redisIdWorker.nextId(ORDER_PREFIX);
        voucherOrder.setId(orderId);
        voucherOrderServiceImpl.save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
