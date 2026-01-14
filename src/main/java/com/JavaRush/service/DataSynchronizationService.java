package com.JavaRush.service;

import com.JavaRush.dao.CityDAO;
import com.JavaRush.dao.CountryDAO;
import com.JavaRush.domain.City;
import com.JavaRush.domain.CountryLanguage;
import com.JavaRush.redis.CityCountry;
import com.JavaRush.redis.Language;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DataSynchronizationService {

    private final SessionFactory sessionFactory;
    private final RedisClient redisClient;
    private final ObjectMapper mapper;
    private final CityDAO cityDAO;
    private final CountryDAO countryDAO;

    public DataSynchronizationService(SessionFactory sessionFactory, RedisClient redisClient) {
        this.sessionFactory = sessionFactory;
        this.redisClient = redisClient;
        this.mapper = new ObjectMapper();
        this.cityDAO = new CityDAO(sessionFactory);
        this.countryDAO = new CountryDAO(sessionFactory);
    }

    public void synchronizeData() {
        List<City> cities = fetchAllCities();
        List<CityCountry> preparedData = transformToCityCountry(cities);
        pushToRedis(preparedData);
    }

    private List<City> fetchAllCities() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();

            List<City> allCities = new ArrayList<>();
            int totalCount = cityDAO.getTotalCount();
            int step = 500;

            for (int i = 0; i < totalCount; i += step) {
                allCities.addAll(cityDAO.getItems(i, step));
            }

            session.getTransaction().commit();
            return allCities;
        }
    }

    private List<CityCountry> transformToCityCountry(List<City> cities) {
        return cities.stream().map(city -> {
            CityCountry cc = new CityCountry();
            cc.setId(city.getId());
            cc.setName(city.getName());
            cc.setPopulation(city.getPopulation());
            cc.setDistrict(city.getDistrict());

            var country = city.getCountry();
            cc.setCountryCode(country.getCode());
            cc.setCountryName(country.getName());
            cc.setAlternativeCountryCode(country.getAlternativeCode());
            cc.setContinent(country.getContinent());
            cc.setCountryRegion(country.getRegion());
            cc.setCountrySurfaceArea(country.getSurfaceArea());
            cc.setCountryPopulation(country.getPopulation());

            Set<Language> languages = country.getLanguages().stream()
                    .map(cl -> {
                        Language lang = new Language();
                        lang.setLanguage(cl.getLanguage());
                        lang.setOfficial(cl.getOfficial());
                        lang.setPercentage(cl.getPercentage());
                        return lang;
                    })
                    .collect(Collectors.toSet());

            cc.setLanguages(languages);
            return cc;
        }).collect(Collectors.toList());
    }

    private void pushToRedis(List<CityCountry> data) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (CityCountry cityCountry : data) {
                try {
                    sync.set(String.valueOf(cityCountry.getId()), mapper.writeValueAsString(cityCountry));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void testPerformance(List<Integer> ids) {
        long startRedis = System.currentTimeMillis();
        testRedisData(ids);
        long stopRedis = System.currentTimeMillis();

        long startMysql = System.currentTimeMillis();
        testMysqlData(ids);
        long stopMysql = System.currentTimeMillis();

        System.out.printf("%s:\t%d ms\n", "Redis", (stopRedis - startRedis));
        System.out.printf("%s:\t%d ms\n", "MySQL", (stopMysql - startMysql));
    }

    private void testRedisData(List<Integer> ids) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (Integer id : ids) {
                String value = sync.get(String.valueOf(id));
                if (value != null) {
                    try {
                        mapper.readValue(value, CityCountry.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void testMysqlData(List<Integer> ids) {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : ids) {
                City city = cityDAO.getById(id);
                if (city != null) {
                    city.getCountry().getLanguages(); // eager load
                }
            }
            session.getTransaction().commit();
        }
    }

    public void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}