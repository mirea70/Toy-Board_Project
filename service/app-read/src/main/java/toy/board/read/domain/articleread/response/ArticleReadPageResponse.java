package toy.board.read.domain.articleread.response;

import lombok.Getter;

import java.util.List;

@Getter
public class ArticleReadPageResponse {
    private List<ArticleReadResponse> articles;

    public static ArticleReadPageResponse of(List<ArticleReadResponse> articles) {
        ArticleReadPageResponse response = new ArticleReadPageResponse();
        response.articles = articles;
        return response;
    }
}
