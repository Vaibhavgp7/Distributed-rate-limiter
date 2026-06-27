package learn.vaibhav.gupta.Strategies;

import redis.clients.jedis.Jedis;

public interface RateLimitStrategy {
    boolean isAllowed(Jedis jedis, String key, int param1, int param2);
}
