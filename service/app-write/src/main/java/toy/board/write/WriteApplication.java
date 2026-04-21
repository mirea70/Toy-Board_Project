package toy.board.write;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"toy.board"})
@EntityScan(basePackages = {"toy.board"})
@EnableJpaRepositories(basePackages = {"toy.board"})
public class WriteApplication {
    public static void main(String[] args) {
        SpringApplication.run(WriteApplication.class, args);
    }
}
