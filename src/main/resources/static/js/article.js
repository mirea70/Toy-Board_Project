import { renderHeader, requireUserId, getCurrentUserId } from './user.js';
import {
  readArticle, deleteArticle,
  listComments, createComment, deleteComment,
  readLike, like, unlike,
  COMMENT_PAGE_SIZE,
} from './api.js';

renderHeader(document.getElementById('header-root'));

const params = new URLSearchParams(location.search);
const articleId = params.get('id');
if (!articleId) {
  document.getElementById('article-root').textContent = 'articleId가 없습니다.';
  throw new Error('no articleId');
}

const articleRoot = document.getElementById('article-root');
const commentList = document.getElementById('comment-list');
const commentPagination = document.getElementById('comment-pagination');
const rootForm = document.getElementById('root-comment-form');

let currentArticle = null;
let currentLiked = false;

async function loadArticle() {
  const uid = getCurrentUserId();
  if (!uid) {
    articleRoot.innerHTML = '상단에서 userId를 먼저 입력하세요.';
    return;
  }
  try {
    currentArticle = await readArticle(articleId, uid);
  } catch (e) {
    articleRoot.textContent = '게시글을 불러오지 못했습니다.';
    return;
  }

  // 좋아요 상태는 독립 호출 (없으면 미좋아요로 간주)
  try {
    const likeRes = await readLike(articleId, uid);
    currentLiked = !!likeRes;
  } catch (e) {
    currentLiked = false;
  }

  renderArticle();
}

function renderArticle() {
  const a = currentArticle;
  const isOwner = Number(a.writerId) === Number(getCurrentUserId());
  articleRoot.innerHTML = `
    <header>
      <h2>${escapeHtml(a.title)}</h2>
      <div class="article-meta">
        작성자 ${a.writerId} · 조회 ${a.articleViewCount ?? 0} · 좋아요 <span id="like-count">${a.articleLikeCount ?? 0}</span> · 댓글 ${a.articleCommentCount ?? 0}
      </div>
    </header>
    <p style="white-space:pre-wrap">${escapeHtml(a.content)}</p>
    <footer>
      <button id="like-btn" class="${currentLiked ? 'liked' : ''}">${currentLiked ? '<span class="heart">♥</span> 좋아요 취소' : '<span class="heart">♡</span> 좋아요'}</button>
      ${isOwner ? `
        <a href="./write.html?id=${a.articleId}" role="button" class="secondary">수정</a>
        <button id="delete-btn" class="contrast">삭제</button>
      ` : ''}
    </footer>
  `;

  document.getElementById('like-btn').addEventListener('click', onToggleLike);
  if (isOwner) {
    document.getElementById('delete-btn').addEventListener('click', onDeleteArticle);
  }
}

async function onToggleLike() {
  const uid = requireUserId();
  try {
    if (currentLiked) {
      await unlike(articleId, uid);
      currentLiked = false;
      currentArticle.articleLikeCount = Math.max(0, (currentArticle.articleLikeCount ?? 1) - 1);
    } else {
      await like(articleId, uid);
      currentLiked = true;
      currentArticle.articleLikeCount = (currentArticle.articleLikeCount ?? 0) + 1;
    }
    renderArticle();
  } catch (e) {
    alert('좋아요 처리 실패');
  }
}

async function onDeleteArticle() {
  if (!confirm('정말 삭제하시겠습니까?')) return;
  try {
    await deleteArticle(articleId);
    location.href = './index.html';
  } catch (e) {
    alert('삭제 실패');
  }
}

// ---- Comments ----
async function loadComments(page = 1) {
  try {
    const resp = await listComments(articleId, page);
    const comments = resp.comments || [];
    if (comments.length === 0) {
      commentList.innerHTML = '<p>댓글이 없습니다.</p>';
      commentPagination.innerHTML = '';
      return;
    }
    commentList.innerHTML = comments.map(renderComment).join('');
    // bind reply / delete buttons
    commentList.querySelectorAll('[data-reply]').forEach(btn => {
      btn.addEventListener('click', () => openReplyForm(btn.dataset.reply, btn.dataset.path));
    });
    commentList.querySelectorAll('[data-delete]').forEach(btn => {
      btn.addEventListener('click', () => onDeleteComment(btn.dataset.delete));
    });
    renderCommentPagination(page, resp.commentCount || 0);
  } catch (e) {
    commentList.innerHTML = '<p>댓글 로드 실패</p>';
  }
}

function renderComment(c) {
  const depth = Math.max(0, Math.floor((c.path?.length || 5) / 5) - 1);
  const indent = depth * 20;
  const isMine = Number(c.writerId) === Number(getCurrentUserId());
  const body = c.deleted ? '<em>(삭제된 댓글)</em>' : escapeHtml(c.content);
  return `
    <div class="comment" style="margin-left:${indent}px">
      <div>${body}</div>
      <div class="article-meta">작성자 ${c.writerId}</div>
      <div class="comment-actions">
        <button type="button" data-reply="${c.commentId}" data-path="${c.path}">답글</button>
        ${isMine && !c.deleted ? `<button type="button" data-delete="${c.commentId}" class="contrast">삭제</button>` : ''}
      </div>
      <div class="reply-form hidden" id="reply-form-${c.commentId}">
        <textarea rows="2" placeholder="답글 내용"></textarea>
        <button type="button" class="reply-submit">등록</button>
      </div>
    </div>
  `;
}

function openReplyForm(commentId, parentPath) {
  const form = document.getElementById('reply-form-' + commentId);
  if (!form) return;
  form.classList.toggle('hidden');
  const submit = form.querySelector('.reply-submit');
  submit.onclick = async () => {
    const uid = requireUserId();
    const textarea = form.querySelector('textarea');
    const content = textarea.value.trim();
    if (!content) return;
    try {
      await createComment({ articleId, content, parentPath, writerId: uid });
      await loadComments(1);
    } catch (e) {
      alert('답글 등록 실패');
    }
  };
}

async function onDeleteComment(commentId) {
  if (!confirm('댓글을 삭제하시겠습니까?')) return;
  try {
    await deleteComment(commentId);
    await loadComments(1);
  } catch (e) {
    alert('댓글 삭제 실패');
  }
}

function renderCommentPagination(current, total) {
  const totalPages = Math.max(1, Math.ceil(total / COMMENT_PAGE_SIZE));
  const btns = [];
  if (current > 1) btns.push(`<button data-page="${current - 1}">이전</button>`);
  for (let p = 1; p <= totalPages; p++) {
    btns.push(`<button data-page="${p}" ${p === current ? 'aria-current="true"' : ''}>${p}</button>`);
  }
  if (current < totalPages) btns.push(`<button data-page="${current + 1}">다음</button>`);
  commentPagination.innerHTML = btns.join('');
  commentPagination.querySelectorAll('button').forEach(b => {
    b.addEventListener('click', () => loadComments(Number(b.dataset.page)));
  });
}

rootForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const uid = requireUserId();
  const input = document.getElementById('root-comment-input');
  const content = input.value.trim();
  if (!content) return;
  try {
    await createComment({ articleId, content, writerId: uid });
    input.value = '';
    await loadComments(1);
  } catch (e2) {
    alert('댓글 등록 실패');
  }
});

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

loadArticle().then(() => loadComments(1));
