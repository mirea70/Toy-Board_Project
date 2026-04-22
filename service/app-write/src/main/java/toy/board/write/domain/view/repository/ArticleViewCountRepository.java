package toy.board.write.domain.view.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ArticleViewCountRepository {
    private final StringRedisTemplate redisTemplate;

    // view::article::{article_id}::view_count
    private static final String KEY_FORMAT = "view::article::%s::view_count";

    public Long read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return result == null ? 0L : Long.parseLong(result);
    }

    public Map<Long, Long> readAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        List<String> keys = articleIds.stream().map(this::generateKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, Long> result = new HashMap<>();
        for (int i = 0; i < articleIds.size(); i++) {
            String v = values == null ? null : values.get(i);
            result.put(articleIds.get(i), v == null ? 0L : Long.parseLong(v));
        }
        return result;
    }

    public Long increase(Long articleId) {
        return redisTemplate.opsForValue().increment(generateKey(articleId));
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
