--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]

--2.数据key
--2.1订单库存key
local seckillStockKey="seckill:stock:"..voucherId
--2.2下单用户集合Key
local orderedUserKey="seckill:order:"..voucherId

--3.脚本业务
--3.1.去库存数据判断，如果不足返回1,如果不存在请求的订单，返回3
local stock = redis.call('get',seckillStockKey)
if(not stock) then
    return 3
end
if(tonumber(stock)<=0) then
    return 1
end
--3.2判断用户是否已经下单，如果已下单返回2
if(redis.call('sismember',orderedUserKey,userId)==1)then
    return 2
end
--3.3允许下单，减库存，加入已下单集合
redis.call('incrby',seckillStockKey,-1)
redis.call('sadd',orderedUserKey,userId)
return 0