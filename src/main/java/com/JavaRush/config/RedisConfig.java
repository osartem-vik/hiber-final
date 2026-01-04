package com.JavaRush.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

public class RedisConfig {

    public static RedisClient createRedisClient() {
        RedisClient redisClient = RedisClient.create(RedisURI.create("localhost", 6379));

        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            System.out.println("\nConnected to Redis\n");
        } catch (Exception e) {
            System.out.println("\nFailed to connect to Redis (is the server not running?). Continuing without it.\n");
            e.printStackTrace();
        }

        return redisClient;
    }
}
