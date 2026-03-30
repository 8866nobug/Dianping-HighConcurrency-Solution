package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    public static ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Test
    public void testNextId() throws InterruptedException {
        int threadNum = 300; // 模拟300个并发线程
        CountDownLatch latch = new CountDownLatch(threadNum);
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        Runnable runnable = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    ids.add(id);
                    System.out.println("id = " + id);
                }
            } finally {
                latch.countDown(); // 每个线程跑完，计数减一
            }
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < threadNum; i++) {
            executorService.execute(runnable);
        }

        latch.await(); // 主线程在此阻塞，直到计数归零
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - start) + "ms"+ids.size());
    }

}
