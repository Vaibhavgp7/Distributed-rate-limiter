package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.List;

public class LeakingBucket implements RateLimitStrategy {
    private static final String LUA =
        "local key = KEYS[1] " +
        "local bucketSize = tonumber(ARGV[1]) " +
        "local leakRatePerMs = tonumber(ARGV[2]) / 1000 " +
        "local now = tonumber(ARGV[3]) " +
        "local state = redis.call('hmget', key, 'water_level', 'last_leak_time') " +
        "local waterLevel = tonumber(state[1]) " +
        "local lastLeakTime = tonumber(state[2]) " +
        "if not waterLevel then " +
        "    waterLevel = 0 " +
        "    lastLeakTime = now " +
        "else " +
        "    local elapsed = now - lastLeakTime " +
        "    local leakedAmount = elapsed * leakRatePerMs " +
        "    waterLevel = math.max(0, waterLevel - leakedAmount) " +
        "end " +
        "if waterLevel < bucketSize then " +
        "    waterLevel = waterLevel + 1 " +
        "    redis.call('hset', key, 'water_level', tostring(waterLevel), 'last_leak_time', tostring(now)) " +
        "    redis.call('expire', key, 60) " +
        "    return { '1', string.format('%.2f', waterLevel) } " +
        "else " +
        "    return { '0', string.format('%.2f', waterLevel) } " +
        "end";

    @Override
    public boolean isAllowed(Jedis jedis, String key, int bucketSize, int leakRatePerSec) {
        long nowMillis = System.currentTimeMillis();

        Object result = jedis.eval(LUA, Collections.singletonList(key),
                List.of(String.valueOf(bucketSize), String.valueOf(leakRatePerSec), String.valueOf(nowMillis)));

        @SuppressWarnings("unchecked")
        List<String> resList = (List<String>) result;
        boolean allowed = "1".equals(resList.get(0));
        String currentWater = resList.get(1);

        System.out.printf("   [LEAKING BUCKET] Key: %s | Queue Size: %s/%d | Allowed: %b%n",
                key, currentWater, bucketSize, allowed);

        return allowed;
    }
}
