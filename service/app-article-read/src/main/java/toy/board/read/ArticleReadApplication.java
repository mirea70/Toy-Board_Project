package toy.board.read;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"toy.board"})
public class ArticleReadApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArticleReadApplication.class, args);
    }
}
