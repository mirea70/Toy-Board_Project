import { renderHeader, requireUserId, getCurrentUserId } from './user.js';
import { readArticle, createArticle, updateArticle } from './api.js';

renderHeader(document.getElementById('header-root'));

const params = new URLSearchParams(location.search);
const articleId = params.get('id');
const isEdit = !!articleId;

const titleEl = document.getElementById('title');
const contentEl = document.getElementById('content');
const pageTitle = document.getElementById('page-title');
const form = document.getElementById('write-form');
const cancel = document.getElementById('cancel');

if (isEdit) {
  pageTitle.textContent = '게시글 수정';
  const uid = getCurrentUserId();
  if (!uid) {
    alert('상단에서 userId를 먼저 입력하세요.');
  } else {
    readArticle(articleId, uid)
      .then(a => {
        titleEl.value = a.title ?? '';
        contentEl.value = a.content ?? '';
      })
      .catch(() => alert('기존 게시글 로드 실패'));
  }
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const uid = requireUserId();
  const title = titleEl.value.trim();
  const content = contentEl.value.trim();
  if (!title || !content) {
    alert('제목과 내용을 입력하세요.');
    return;
  }
  try {
    if (isEdit) {
      await updateArticle(articleId, { title, content });
      location.href = `./article.html?id=${articleId}`;
    } else {
      const created = await createArticle({ title, content, writerId: uid });
      location.href = `./article.html?id=${created.articleId}`;
    }
  } catch (err) {
    alert('저장 실패');
  }
});

cancel.addEventListener('click', () => {
  if (isEdit) {
    location.href = `./article.html?id=${articleId}`;
  } else {
    location.href = './index.html';
  }
});
