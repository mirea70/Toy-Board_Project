import { renderHeader } from './user.js';
import { listArticles, listTodayHotArticles, PAGE_SIZE } from './api.js';

renderHeader(document.getElementById('header-root'));

const hotList = document.getElementById('hot-list');
const articleList = document.getElementById('article-list');
const pagination = document.getElementById('pagination');

async function loadHot() {
  try {
    const hot = await listTodayHotArticles();
    if (!hot || hot.length === 0) {
      hotList.innerHTML = '<li style="cursor:default; color:var(--pico-muted-color)">오늘의 인기글 없음</li>';
      return;
    }
    hotList.innerHTML = hot.map(h => `
      <li data-id="${h.articleId}">${escapeHtml(h.title)}</li>
    `).join('');
    hotList.querySelectorAll('li[data-id]').forEach(li => {
      li.addEventListener('click', () => {
        location.href = `./article.html?id=${li.dataset.id}`;
      });
    });
  } catch (e) {
    hotList.innerHTML = '<li style="cursor:default; color:var(--pico-del-color)">핫글 로드 실패</li>';
  }
}

async function loadPage(page) {
  try {
    const resp = await listArticles(page);
    const articles = resp.articles || [];
    if (articles.length === 0) {
      articleList.innerHTML = '<li style="cursor:default">게시글이 없습니다.</li>';
      pagination.innerHTML = '';
      return;
    }
    articleList.innerHTML = articles.map(a => `
      <li data-id="${a.articleId}">
        <strong>${escapeHtml(a.title)}</strong>
        <div class="article-meta">
          작성자 ${a.writerId} · 조회 ${a.articleViewCount ?? 0} · 댓글 ${a.articleCommentCount ?? 0} · 좋아요 ${a.articleLikeCount ?? 0}
        </div>
      </li>
    `).join('');
    articleList.querySelectorAll('li[data-id]').forEach(li => {
      li.addEventListener('click', () => {
        location.href = `./article.html?id=${li.dataset.id}`;
      });
    });
    renderPagination(page, resp.articleCount || 0);
  } catch (e) {
    articleList.innerHTML = '<li>목록 로드 실패</li>';
  }
}

function renderPagination(current, total) {
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const windowSize = 5;
  const start = Math.max(1, current - Math.floor(windowSize / 2));
  const end = Math.min(totalPages, start + windowSize - 1);
  const btns = [];
  if (current > 1) btns.push(`<button data-page="${current - 1}">이전</button>`);
  for (let p = start; p <= end; p++) {
    btns.push(`<button data-page="${p}" ${p === current ? 'aria-current="true"' : ''}>${p}</button>`);
  }
  if (current < totalPages) btns.push(`<button data-page="${current + 1}">다음</button>`);
  pagination.innerHTML = btns.join('');
  pagination.querySelectorAll('button').forEach(b => {
    b.addEventListener('click', () => loadPage(Number(b.dataset.page)));
  });
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

loadHot();
loadPage(1);
