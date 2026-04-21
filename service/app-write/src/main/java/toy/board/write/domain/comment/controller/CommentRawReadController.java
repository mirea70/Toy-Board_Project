package toy.board.write.domain.comment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import toy.board.write.domain.comment.response.CommentPageResponse;
import toy.board.write.domain.comment.response.CommentResponse;
import toy.board.write.domain.comment.service.CommentServiceV2;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentRawReadController {
    private final CommentServiceV2 commentService;

    @GetMapping("/v2/comments/{commentId}")
    public CommentResponse read(@PathVariable("commentId") Long commentId) {
        return commentService.read(commentId);
    }

    @GetMapping("/v2/comments")
    public CommentPageResponse readAll(
            @RequestParam("articleId") Long articleId,
            @RequestParam("page") Long page,
            @RequestParam("pageSize") Long pageSize) {
        return commentService.readAll(articleId, page, pageSize);
    }

    @GetMapping("/v2/comments/infinite-scroll")
    public List<CommentResponse> readInfiniteScroll(
            @RequestParam("articleId") Long articleId,
            @RequestParam(value = "lastPath", required = false) String lastPath,
            @RequestParam("pageSize") Long pageSize) {
        return commentService.readAllInfiniteScroll(articleId, lastPath, pageSize);
    }

    @GetMapping("/v2/comments/articles/{articleId}/count")
    public Long count(@PathVariable("articleId") Long articleId) {
        return commentService.count(articleId);
    }
}
