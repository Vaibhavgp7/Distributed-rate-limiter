package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;
import java.util.List;

public class SlidingWindowCounter implements RateLimitStrategy {
    private static final String LUA =
        "local currentKey = KEYS[1] " +
        "local previousKey = KEYS[2] " +
        "local limit = tonumber(ARGV[1]) " +
        "local weight = tonumber(ARGV[2]) " +
        "local currentCount = redis.call('get', currentKey) or 0 " +
        "local previousCount = redis.call('get', previousKey) or 0 " +
        "if (tonumber(previousCount) * weight + tonumber(currentCount)) >= limit then " +
        "    return 0 " +
        "else " +
        "    redis.call('incr', currentKey) " +
        "    redis.call('expire', currentKey, ARGV[3]) " + // ttl of 2*window size needed so at transition time from curr to prev key the prev window key doesn't expire
        "    return 1 " +
        "end";

    @Override
    public boolean isAllowed(Jedis jedis, String key, int maxLimit, int windowSeconds) {
        long nowMillis = System.currentTimeMillis();
        long nowSeconds = nowMillis / 1000;
        long windowMillis = windowSeconds * 1000L;

        long currentWindowId = nowSeconds / windowSeconds;
        String currentKey = key + ":" + currentWindowId;
        String previousKey = key + ":" + (currentWindowId - 1);

        long timeIntoCurrentWindowMs = nowMillis % windowMillis;
        double weight = (double) (windowMillis - timeIntoCurrentWindowMs) / windowMillis;

        String currentCountStr = jedis.get(currentKey);
        String previousCountStr = jedis.get(previousKey);
        long currentCount = currentCountStr != null ? Long.parseLong(currentCountStr) : 0;
        long previousCount = previousCountStr != null ? Long.parseLong(previousCountStr) : 0;
        double calculatedLoad = (previousCount * weight) + currentCount;

        Object result = jedis.eval(LUA, List.of(currentKey, previousKey),
                List.of(String.valueOf(maxLimit), String.valueOf(weight), String.valueOf(windowSeconds * 2)));

        boolean allowed = "1".equals(result.toString());

        System.out.printf("   [SLIDING COUNTER MS DEEP DIVE] Key: %s | Win ID: %d | Progress: %4d/%4d ms | Wt: %.3f | Load: %5.2f/%d | Allowed: %s%n",
                key, currentWindowId, timeIntoCurrentWindowMs, windowMillis, weight, calculatedLoad, maxLimit, allowed ? "✅ ALLOWED" : "❌ BLOCKED");

        return allowed;
    }
}