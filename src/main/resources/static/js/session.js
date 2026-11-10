/* Shared session transport. Credentials remain in an HttpOnly session cookie. */
(() => {
    'use strict';
    const rawFetch = window.fetch.bind(window);
    let csrfPromise;
    const csrf = () => csrfPromise ||= rawFetch('/api/auth/csrf', { credentials: 'same-origin' })
        .then(async response => {
            if (!response.ok) throw new Error('Không thể khởi tạo phiên. Vui lòng tải lại trang.');
            return response.json();
        }).catch(error => { csrfPromise = null; throw error; });
    window.fetch = async (resource, options = {}) => {
        const url = new URL(typeof resource === 'string' ? resource : resource.url, location.href);
        const method = (options.method || resource.method || 'GET').toUpperCase();
        if (url.origin === location.origin && url.pathname.startsWith('/api/') && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            const token = await csrf();
            const headers = new Headers(options.headers);
            headers.set(token.headerName, token.token);
            options = { ...options, headers, credentials: 'same-origin' };
        }
        const response = await rawFetch(resource, options);
        if (url.origin === location.origin && url.pathname.startsWith('/api/') && response.status === 403) {
            const error = await response.clone().json().catch(() => null);
            // A CSRF rejection happens before the controller: one retry cannot duplicate a write.
            if (error?.code === 'CSRF_INVALID') {
                csrfPromise = null;
                const token = await csrf();
                const headers = new Headers(options.headers);
                headers.set(token.headerName, token.token);
                return rawFetch(resource, { ...options, headers, credentials: 'same-origin' });
            }
        }
        return response;
    };
    window.Cinema = {
        user: null,
        money: value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0),
        async api(path, options = {}) {
            const response = await fetch(path, {
                ...options, headers: { 'Content-Type': 'application/json', ...options.headers }
            });
            const text = await response.text();
            let data;
            try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
            if (!response.ok) {
                const error = new Error(Object.values(data?.fieldErrors || {}).join(' ') || data?.message || 'Yêu cầu chưa thành công. Vui lòng thử lại.');
                error.status = response.status;
                throw error;
            }
            return data;
        },
        resetCsrf() { csrfPromise = null; },
        async me() {
            try { this.user = await this.api('/api/auth/me'); }
            catch (error) { if (error.status !== 401) throw error; this.user = null; }
            return this.user;
        },
        async logout() {
            await this.api('/api/auth/logout', { method: 'POST' });
            this.resetCsrf(); this.user = null; location.assign('/welcome.html');
        },
        async requireAdmin() {
            const user = await this.me();
            if (user?.role !== 'ADMIN') { location.replace('/welcome.html?login=1'); return null; }
            return user;
        }
    };
})();
