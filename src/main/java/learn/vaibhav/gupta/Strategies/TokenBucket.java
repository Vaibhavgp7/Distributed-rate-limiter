package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.List;

public class TokenBucket implements RateLimitStrategy {
    private static final String LUA =
        "local key = KEYS[1] " +
        "local maxCapacity = tonumber(ARGV[1]) " +
        "local refillRatePerMs = tonumber(ARGV[2]) / 1000 " +
        "local now = tonumber(ARGV[3]) " +
        "local state = redis.call('hmget', key, 'tokens', 'last_refill_time') " +
        "local tokens = tonumber(state[1]) " +
        "local lastRefillTime = tonumber(state[2]) " +
        "if not tokens then " +
        "    tokens = maxCapacity " +
        "    lastRefillTime = now " +
        "else " +
        "    local elapsed = now - lastRefillTime " +
        "    tokens = math.min(maxCapacity, tokens + (elapsed * refillRatePerMs)) " +
        "    lastRefillTime = now " +
        "end " +
        "if tokens >= 1 then " +
        "    tokens = tokens - 1 " +
        "    redis.call('hset', key, 'tokens', tostring(tokens), 'last_refill_time', tostring(lastRefillTime)) " +
        "    redis.call('expire', key, 60) " +
        "    return { '1', string.format('%.2f', tokens) } " +
        "else " +
        "    redis.call('hset', key, 'tokens', tostring(tokens), 'last_refill_time', tostring(lastRefillTime)) " +
        "    return { '0', string.format('%.2f', tokens) } " +
        "end";

    @Override
    public boolean isAllowed(Jedis jedis, String key, int bucketCapacity, int refillRatePerSec) {
        long nowMillis = System.currentTimeMillis();

        Object result = jedis.eval(LUA, Collections.singletonList(key),
                List.of(String.valueOf(bucketCapacity), String.valueOf(refillRatePerSec), String.valueOf(nowMillis)));

        @SuppressWarnings("unchecked")
        List<String> resList = (List<String>) result;
        boolean allowed = "1".equals(resList.get(0));
        String tokensLeft = resList.get(1);

        System.out.printf("   [TOKEN BUCKET] Key: %s | Remaining Tokens: %s/%d | Allowed: %b%n",
                key, tokensLeft, bucketCapacity, allowed);

        return allowed;
    }
}
