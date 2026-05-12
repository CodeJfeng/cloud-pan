local key = KEYS[1]
local value = ARGV[1]
local ttl = tonumber(ARGV[2])
local exists = redis.call('EXISTS', key)
if exists == 1 then
    return redis.call('GET', key)
else
    redis.call('SETEX', key, ttl, value)
    return 'OK'
end
