package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.List;

public class SlidingWindowLog implements RateLimitStrategy {
    private static final String LUA =
        "local key = KEYS[1] " +
        "local limit = tonumber(ARGV[1]) " +
        "local window = tonumber(ARGV[2]) " +
        "local now = tonumber(ARGV[3]) " +
        "local clearBefore = now - window " +
        "redis.call('zremrangebyscore', key, 0, clearBefore) " + // clears all the scores for this key before the clearBefore
        "local currentRequests = redis.call('zcard', key) " +
        "if currentRequests < limit then " +
        "    redis.call('zadd', key, now, now) " +  // first now is for the score, 2nd now is the string automatically converted by lua
        "    redis.call('pexpire', key, window) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    @Override
    public boolean isAllowed(Jedis jedis, String key, int maxLimit, int windowSeconds) {
        long nowMillis = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        Object result = jedis.eval(LUA, Collections.singletonList(key),
                List.of(String.valueOf(maxLimit), String.valueOf(windowMillis), String.valueOf(nowMillis)));

        boolean allowed = "1".equals(result.toString());
        long logSize = jedis.zcard(key);

        System.out.printf("   [SLIDING LOG] Key: %s | Log Size: %d/%d | Allowed: %b%n",
                key, logSize, maxLimit, allowed);

        return allowed;
    }
}