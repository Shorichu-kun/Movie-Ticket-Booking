'use strict';
/* Tiện ích dùng chung cho các trang admin giao diện neon.
   Cần load SAU session.js (dùng Cinema.me / Cinema.logout). */
(() => {
    const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const TABS = [
        ['movies.html', '🎬', 'Quản lý phim'],
        ['showtimes.html', '🗓', 'Suất chiếu'],
        ['rooms.html', '💺', 'Phòng & Ghế'],
        ['customers.html', '📊', 'Khách hàng & Thống kê']
    ];

    function toast(msg, type = 'ok') {
        let box = document.getElementById('toasts');
        if (!box) { box = document.createElement('div'); box.id = 'toasts'; box.className = 'n-toasts'; document.body.append(box); }
        const t = document.createElement('div');
        t.className = 'n-toast ' + (type === 'err' ? 'err' : '');
        t.textContent = msg;
        box.append(t);
        setTimeout(() => t.remove(), 3500);
    }

    function tiles(active, counts = {}) {
        const el = document.getElementById('tiles');
        if (!el) return;
        el.innerHTML = TABS.map(([href, icon, label]) =>
            `<a class="n-tile ${href === active ? 'on' : ''}" href="${href}"><span>${icon}</span><b>${label}</b><small>${esc(counts[href] ?? '')}</small></a>`).join('');
    }

    function side() {
        const el = document.getElementById('side');
        if (!el) return;
        const own = document.body.dataset.adminShell !== 'hub';
        el.innerHTML = `<div class="n-logo"><i>▶</i><span>NEON <b>CINEMA</b></span></div>
<div class="n-av" id="adAv">AD</div><h2 id="adName">Admin</h2>
<p class="n-sub" id="adminIdentity">Đang kiểm tra quyền truy cập…</p><span class="n-chip">Super Admin</span>
<div class="n-st">
  <div><b id="sideMovieCount">—</b><small>Phim</small></div>
  <div><b id="sideShowCount">—</b><small>Suất</small></div>
  <div><b id="sideCustCount">—</b><small>Khách</small></div>
</div>
<div class="n-kv"><span>Rạp</span><span>Neon Landmark 81</span></div>
<div class="n-kv"><span>Lưu trữ</span><span style="color:var(--n-gr)">data/*.json</span></div>
<div class="n-kv"><span>Phiên</span><span style="color:var(--n-cy)">Spring Security</span></div>
<a class="n-kv" href="movies.html"><span>Quản lý chính</span><span>→</span></a>
<a class="n-kv" href="welcome.html"><span>Trang khách hàng</span><span>↗</span></a>
<button class="n-btn g" id="adminLogout" style="width:100%;margin-top:14px">Đăng xuất</button>`;

        if (own) document.getElementById('adminLogout').onclick = () => Cinema.logout().catch(e => toast(e.message, 'err'));

        Cinema.me().then(u => {
            if (!u) return;
            const name = u.fullName || u.name || u.displayName || u.email || u.id || 'Admin';
            document.getElementById('adName').textContent = name;
            document.getElementById('adAv').textContent = String(name).trim().slice(0, 2).toUpperCase();
            const idn = document.getElementById('adminIdentity');
            if (own || idn.textContent.startsWith('Đang kiểm tra')) idn.textContent = u.email || u.id || '';
        }).catch(() => {});

        Promise.all([
            fetch('/api/movies').then(r => r.ok ? r.json() : []).catch(() => []),
            fetch('/api/showtimes').then(r => r.ok ? r.json() : []).catch(() => []),
            fetch('/api/customers').then(r => r.ok ? r.json() : []).catch(() => [])
        ]).then(([movies, shows, custs]) => {
            if (Array.isArray(movies)) document.getElementById('sideMovieCount').textContent = movies.length;
            if (Array.isArray(shows)) document.getElementById('sideShowCount').textContent = shows.length;
            if (Array.isArray(custs)) document.getElementById('sideCustCount').textContent = custs.length;
        });
    }

    window.App = { esc, toast, tiles, side };
    side();
    const t = document.getElementById('tiles');
    if (t) tiles(t.dataset.active || '');
})();