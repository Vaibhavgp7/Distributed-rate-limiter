# Distributed Rate Limiter (Redis + Java)

A Java-based simulation of a **distributed rate limiter** using **Redis** and **Jedis**, supporting multiple classic algorithms via Redis-side **Lua scripts**.

This project runs a concurrent workload against a simulated cluster of application nodes, each enforcing rate limits using one selectable strategy (Fixed Window, Sliding Window, Token Bucket, Leaky Bucket, Sliding Window Log).

---

## Features
- **Distributed simulation**: multiple `AppNode` instances share the same Redis data.
- **Atomic enforcement**: rate limiting decisions are executed via Redis Lua scripts (`jedis.eval(...)`).
- Multiple strategies implemented (each implements `RateLimitStrategy`):
  - Fixed Window Counter
  - Sliding Window Counter (weighted)
  - Sliding Window Log (sorted set)
  - Token Bucket
  - Leaky Bucket

---

## Prerequisites
- **Java 25** (configured in `pom.xml`)
- **Maven**
- **Redis** running locally (`localhost:6379` is used by the simulation)

---

## Setup
1. Start Redis locally. `brew services start redis`
2. (Optional) You can keep Redis data as-is, but the simulation clears Redis at startup:
   - `jedis.flushAll()` is called in `Main.java`.

---

## How the Simulation Works
### Strategy selection
Edit `chooseStrategy()` in `Main.java`:

```java
private static RateLimitStrategy chooseStrategy() {
    return 
    new FixedWindowCounter();
    // new SlidingWindowLog();
    // new SlidingWindowCounter();
    // new TokenBucket();
    // new LeakingBucket();
}
```

This selection controls the algorithm used by all simulated nodes.

### Cluster + traffic generation
- 3 simulated nodes are created:
  - `Node-Alpha`, `Node-Beta`, `Node-Gamma`
- A fixed thread pool generates requests concurrently via Round-Robin routing
- Each request calls:
  - `node.processRequest(clientIp, endpoint, param1, param2)`

In the current code:
- `clientIp` = `AGENT-X`
- `endpoint` = `api/v1/test`
- Example params:
  - `param1 = 5`
  - `param2 = 2`

---

## Rate Limit Parameters (`param1`, `param2`)
These map to strategy-specific concepts:

- **Fixed Window Counter** / **Sliding Window Counter** / **Sliding Window Log**
  - `param1` = max limit
  - `param2` = window size (seconds)
- **Token Bucket**
  - `param1` = bucket capacity
  - `param2` = refill rate per second
- **Leaky Bucket**
  - `param1` = queue size
  - `param2` = leak / outflow rate per second

---

## Redis Keying Model
Each request builds a base key:

```
ratelimit:<clientIp>:<endpoint>
```

Strategies further derive their own Redis keys/data structures, for example:
- Fixed window uses a computed time-window id appended to the base key.
- Sliding window counter uses both current and previous window keys.
- Sliding window log uses a **sorted set** and `zcard`.
- Token/Leaky buckets use **hash fields** and `expire`.

---

## Output
For each simulated request, the console prints:
- Node name
- Allowed vs blocked status (`✅ 200 OK` / `❌ 429 BLOCKED`)
- Strategy-specific debug output (Lua-driven counters/tokens/load)

---

## Notes
- Lua scripts ensure the decision + Redis state update are executed atomically.
- This repo is a simulation; there is no HTTP server layer—“requests” are simulated in `Main.java` via threads calling `AppNode.processRequest(...)`.