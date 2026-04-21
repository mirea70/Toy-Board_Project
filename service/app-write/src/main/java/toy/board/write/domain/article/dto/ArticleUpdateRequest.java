package toy.board.write.domain.article.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ArticleUpdateRequest {
    private String title;
    private String content;
}
