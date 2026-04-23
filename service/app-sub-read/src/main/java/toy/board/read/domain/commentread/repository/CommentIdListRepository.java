package toy.board.read.domain.commentread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CommentIdListRepository {
    private final StringRedisTemplate redisTemplate;

    // comment-read::article::{articleId}::comment-list
    private static final String KEY_FORMAT = "comment-read::article::%s::comment-list";

    public void add(Long articleId, Long commentId, Double score, Long maxSize) {
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateKey(articleId);
            conn.zAdd(key, score, String.valueOf(commentId));
            conn.zRemRange(key, 0, -maxSize - 1);
            return null;
        });
    }

    public void delete(Long articleId, Long commentId) {
        redisTemplate.opsForZSet().remove(generateKey(articleId), String.valueOf(commentId));
    }

    public List<Long> readAll(Long articleId, Long offset, Long limit) {
        Set<String> range = redisTemplate.opsForZSet()
                .range(generateKey(articleId), offset, offset + limit - 1);
        if (range == null) {
            return List.of();
        }
        return range.stream().map(Long::valueOf).toList();
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
