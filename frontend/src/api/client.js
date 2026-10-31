// 백엔드 API 호출을 한 곳에서 처리하는 파일
// 위치: frontend/src/api/client.js

// ---------- 1. 토큰 저장/꺼내기/지우기 ----------

// '로그인 상태 유지' 체크 → localStorage (브라우저 꺼도 남음)
// 체크 안 함            → sessionStorage (탭 닫으면 사라짐)
export function saveTokens(accessToken, refreshToken, keepLogin) {
    const storage = keepLogin ? localStorage : sessionStorage;
    storage.setItem('accessToken', accessToken);
    storage.setItem('refreshToken', refreshToken);
}

// refresh 할 때는 원래 있던 저장소에 그대로 덮어쓴다
function replaceTokens(accessToken, refreshToken) {
    const keepLogin = localStorage.getItem('refreshToken') !== null;
    saveTokens(accessToken, refreshToken, keepLogin);
}

export function getAccessToken() {
    return localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
}

export function getRefreshToken() {
    return localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');
}

export function clearTokens() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
}

// ---------- 2. 실제로 요청 보내기 ----------

// 요청 한 번 보내고 { status, body } 로 돌려준다
async function send(path, options) {
    const headers = { ...options.headers };

    // 파일 업로드(FormData)가 아닐 때만 JSON 이라고 알려준다
    if (options.body && !(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }

    // 로그인 되어 있으면 토큰을 붙인다
    const token = getAccessToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const res = await fetch(path, { ...options, headers });

    // 204(삭제 성공 등)는 본문이 없다
    if (res.status === 204) {
        return { status: 204, body: { success: true, data: null, error: null } };
    }

    const body = await res.json();
    return { status: res.status, body };
}

// access 토큰이 만료됐을 때 새 토큰 받아오기. 성공하면 true
async function refresh() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;

    const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
    });
    const body = await res.json();
    if (!body.success) return false;

    // refreshToken 도 매번 새로 바뀌므로 둘 다 교체
    replaceTokens(body.data.accessToken, body.data.refreshToken);
    return true;
}

// ---------- 3. 페이지에서 쓰는 함수 ----------

// 사용법: const res = await api('/api/home');
//        res.success 가 true 면 res.data 사용, false 면 res.error.message 띄우기
export async function api(path, options = {}) {
    let { status, body } = await send(path, options);

    // 토큰 만료 → refresh 후 한 번만 다시 시도
    if (status === 401 && body.error?.code === 'EXPIRED_TOKEN') {
        const ok = await refresh();
        if (!ok) {
            clearTokens();
            window.location.href = '/signin';
            return body;
        }
        ({ body } = await send(path, options));
    }

    // 토큰이 없거나 잘못됨 → 로그인 화면으로
    if (status === 401 && body.error?.code === 'UNAUTHORIZED') {
        clearTokens();
        window.location.href = '/signin';
    }

    return body;
}
