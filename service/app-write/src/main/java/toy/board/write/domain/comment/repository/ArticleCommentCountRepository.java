package toy.board.write.domain.comment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import toy.board.write.domain.comment.entity.ArticleCommentCount;

@Repository
public interface ArticleCommentCountRepository extends JpaRepository<ArticleCommentCount, Long> {
    /**
     * UPDATE 후 성공 횟수가 0건이면 INSERT 하는 방식은 두 트랜잭션이 동시에 row가 있는지 확인하고
     * 각각 INSERT를 시도할 때 두번째 요청은 Duplicate Key 에러가 발생할 것 같아요.
     * ON DUPLICATE KEY UPDATE로 처리하면 INSERT 와 UPDATE 가 단일 원자적 연산으로 처리해서
     * InnoDB 가 X lock 으로 직렬화해서 동시성 문제가 없어져요.
     *
     * 추가로 이 부분 동시성 테스트 하는 케이스에 대해 테스트 코드가 있으면 좋을 것 같아요.
     */
    @Query(
            value = "INSERT INTO article_comment_count (article_id, comment_count) VALUES (:articleId, 1) " +
                    "ON DUPLICATE KEY UPDATE comment_count = comment_count + 1",
            nativeQuery = true
    )
    @Modifying
    void increase(@Param("articleId") Long articleId);

    @Query(
            value = "UPDATE article_comment_count SET comment_count = comment_count - 1 " +
                    "WHERE article_id = :articleId ",
            nativeQuery = true
    )
    @Modifying
    int decrease(@Param("articleId") Long articleId);
}
