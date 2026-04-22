package toy.board.read.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only stub that supplies the {@link ConsumerFactory} bean that production
 * {@link KafkaConfig} requires. We exclude {@code KafkaAutoConfiguration} in
 * {@code application-test.yml} (to keep integration tests free of a live broker
 * dependency), but {@code KafkaConfig} is under component scan and still
 * demands a {@link ConsumerFactory}. This class satisfies that dependency.
 * <p>
 * No listener containers are ever started against this factory in the Redis
 * repository tests, so no network connection is attempted.
 */
@Configuration
@Profile("test")
public class TestKafkaConsumerFactoryConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "toy-board-app-read-test");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
