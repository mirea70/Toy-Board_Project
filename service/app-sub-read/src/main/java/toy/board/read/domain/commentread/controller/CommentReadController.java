package toy.board.read.domain.commentread.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import toy.board.read.domain.commentread.response.CommentReadPageResponse;
import toy.board.read.domain.commentread.response.CommentReadResponse;
import toy.board.read.domain.commentread.service.CommentReadService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentReadController {
    private final CommentReadService commentReadService;

    @GetMapping("/v2/comments/{commentId}")
    public CommentReadResponse read(@PathVariable("commentId") Long commentId) {
        return commentReadService.read(commentId);
    }

    @GetMapping("/v2/comments")
    public CommentReadPageResponse readAll(
            @RequestParam("articleId") Long articleId,
            @RequestParam("page") Long page,
            @RequestParam("pageSize") Long pageSize) {
        return commentReadService.readAll(articleId, page, pageSize);
    }

    @GetMapping("/v2/comments/infinite-scroll")
    public List<CommentReadResponse> readInfiniteScroll(
            @RequestParam("articleId") Long articleId,
            @RequestParam(value = "lastPath", required = false) String lastPath,
            @RequestParam("pageSize") Long pageSize) {
        return commentReadService.readAllInfiniteScroll(articleId, lastPath, pageSize);
    }

    @GetMapping("/v2/comments/articles/{articleId}/count")
    public Long count(@PathVariable("articleId") Long articleId) {
        return commentReadService.count(articleId);
    }
}
