-- Sliding Window Log Rate Limiter (atomic via Redis EVAL)
-- Uses a sorted set of request timestamps; trims entries older than window.
--
-- KEYS[1] = window key (e.g., "rl:sw:user:42:/api/foo")
-- ARGV[1] = max_requests
-- ARGV[2] = window_size_seconds
-- ARGV[3] = now_millis
-- ARGV[4] = request_id (unique value for ZADD member; caller may pass UUID or now+rand)
--
-- Returns: { allowed (1|0), remaining, reset_seconds, retry_after_seconds }

local key                  = KEYS[1]
local max_requests         = tonumber(ARGV[1])
local window_size_seconds  = tonumber(ARGV[2])
local now_ms               = tonumber(ARGV[3])
local request_id           = ARGV[4]

local window_ms = window_size_seconds * 1000
local cutoff_ms = now_ms - window_ms

-- Drop entries older than the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff_ms)
local current = tonumber(redis.call('ZCARD', key))

local allowed = 0
if current < max_requests then
    redis.call('ZADD', key, now_ms, request_id)
    current = current + 1
    allowed = 1
end

redis.call('EXPIRE', key, window_size_seconds + 1)

local remaining = math.max(0, max_requests - current)

-- For sliding window the "reset" is when the oldest in-window request ages out.
local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local reset_seconds
local retry_after_seconds = 0
if #oldest >= 2 then
    local oldest_ms = tonumber(oldest[2])
    local expires_at_ms = oldest_ms + window_ms
    reset_seconds = math.max(0, math.ceil((expires_at_ms - now_ms) / 1000))
    if allowed == 0 then
        retry_after_seconds = math.max(1, reset_seconds)
    end
else
    reset_seconds = 0
end

return { allowed, remaining, reset_seconds, retry_after_seconds }
