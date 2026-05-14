local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 1. 移除窗口外的请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)

-- 2. 获取当前窗口内的请求数
local count = redis.call('ZCARD', key)

-- 3. 判断是否超过限制
if count < limit then
    -- 4. 添加新请求（使用时间戳+随机数保证唯一性）
    redis.call('ZADD', key, now, now .. ':' .. math.random(100000))
    redis.call('EXPIRE', key, window)
    return 1
end
return 0
