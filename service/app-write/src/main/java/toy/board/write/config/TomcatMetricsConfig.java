package toy.board.write.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
public class TomcatMetricsConfig {

    @Bean
    public TomcatConnectorCustomizer tomcatThreadMetricsCustomizer(MeterRegistry registry) {
        return connector -> {
            LifecycleListener listener = event -> {
                if ("after_start".equals(event.getType())) {
                    Executor exec = connector.getProtocolHandler().getExecutor();
                    if (exec instanceof ThreadPoolExecutor tpe) {
                        Gauge.builder("tomcat.threads.busy", tpe, t -> (double) t.getActiveCount())
                                .baseUnit("threads")
                                .register(registry);
                        Gauge.builder("tomcat.threads.current", tpe, t -> (double) t.getPoolSize())
                                .baseUnit("threads")
                                .register(registry);
                        Gauge.builder("tomcat.threads.config.max", tpe, t -> (double) t.getMaximumPoolSize())
                                .baseUnit("threads")
                                .register(registry);
                    }
                }
            };
            connector.addLifecycleListener(listener);
        };
    }
}
