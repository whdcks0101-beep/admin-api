local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])

local count = redis.call('INCR', key)

if count == 1 then
    redis.call('EXPIRE', key, windowSeconds)
end

if count > limit then
    local remainingMillis = redis.call('PTTL', key)
    local retryAfterSeconds = math.max(
        1,
        math.ceil(remainingMillis / 1000)
    )

    return {0, 0, retryAfterSeconds}
end

return {1, limit - count, 0}