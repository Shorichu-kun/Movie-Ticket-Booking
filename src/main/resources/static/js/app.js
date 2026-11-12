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

    // 4 thẻ bo góc chọn mục; counts = { 'movies.html': '48 phim', ... }
    function tiles(active, counts = {}) {
        const el = document.getElementById('tiles');
        if (!el) return;
        el.innerHTML = TABS.map(([href, icon, label]) =>
            `<a class="n-tile ${href === active ? 'on' : ''}" href="${href}"><span>${icon}</span><b>${label}</b><small>${esc(counts[href] ?? '')}</small></a>`).join('');
    }

    // Thẻ thông tin admin bên trái. Trang có body[data-admin-shell="hub"] (admin.html) giữ
    // nguyên việc đăng xuất / hiện danh tính cho admin.js, các trang khác tự xử lý ở đây.
    function side() {
        const el = document.getElementById('side');
        if (!el) return;
        const own = document.body.dataset.adminShell !== 'hub';
        el.innerHTML = `<div class="n-logo"><i>▶</i><span>N6 <b>CINEMA</b></span></div>
<div class="n-av" id="adAv">AD</div><h2 id="adName">Admin</h2>
<p class="n-sub" id="adminIdentity">Đang kiểm tra quyền truy cập…</p><span class="n-chip">ADMIN</span>
<a class="n-kv" href="admin.html"><span>Tổng quan</span><span>→</span></a>
<a class="n-kv" href="welcome.html"><span>Trang khách hàng</span><span>↗</span></a>
<button class="n-btn g" id="adminLogout" style="width:100%;margin-top:14px">Đăng xuất</button>`;
        if (own) document.getElementById('adminLogout').onclick = () => Cinema.logout().catch(e => toast(e.message, 'err'));
        Cinema.me().then(u => {
            if (!u) return;
            // Tên hiển thị: chưa biết chắc trường nào của /api/auth/me – thử lần lượt
            const name = u.fullName || u.name || u.displayName || u.email || u.id || 'Admin';
            document.getElementById('adName').textContent = name;
            document.getElementById('adAv').textContent = String(name).trim().slice(0, 2).toUpperCase();
            const idn = document.getElementById('adminIdentity');
            if (own || idn.textContent.startsWith('Đang kiểm tra')) idn.textContent = u.email || u.id || '';
        }).catch(() => {});
    }

    window.App = { esc, toast, tiles, side };
    side();
    const t = document.getElementById('tiles');
    if (t) tiles(t.dataset.active || '');
})();