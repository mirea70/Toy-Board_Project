// Manages current user id via localStorage and renders the shared site header.
const USER_KEY = 'currentUserId';

export function getCurrentUserId() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : null;
}

export function setCurrentUserId(userId) {
  if (userId === null || userId === undefined || userId === '') {
    localStorage.removeItem(USER_KEY);
  } else {
    localStorage.setItem(USER_KEY, String(userId));
  }
}

export function requireUserId() {
  const uid = getCurrentUserId();
  if (!uid) {
    alert('먼저 상단에서 userId를 입력하세요.');
    throw new Error('userId not set');
  }
  return uid;
}

export function renderHeader(container, { title = 'Toy Board' } = {}) {
  container.innerHTML = `
    <header class="site-header">
      <h1><a href="./index.html">${title}</a></h1>
      <div class="user-box">
        <label for="user-input">userId</label>
        <input id="user-input" type="number" min="1" placeholder="e.g. 1" />
        <button id="user-save" type="button">저장</button>
      </div>
    </header>
  `;
  const input = container.querySelector('#user-input');
  const save = container.querySelector('#user-save');
  const current = getCurrentUserId();
  if (current) input.value = current;

  save.addEventListener('click', () => {
    const val = input.value.trim();
    if (!val) {
      setCurrentUserId(null);
      alert('userId가 초기화되었습니다.');
      return;
    }
    const n = Number(val);
    if (!Number.isFinite(n) || n <= 0) {
      alert('양의 정수만 입력하세요.');
      return;
    }
    setCurrentUserId(n);
    alert('userId 저장 완료: ' + n);
    // 화면 갱신이 필요한 페이지는 reload
    window.location.reload();
  });
}
