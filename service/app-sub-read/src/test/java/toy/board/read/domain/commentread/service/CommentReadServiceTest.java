package toy.board.read.domain.commentread.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import toy.board.read.client.CommentClient;
import toy.board.read.repository.CommentCountQueryRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CommentReadServiceTest {

    @Autowired CommentReadService service;
    @Autowired CommentCountQueryRepository repository;
    @Autowired StringRedisTemplate redisTemplate;

    @MockBean CommentClient commentClient;

    @BeforeEach
    void setUp() {
        try (RedisConnection c = redisTemplate.getConnectionFactory().getConnection()) {
            c.serverCommands().flushDb();
        }
        Mockito.reset(commentClient);
    }

    @Test
    void count_returnsStoredValueAndSkipsWriteCall_whenCacheHit() {
        long articleId = 500L;
        repository.createOrUpdate(articleId, 7L, Duration.ofDays(1));

        Long result = service.count(articleId);

        assertThat(result).isEqualTo(7L);
        verify(commentClient, never()).count(anyLong());
    }

    @Test
    void count_callsWriteAndStores_whenCacheMiss() {
        long articleId = 501L;
        when(commentClient.count(articleId)).thenReturn(12L);

        Long result = service.count(articleId);

        assertThat(result).isEqualTo(12L);
        verify(commentClient, times(1)).count(articleId);
        assertThat(repository.read(articleId)).contains(12L);
    }

    @Test
    void count_callsWriteOnce_andCachesForSubsequentCalls() {
        long articleId = 502L;
        when(commentClient.count(articleId)).thenReturn(3L);

        service.count(articleId);
        service.count(articleId);
        service.count(articleId);

        verify(commentClient, times(1)).count(articleId);
    }
}
