package learn.vaibhav.gupta;

import learn.vaibhav.gupta.Strategies.RateLimitStrategy;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class AppNode {
    private final String nodeName;
    private final JedisPool jedisPool;
    private final RateLimitStrategy strategy;

    public AppNode(String nodeName, JedisPool jedisPool, RateLimitStrategy strategy) {
        this.nodeName = nodeName;
        this.jedisPool = jedisPool;
        this.strategy = strategy;
    }

    public void processRequest(String clientIp, String endpoint, int param1, int param2) {
        String rateLimitKey = "ratelimit:" + clientIp + ":" + endpoint;
        String threadName = Thread.currentThread().getName();

        try (Jedis jedis = jedisPool.getResource()) {
            boolean allowed = strategy.isAllowed(jedis, rateLimitKey, param1, param2);

            if (allowed) {
                System.out.printf("[%s] Node: %s -> ✅ 200 OK %s%n",
                        threadName, nodeName, strategy.getClass().getSimpleName());
            } else {
                System.out.printf("[%s] Node: %s -> ❌ 429 BLOCKED %s%n",
                        threadName, nodeName, strategy.getClass().getSimpleName());
            }
        } catch (Exception e) {
            System.err.printf("[%s] Node: %s -> 💥 Error processing request: %s%n",
                    threadName, nodeName, e.getMessage());
        }
    }
}
