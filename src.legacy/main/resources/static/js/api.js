// API layer: fetch wrappers + domain functions.

export const BOARD_ID = 1;
export const LIKE_STRATEGY = 'pessimistic-lock-2';
export const PAGE_SIZE = 10;
export const COMMENT_PAGE_SIZE = 20;

async function handle(res) {
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const err = new Error(`HTTP ${res.status} ${res.statusText}: ${text}`);
    console.error(err);
    throw err;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export function apiGet(path) {
  return fetch(path, { method: 'GET' }).then(handle);
}
export function apiPost(path, body) {
  return fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).then(handle);
}
export function apiPut(path, body) {
  return fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).then(handle);
}
export function apiDelete(path) {
  return fetch(path, { method: 'DELETE' }).then(handle);
}

// ---- Article ----
export function listArticles(page) {
  return apiGet(`/v1/articles?boardId=${BOARD_ID}&page=${page}&pageSize=${PAGE_SIZE}`);
}
export function readArticle(articleId, userId) {
  return apiGet(`/v1/articles/${articleId}?userId=${userId}`);
}
export function createArticle({ title, content, writerId }) {
  return apiPost('/v1/articles', { title, content, writerId, boardId: BOARD_ID });
}
export function updateArticle(articleId, { title, content }) {
  return apiPut(`/v1/articles/${articleId}`, { title, content });
}
export function deleteArticle(articleId) {
  return apiDelete(`/v1/articles/${articleId}`);
}

// ---- Comment V2 ----
export function listComments(articleId, page) {
  return apiGet(`/v2/comments?articleId=${articleId}&page=${page}&pageSize=${COMMENT_PAGE_SIZE}`);
}
export function createComment({ articleId, content, parentPath, writerId }) {
  const body = { articleId, content, writerId };
  if (parentPath) body.parentPath = parentPath;
  return apiPost('/v2/comments', body);
}
export function deleteComment(commentId) {
  return apiDelete(`/v2/comments/${commentId}`);
}

// ---- Like ----
export function readLike(articleId, userId) {
  return apiGet(`/v1/article-likes/articles/${articleId}/users/${userId}`);
}
export function like(articleId, userId) {
  return apiPost(`/v1/article-likes/articles/${articleId}/users/${userId}/${LIKE_STRATEGY}`);
}
export function unlike(articleId, userId) {
  return apiDelete(`/v1/article-likes/articles/${articleId}/users/${userId}/${LIKE_STRATEGY}`);
}

// ---- HotArticle ----
export function listTodayHotArticles() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const dateStr = `${y}${m}${day}`;
  return apiGet(`/v1/hot-articles/articles/date/${dateStr}`);
}
