package toy.board.domain.hotarticle.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class TimeCalculatorUtilsTest {
    @Test
    void test() {
        Duration duration = TimeCalculatorUtils.calculateDurationToMidNight();
        System.out.println("duration.getSeconds() / 60 = " + duration.getSeconds() / 60);
    }
}
