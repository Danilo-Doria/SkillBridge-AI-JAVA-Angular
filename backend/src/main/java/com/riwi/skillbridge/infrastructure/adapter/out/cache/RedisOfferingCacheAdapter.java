package com.riwi.skillbridge.infrastructure.adapter.out.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class RedisOfferingCacheAdapter implements OfferingCachePort {
    private static final String KEY = "offerings:active";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisOfferingCacheAdapter(StringRedisTemplate redis,
                                     ObjectMapper objectMapper,
                                     @Value("${app.cache.offerings-ttl-minutes:10}") long ttlMinutes) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public Optional<List<Offering>> getActiveOfferings() {
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<Offering>>() {}));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void putActiveOfferings(List<Offering> offerings) {
        try {
            redis.opsForValue().set(KEY, objectMapper.writeValueAsString(offerings), ttl);
        } catch (Exception ignored) {
            // Cache is an optimization: a cache failure must not break the use case.
        }
    }

    @Override
    public void evictActiveOfferings() {
        try { redis.delete(KEY); } catch (Exception ignored) { }
    }
}
