package learn.vaibhav.gupta;

import learn.vaibhav.gupta.Strategies.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    // === CHOOSE YOUR STRATEGY ===
    private static RateLimitStrategy chooseStrategy() {
        return
//         new TokenBucket();
//         new LeakingBucket();
        new FixedWindowCounter();
//         new SlidingWindowLog();
//         new SlidingWindowCounter();
    }

    public static void main(String[] args) throws InterruptedException {
        // 1. Setup Jedis Connection Pool (Shared infrastructure)
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20); // Scale connections to match thread load
        JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379);

        // Flush Redis storage so your test starts clean
        try (var jedis = jedisPool.getResource()) { jedis.flushAll(); }

        RateLimitStrategy selectedStrategy = chooseStrategy();
        String strategy = selectedStrategy.getClass().getSimpleName();
        System.out.println("Running Distributed Simulation Using: " + strategy);

        // Setup distributed App Server Cluster
        List<AppNode> appNodes = List.of(
            new AppNode("Node-Alpha", jedisPool, selectedStrategy),
            new AppNode("Node-Beta", jedisPool, selectedStrategy),
            new AppNode("Node-Gamma", jedisPool, selectedStrategy)
        );

        // Simulation parameters
        int param1 = 5; // e.g., max 5 requests / Max 5 bucket capacity
        int param2 = 2; // e.g., 2 seconds window / 2 tokens refilled per second

        ExecutorService clientThreads = Executors.newFixedThreadPool(15);

        System.out.println("=== STARTING DISTRIBUTED RATE LIMITER SIMULATION ===");

        System.out.println("Aligning simulation with a fresh window boundary...");
        // Wait until System.currentTimeMillis() % 2000 gets incredibly close to 0
        while (System.currentTimeMillis() % (param2*1000L) > 10) {
            Thread.onSpinWait(); // Efficiently wait for the remainder of the millisecond block
        }
        System.out.println("Window synced! Starting threads now...");


        // Simulate intense user load hitting the app cluster concurrently
        for (int i = 0; i < 40; i++) {
            int index = i;
            clientThreads.submit(() -> {
                // Alternates request routes across our nodes
                AppNode node = appNodes.get(index % appNodes.size());
                node.processRequest("AGENT-X", "api/v1/test", param1, param2);
            });
            Thread.sleep(80); // Rapid request intervals
        }

        // Clean up execution threads
        clientThreads.shutdown();
        assert clientThreads.awaitTermination(5, TimeUnit.SECONDS) : "Threads timed out!";

        jedisPool.close();
        System.out.println("=== SIMULATION COMPLETED ===");
    }
}
