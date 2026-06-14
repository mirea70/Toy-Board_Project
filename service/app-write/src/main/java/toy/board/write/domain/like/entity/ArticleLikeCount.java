package toy.board.write.domain.like.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Table(name = "article_like_count")
@Getter
@Entity
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleLikeCount {
    /**
     * 모델에 유니크한 정보들이나 인덱스에 대한 정보들도 있으면 좋을 것 같아요.
     * 레포보고 한번에 파악하기 어려워서요.
     * 추가로 pk를 articleId (fk 정보)로 설정하신 이유가 있을까요?
      */
    @Id
    private Long articleId;
    private Long likeCount;
    @Version
    private Long version;

    public static ArticleLikeCount init(Long articleId, Long likeCount) {
        ArticleLikeCount articleLikeCount = new ArticleLikeCount();
        articleLikeCount.articleId = articleId;
        articleLikeCount.likeCount = likeCount;
        articleLikeCount.version = 0L;
        return articleLikeCount;
    }

    public void increase() {
        this.likeCount++;
    }

    public void decrease() {
        this.likeCount--;
    }
}
