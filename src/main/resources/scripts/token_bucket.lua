-- Token Bucket Rate Limiter (atomic via Redis EVAL)
-- KEYS[1] = bucket key (e.g., "rl:tb:ip:1.2.3.4:/api/foo")
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill_tokens (tokens added per period)
-- ARGV[3] = refill_period_seconds
-- ARGV[4] = now_millis (server-side clock from caller)
-- ARGV[5] = requested (tokens to consume; default 1)
--
-- Returns: { allowed (1|0), remaining, reset_seconds, retry_after_seconds }

local key                   = KEYS[1]
local capacity              = tonumber(ARGV[1])
local refill_tokens         = tonumber(ARGV[2])
local refill_period_seconds = tonumber(ARGV[3])
local now_ms                = tonumber(ARGV[4])
local requested             = tonumber(ARGV[5]) or 1

local refill_rate_per_ms = refill_tokens / (refill_period_seconds * 1000.0)

local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens = tonumber(data[1])
local last_refill_ms = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_refill_ms = now_ms
end

local elapsed_ms = math.max(0, now_ms - last_refill_ms)
local refilled = elapsed_ms * refill_rate_per_ms
tokens = math.min(capacity, tokens + refilled)
last_refill_ms = now_ms

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

-- Calculate time until next token (for Retry-After) and reset (for full refill)
local tokens_needed_for_request = math.max(0, requested - tokens)
local retry_after_ms = 0
if allowed == 0 and refill_rate_per_ms > 0 then
    retry_after_ms = math.ceil(tokens_needed_for_request / refill_rate_per_ms)
end

local tokens_needed_for_full = capacity - tokens
local reset_ms = 0
if refill_rate_per_ms > 0 then
    reset_ms = math.ceil(tokens_needed_for_full / refill_rate_per_ms)
end

-- Persist updated state; TTL prevents stale buckets from lingering
redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', last_refill_ms)
local ttl_seconds = math.max(refill_period_seconds * 2, math.ceil(reset_ms / 1000) + 1)
redis.call('EXPIRE', key, ttl_seconds)

local remaining = math.floor(tokens)
local reset_seconds = math.ceil(reset_ms / 1000)
local retry_after_seconds = math.ceil(retry_after_ms / 1000)

return { allowed, remaining, reset_seconds, retry_after_seconds }
