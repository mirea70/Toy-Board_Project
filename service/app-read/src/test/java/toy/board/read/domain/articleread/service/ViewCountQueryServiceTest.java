package toy.board.read.domain.articleread.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import toy.board.read.client.ViewClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ViewCountQueryServiceTest {

    @Autowired ViewCountQueryService service;
    @Autowired StringRedisTemplate redisTemplate;

    @MockBean ViewClient viewClient;

    @BeforeEach
    void setUp() {
        try (RedisConnection c = redisTemplate.getConnectionFactory().getConnection()) {
            c.serverCommands().flushDb();
        }
        Mockito.reset(viewClient);
    }

    @Test
    void countAll_returnsEmptyMap_whenInputEmpty() {
        Map<Long, Long> result = service.countAll(List.of());

        assertThat(result).isEmpty();
        verify(viewClient, never()).countAll(anyList());
    }

    @Test
    void countAll_callsClientOnce_whenAllCacheMiss() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(viewClient.countAll(ids)).thenReturn(Map.of(1L, 10L, 2L, 20L, 3L, 30L));

        Map<Long, Long> result = service.countAll(ids);

        assertThat(result).containsEntry(1L, 10L).containsEntry(2L, 20L).containsEntry(3L, 30L);
        verify(viewClient, times(1)).countAll(ids);
        assertThat(redisTemplate.opsForValue().get("articleViewCountBatch::1")).isEqualTo("10");
        assertThat(redisTemplate.opsForValue().get("articleViewCountBatch::2")).isEqualTo("20");
        assertThat(redisTemplate.opsForValue().get("articleViewCountBatch::3")).isEqualTo("30");
    }

    @Test
    void countAll_skipsClient_whenAllCacheHit() {
        redisTemplate.opsForValue().set("articleViewCountBatch::1", "100");
        redisTemplate.opsForValue().set("articleViewCountBatch::2", "200");

        Map<Long, Long> result = service.countAll(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 100L).containsEntry(2L, 200L);
        verify(viewClient, never()).countAll(anyList());
    }

    @Test
    void countAll_callsClientForMissedIdsOnly_whenPartialHit() {
        redisTemplate.opsForValue().set("articleViewCountBatch::1", "100");
        when(viewClient.countAll(List.of(2L, 3L))).thenReturn(Map.of(2L, 20L, 3L, 30L));

        Map<Long, Long> result = service.countAll(List.of(1L, 2L, 3L));

        assertThat(result).containsEntry(1L, 100L).containsEntry(2L, 20L).containsEntry(3L, 30L);
        verify(viewClient, times(1)).countAll(List.of(2L, 3L));
    }

    @Test
    void countAll_fillsZeroForMissingIds_whenClientReturnsIncomplete() {
        when(viewClient.countAll(List.of(1L, 2L))).thenReturn(Map.of(1L, 5L));

        Map<Long, Long> result = service.countAll(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 5L).containsEntry(2L, 0L);
    }
}
