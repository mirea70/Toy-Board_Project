package toy.board.read.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CommentCountQueryRepositoryTest {

    @Autowired
    CommentCountQueryRepository repository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void read_returnsEmpty_whenKeyAbsent() {
        Optional<Long> result = repository.read(42L);
        assertThat(result).isEmpty();
    }

    @Test
    void createOrUpdate_thenRead_returnsStoredCount() {
        repository.createOrUpdate(42L, 7L, Duration.ofDays(1));

        Optional<Long> result = repository.read(42L);

        assertThat(result).contains(7L);
    }

    @Test
    void createOrUpdate_appliesTtl() {
        repository.createOrUpdate(42L, 7L, Duration.ofDays(1));

        String key = "comment-read::article::42::comment-count";
        Long ttlSeconds = redisTemplate.getExpire(key);

        assertThat(ttlSeconds).isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(ttlSeconds).isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    void createOrUpdate_overwritesAndResetsTtl() {
        repository.createOrUpdate(42L, 7L, Duration.ofSeconds(60));
        repository.createOrUpdate(42L, 9L, Duration.ofDays(1));

        assertThat(repository.read(42L)).contains(9L);
        Long ttlSeconds = redisTemplate.getExpire("comment-read::article::42::comment-count");
        assertThat(ttlSeconds).isGreaterThan(Duration.ofHours(23).toSeconds());
    }

    @Test
    void delete_removesKey() {
        repository.createOrUpdate(42L, 7L, Duration.ofDays(1));
        repository.delete(42L);

        assertThat(repository.read(42L)).isEmpty();
    }
}
