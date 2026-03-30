--比较线程标识与锁中的标识是否一致，如果一致则释放锁，否则不释放
if(redis.call('get',KEYS[1])==ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0