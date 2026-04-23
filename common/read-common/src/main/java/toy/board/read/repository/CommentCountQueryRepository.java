package toy.board.read.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommentCountQueryRepository {
    private final StringRedisTemplate redisTemplate;

    // comment-read::article::{articleId}::comment-count
    private static final String KEY_FORMAT = "comment-read::article::%s::comment-count";

    public void createOrUpdate(Long articleId, Long commentCount, Duration ttl) {
        redisTemplate.opsForValue().set(generateKey(articleId), String.valueOf(commentCount), ttl);
    }

    public Optional<Long> read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return Optional.ofNullable(result).map(Long::valueOf);
    }

    public void delete(Long articleId) {
        redisTemplate.delete(generateKey(articleId));
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
