package toy.board.read.cache;

import lombok.Getter;

import java.time.Duration;

@Getter
public class OptimizeCacheTTL {
    private Duration logicalTTL;
    private Duration physicalTTL;

    public static final long PHYSICAL_TTL_DELAY_SECONDS = 5;

    public static OptimizeCacheTTL of(long ttlSeconds) {
        OptimizeCacheTTL optimizeCacheTTL = new OptimizeCacheTTL();
        optimizeCacheTTL.logicalTTL = Duration.ofSeconds(ttlSeconds);
        optimizeCacheTTL.physicalTTL = optimizeCacheTTL.logicalTTL.plusSeconds(PHYSICAL_TTL_DELAY_SECONDS);
        return optimizeCacheTTL;
    }
}
