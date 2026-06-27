package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.List;

public class FixedWindowCounter implements RateLimitStrategy {
    private static final String LUA =
            "local key = KEYS[1] " +
                    "local limit = tonumber(ARGV[1]) " +
                    "local window = tonumber(ARGV[2]) " +
                    "local current = redis.call('get', key) " +
                    "if current and tonumber(current) >= limit then " +
                    "    return 0 " +
                    "else " +
                    "    current = redis.call('incr', key) " +
                    "    if tonumber(current) == 1 then " +
                    "        redis.call('expire', key, window) " +
                    "    end " +
                    "    return 1 " +
                    "end";

    @Override
    public boolean isAllowed(Jedis jedis, String key, int maxLimit, int windowSeconds) {
        long currentWindowId = (System.currentTimeMillis() / 1000) / windowSeconds;
        String redisKey = key + ":" + currentWindowId;

        Object result = jedis.eval(LUA, Collections.singletonList(redisKey),
                List.of(String.valueOf(maxLimit), String.valueOf(windowSeconds)));

        boolean allowed = "1".equals(result.toString());
        String currentCount = jedis.get(redisKey);

        System.out.printf("   [FIXED WINDOW] Key: %s | Count: %s/%d | Allowed: %b%n",
                redisKey, (currentCount == null ? "0" : currentCount), maxLimit, allowed);

        return allowed;
    }
}
