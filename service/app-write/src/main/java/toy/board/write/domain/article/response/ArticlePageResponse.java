package toy.board.write.domain.article.response;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class ArticlePageResponse {
    private List<ArticleResponse> articles;

    public static ArticlePageResponse of(List<ArticleResponse> articles) {
        ArticlePageResponse response = new ArticlePageResponse();
        response.articles = articles;
        return response;
    }
}
