package toy.board.write.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import toy.board.common.snowflake.Snowflake;

@Configuration
public class SnowflakeConfig {
    @Bean
    public Snowflake snowflake() {
        return new Snowflake();
    }
}
