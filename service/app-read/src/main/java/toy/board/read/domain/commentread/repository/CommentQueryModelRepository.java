package toy.board.read.domain.commentread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import toy.board.common.dataserializer.DataSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Repository
@RequiredArgsConstructor
public class CommentQueryModelRepository {
    private final StringRedisTemplate redisTemplate;

    // comment-read::comment::{commentId}
    private static final String KEY_FORMAT = "comment-read::comment::%s";

    public void create(CommentQueryModel commentQueryModel, Duration ttl) {
        redisTemplate.opsForValue()
                .set(generateKey(commentQueryModel), DataSerializer.serialize(commentQueryModel), ttl);
    }

    public void delete(Long commentId) {
        redisTemplate.delete(generateKey(commentId));
    }

    public Optional<CommentQueryModel> read(Long commentId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(commentId))
        ).map(json -> DataSerializer.deserialize(json, CommentQueryModel.class));
    }

    public Map<Long, CommentQueryModel> readAll(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Map.of();
        }
        List<String> keyList = commentIds.stream().map(this::generateKey).toList();
        return redisTemplate.opsForValue().multiGet(keyList).stream()
                .filter(Objects::nonNull)
                .map(json -> DataSerializer.deserialize(json, CommentQueryModel.class))
                .collect(toMap(CommentQueryModel::getCommentId, identity()));
    }

    private String generateKey(CommentQueryModel commentQueryModel) {
        return generateKey(commentQueryModel.getCommentId());
    }

    private String generateKey(Long commentId) {
        return KEY_FORMAT.formatted(commentId);
    }
}
