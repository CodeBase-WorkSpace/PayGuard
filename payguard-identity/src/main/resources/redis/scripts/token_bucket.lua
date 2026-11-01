-- PayGuard OTP: atomic multi-policy token bucket for a single Redis primary.
-- KEYS[i] is the bucket key for policy i.
-- ARGV consists of four values per key:
--   ARGV[4*i - 3] = policy name (non-empty string)
--   ARGV[4*i - 2] = capacity (positive integer)
--   ARGV[4*i - 1] = refillTokens (positive integer)
--   ARGV[4*i]     = refillPeriodMillis (positive integer)
-- Each request costs exactly one token from each applicable bucket.
-- Result: {1, 0, ""} on success; {0, retryAfterMillis, policyName} on rejection.
-- No keys are updated on rejection. All updates are atomic on one Redis primary.

local count = #KEYS
if count == 0 or #ARGV ~= count * 4 then
    return redis.error_reply('Invalid token bucket arguments')
end

local now = redis.call('TIME')
local now_ms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local states = {}
local rejected_policy = ''
local maximum_retry_ms = 0

for i = 1, count do
    local base = (i - 1) * 4
    local name = ARGV[base + 1]
    local capacity = tonumber(ARGV[base + 2])
    local refill_tokens = tonumber(ARGV[base + 3])
    local period_ms = tonumber(ARGV[base + 4])

    if not name or name == '' or not capacity or not refill_tokens or not period_ms
        or capacity < 1 or refill_tokens < 1 or period_ms < 1
        or capacity ~= math.floor(capacity)
        or refill_tokens ~= math.floor(refill_tokens)
        or period_ms ~= math.floor(period_ms) then
        return redis.error_reply('Invalid token bucket policy')
    end

    local values = redis.call('HMGET', KEYS[i], 'tokens', 'updated_at_ms')
    local tokens = capacity
    if values[1] ~= false and values[2] ~= false then
        local stored_tokens = tonumber(values[1])
        local updated_at_ms = tonumber(values[2])
        if not stored_tokens or not updated_at_ms then
            return redis.error_reply('Corrupt token bucket state')
        end
        local elapsed_ms = math.max(0, now_ms - updated_at_ms)
        tokens = math.min(capacity, stored_tokens + elapsed_ms * refill_tokens / period_ms)
    end

    local ttl_ms = math.max(1, math.ceil(capacity * period_ms / refill_tokens))
    if ttl_ms > 9007199254740991 then
        return redis.error_reply('Token bucket TTL out of range')
    end

    states[i] = {tokens = tokens, ttl_ms = ttl_ms}

    if tokens < 1 then
        local retry_ms = math.max(1, math.ceil((1 - tokens) * period_ms / refill_tokens))
        if retry_ms > maximum_retry_ms then
            maximum_retry_ms = retry_ms
            rejected_policy = name
        end
    end
end

if maximum_retry_ms > 0 then
    return {0, maximum_retry_ms, rejected_policy}
end

for i = 1, count do
    redis.call('HSET', KEYS[i],
        'tokens', tostring(states[i].tokens - 1),
        'updated_at_ms', tostring(now_ms))
    redis.call('PEXPIRE', KEYS[i], states[i].ttl_ms)
end

return {1, 0, ''}
