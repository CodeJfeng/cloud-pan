local key = KEYS[1]
local exists = redis.call('EXISTS', key)
if exists == 1 then
    return redis.call('GET', key)
else
    return nil
end
