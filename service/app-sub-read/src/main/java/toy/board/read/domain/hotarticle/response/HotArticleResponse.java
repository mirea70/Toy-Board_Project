package toy.board.read.domain.hotarticle.response;

import lombok.Getter;
import lombok.ToString;
import toy.board.read.client.ArticleClient;
import toy.board.read.repository.ArticleQueryModel;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    public static HotArticleResponse of(Long articleId, String title, LocalDateTime createdAt) {
        HotArticleResponse response = new HotArticleResponse();
        response.articleId = articleId;
        response.title = title;
        response.createdAt = createdAt;
        return response;
    }

    public static HotArticleResponse from(ArticleClient.ArticleResponse article) {
        return of(article.getArticleId(), article.getTitle(), article.getCreatedAt());
    }

    public static HotArticleResponse from(ArticleQueryModel model) {
        return of(model.getArticleId(), model.getTitle(), model.getCreatedAt());
    }
}
