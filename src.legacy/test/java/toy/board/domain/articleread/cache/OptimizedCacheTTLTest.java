package toy.board.domain.articleread.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizedCacheTTLTest {
    @Test
    void ofTest() {
        long ttlSeconds = 10;

        OptimizeCacheTTL optimizeCacheTTL = OptimizeCacheTTL.of(ttlSeconds);

        assertThat(optimizeCacheTTL.getLogicalTTL()).isEqualTo(Duration.ofSeconds(ttlSeconds));
        assertThat(optimizeCacheTTL.getPhysicalTTL()).isEqualTo(
                Duration.ofSeconds(ttlSeconds).plusSeconds(OptimizeCacheTTL.PHYSICAL_TTL_DELAY_SECONDS)
        );
    }
}
