package com.JavaRush;

import com.JavaRush.config.HibernateConfig;
import com.JavaRush.config.RedisConfig;
import com.JavaRush.service.DataSynchronizationService;
import io.lettuce.core.RedisClient;
import org.hibernate.SessionFactory;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        SessionFactory sessionFactory = HibernateConfig.createSessionFactory();
        RedisClient redisClient = RedisConfig.createRedisClient();

        DataSynchronizationService service = new DataSynchronizationService(sessionFactory, redisClient);

        service.synchronizeData();

        List<Integer> testIds = List.of(3, 2545, 123, 4, 189, 89, 3458, 1189, 10, 102);
        service.testPerformance(testIds);

        service.shutdown();
    }
}