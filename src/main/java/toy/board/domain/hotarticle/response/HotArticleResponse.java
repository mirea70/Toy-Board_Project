package toy.board.domain.hotarticle.response;

import lombok.Getter;
import lombok.ToString;
import toy.board.domain.article.entity.Article;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    public static HotArticleResponse from(Article article) {
        HotArticleResponse response = new HotArticleResponse();
        response.articleId = article.getArticleId();
        response.title = article.getTitle();
        response.createdAt = article.getCreatedAt();
        return response;
    }
}
