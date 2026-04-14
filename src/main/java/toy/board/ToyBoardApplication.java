package toy.board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToyBoardApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToyBoardApplication.class, args);
    }
}
