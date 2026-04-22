package toy.board.read.domain.articleread.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import toy.board.read.client.ViewClient;
import toy.board.read.domain.articleread.cache.OptimizedCacheable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountQueryService {

    private final ViewClient viewClient;
    private final StringRedisTemplate redisTemplate;

    private static final String BATCH_KEY_PREFIX = "articleViewCountBatch::";
    private static final Duration BATCH_TTL = Duration.ofSeconds(1);

    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        return viewClient.count(articleId);
    }

    public Map<Long, Long> countAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) return Map.of();

        List<String> keys = articleIds.stream().map(id -> BATCH_KEY_PREFIX + id).toList();
        List<String> cached = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Long> result = new HashMap<>();
        List<Long> missed = new ArrayList<>();
        for (int i = 0; i < articleIds.size(); i++) {
            String v = cached == null ? null : cached.get(i);
            if (v != null) {
                result.put(articleIds.get(i), Long.parseLong(v));
            } else {
                missed.add(articleIds.get(i));
            }
        }

        if (!missed.isEmpty()) {
            Map<Long, Long> fetched = viewClient.countAll(missed);
            fetched.forEach((id, count) -> {
                redisTemplate.opsForValue().set(BATCH_KEY_PREFIX + id, String.valueOf(count), BATCH_TTL);
                result.put(id, count);
            });
            for (Long id : missed) result.putIfAbsent(id, 0L);
        }

        return result;
    }
}
