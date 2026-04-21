package toy.board.read.domain.hotarticle.response;

import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    // TODO Task 18: replace with HotArticleResponse.from(ArticleResponse) where ArticleResponse is the RestClient DTO returned by ArticleClient.read(articleId).
    public static HotArticleResponse of(Long articleId, String title, LocalDateTime createdAt) {
        HotArticleResponse response = new HotArticleResponse();
        response.articleId = articleId;
        response.title = title;
        response.createdAt = createdAt;
        return response;
    }
}
