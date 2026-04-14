package toy.board.domain.view.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import toy.board.domain.view.entity.ArticleViewCount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArticleViewCountBackupRepositoryTest {
    @Autowired
    ArticleViewCountBackupRepository articleViewCountBackupRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void updateViewCountTest() {
        articleViewCountBackupRepository.save(
                ArticleViewCount.init(1L, 0L)
        );
        entityManager.flush();
        entityManager.clear();

        int result1 = articleViewCountBackupRepository.updateViewCount(1L, 100L);
        int result2 = articleViewCountBackupRepository.updateViewCount(1L, 300L);
        int result3 = articleViewCountBackupRepository.updateViewCount(1L, 200L);

        assertThat(result1).isEqualTo(1);
        assertThat(result2).isEqualTo(1);
        assertThat(result3).isEqualTo(0);

        ArticleViewCount articleViewCount = articleViewCountBackupRepository.findById(1L).get();
        assertThat(articleViewCount.getViewCount()).isEqualTo(300L);
    }
}
