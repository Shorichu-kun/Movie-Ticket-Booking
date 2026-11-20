'use strict';
(() => {
    const $ = id => document.getElementById(id);
    const SLOTS = [10, 13, 16, 19, 22]; // giờ bắt đầu của từng cột, cột cuối kéo đến 24:00
    const DAYS = 7, DEFAULT_PRICE = 80000;
    const WD = ['CN', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    const MST = { NOW_SHOWING: 'Đang chiếu', COMING_SOON: 'Sắp chiếu', ENDED: 'Ngừng chiếu' };
    const pad = n => String(n).padStart(2, '0');
    const key = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const parse = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
    const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
    const iso = d => `${key(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const hhmm = s => (s || '').slice(11, 16) || '—';
    const mins = s => Number(s.slice(11, 13)) * 60 + Number(s.slice(14, 16));
    const label = d => `${WD[d.getDay()]}, ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;
    const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const money = v => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(v) || 0);
    const same = (a, b) => String(a).toLowerCase() === String(b).toLowerCase();
    const state = { movies: [], rooms: [], shows: [], week: parse(key(new Date())), day: key(new Date()), room: null, pick: null, detail: null };

    let csrfP;
    const csrf = () => csrfP ||= fetch('/api/auth/csrf', { credentials: 'same-origin' })
        .then(r => { if (!r.ok) throw new Error('Không thể khởi tạo phiên.'); return r.json(); })
        .catch(e => { csrfP = null; throw e; });
    async function api(path, opt = {}) {
        const method = (opt.method || 'GET').toUpperCase(), headers = new Headers(opt.headers || {});
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) { const t = await csrf(); headers.set(t.headerName, t.token); }
        const res = await fetch(path, { ...opt, headers, credentials: 'same-origin' });
        const raw = await res.text(); let data;
        try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
        if (!res.ok) throw new Error(typeof data === 'string' ? data : (data?.message || 'Yêu cầu chưa thành công.'));
        return data;
    }
    function toast(m, err = false) {
        const b = document.createElement('div'); b.className = 'toast' + (err ? ' err' : ''); b.textContent = m;
        document.body.append(b); setTimeout(() => b.remove(), 3600);
    }

    const movie = id => state.movies.find(m => same(m.id, id));
    const room = id => state.rooms.find(r => same(r.id, id));
    const slotOf = m => { for (let i = SLOTS.length - 1; i >= 0; i--) if (m >= SLOTS[i] * 60) return i; return -1; };
    const status = s => { const n = new Date(), a = new Date(s.startTime), b = new Date(s.endTime); return b < n ? ['Đã chiếu', 'ended'] : a <= n ? ['Đang chiếu', 'live'] : ['Active', 'active']; };
    const cardHtml = s => { const m = movie(s.movieId), [t, c] = status(s); return `<button type="button" class="card-show ${c}" data-show="${esc(s.id)}"><b>${esc(m?.title || s.movieId)}</b><small>${hhmm(s.startTime)} – ${hhmm(s.endTime)}</small><span class="tag ${c}">${t}</span></button>`; };

    function render() {
        $('dayTabs').innerHTML = Array.from({ length: DAYS }, (_, i) => { const d = addDays(state.week, i), k = key(d); return `<button type="button" class="tab${k === state.day ? ' active' : ''}" data-day="${k}">${label(d)}</button>`; }).join('');
        const day = state.shows.filter(s => String(s.startTime || '').slice(0, 10) === state.day).sort((a, b) => a.startTime.localeCompare(b.startTime));
        const outside = day.filter(s => slotOf(mins(s.startTime)) < 0).length;
        $('stats').textContent = `${label(parse(state.day))} · ${day.length} suất chiếu · ${state.rooms.length} phòng` + (outside ? ` · ${outside} suất ngoài khung giờ hiển thị` : '');
        const g = $('grid'); g.style.setProperty('--cols', SLOTS.length);
        if (!state.rooms.length) { g.innerHTML = '<p class="note">Chưa có phòng chiếu. Hãy tạo phòng ở mục Phòng & Ghế.</p>'; return; }
        g.innerHTML = '<div></div>' + SLOTS.map(h => `<div class="hour">${pad(h)}:00</div>`).join('') +
            state.rooms.map(r => `<div class="room">${esc(r.name || r.id)}</div>` + SLOTS.map((_, i) => {
                const list = day.filter(s => same(s.roomId, r.id) && slotOf(mins(s.startTime)) === i);
                const add = `data-add data-room="${esc(r.id)}" data-slot="${i}"`;
                return list.length
                    ? `<div class="cell">${list.map(cardHtml).join('')}<button type="button" class="more" ${add}>＋ Thêm</button></div>`
                    : `<button type="button" class="cell free" ${add}>＋ Gán phim</button>`;
            }).join('')).join('');
    }

    const openM = id => $(id).classList.add('open'), closeM = id => $(id).classList.remove('open');

    function openAssign(roomId, i) {
        if (!state.movies.length) return toast('Chưa có phim nào. Hãy thêm phim ở mục Quản lý phim.', true);
        state.room = roomId; state.pick = null;
        const end = (SLOTS[i + 1] ?? 24) * 60 - 1, t = $('assignTime');
        $('assignContext').textContent = `Phòng ${room(roomId)?.name || roomId} · ${label(parse(state.day))}`;
        t.min = `${pad(SLOTS[i])}:00`; t.max = `${pad(Math.floor(end / 60))}:${pad(end % 60)}`; t.value = t.min;
        $('movieSearch').value = ''; $('assignPrice').value = DEFAULT_PRICE; $('assignError').classList.remove('show');
        movieList(); preview(); openM('assignOverlay');
    }
    function movieList() {
        const q = $('movieSearch').value.trim().toLocaleLowerCase('vi');
        $('movieList').innerHTML = state.movies.filter(m => !q || `${m.title} ${m.genre || ''}`.toLocaleLowerCase('vi').includes(q)).map(m => {
            const st = String(m.status).toUpperCase(), off = st === 'ENDED';
            return `<label class="movie-opt${off ? ' off' : ''}"><input type="radio" name="movie" value="${esc(m.id)}"${off ? ' disabled' : ''}${same(state.pick ?? '', m.id) ? ' checked' : ''}><span><b>${esc(m.title || m.id)}</b><small>${esc(m.genre || '')} · ${Number(m.duration) || '?'} phút</small></span><span class="tag ${off ? 'ended' : 'active'}">${MST[st] || esc(m.status || '')}</span></label>`;
        }).join('') || '<p class="note">Không tìm thấy phim.</p>';
    }
    function slotInfo() {
        const m = movie(state.pick), t = $('assignTime').value;
        if (!m || !t) return null;
        const start = new Date(`${state.day}T${t}`);
        return { m, start, end: new Date(start.getTime() + (Number(m.duration) || 0) * 60000) };
    }
    const clash = x => state.shows.find(s => same(s.roomId, state.room) && x.start < new Date(s.endTime) && x.end > new Date(s.startTime));
    function preview() {
        const x = slotInfo(), p = $('preview');
        if (!x) { p.className = 'preview'; p.textContent = 'Chọn phim và giờ bắt đầu để xem giờ kết thúc.'; return; }
        const c = clash(x);
        p.className = 'preview ' + (c ? 'bad' : 'ok');
        p.textContent = c ? `Trùng lịch với suất ${c.id} (${hhmm(c.startTime)} – ${hhmm(c.endTime)}).`
            : `Kết thúc dự kiến lúc ${pad(x.end.getHours())}:${pad(x.end.getMinutes())}${key(x.end) !== state.day ? ' (hôm sau)' : ''}.`;
    }

    $('movieList').addEventListener('change', e => { state.pick = e.target.value; preview(); });
    $('movieSearch').addEventListener('input', movieList);
    $('assignTime').addEventListener('input', preview);
    $('assignForm').addEventListener('submit', async e => {
        e.preventDefault();
        const x = slotInfo(), err = $('assignError'), btn = $('assignSave');
        const fail = m => { err.textContent = m; err.classList.add('show'); };
        err.classList.remove('show');
        if (!x) return fail('Vui lòng chọn một phim.');
        if (clash(x)) return fail($('preview').textContent);
        const nums = state.shows.map(s => /^ST(\d+)$/i.exec(s.id || '')).filter(Boolean).map(m => +m[1]);
        const show = { id: 'ST' + String(Math.max(0, ...nums) + 1).padStart(3, '0'), movieId: x.m.id, roomId: state.room, startTime: iso(x.start), price: Number($('assignPrice').value) };
        btn.disabled = true; btn.textContent = 'Đang lưu…';
        try {
            const msg = await api('/api/showtimes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(show) });
            toast(typeof msg === 'string' ? msg : 'Đã gán phim.'); closeM('assignOverlay'); await load();
        } catch (er) { fail(er.message); }
        finally { btn.disabled = false; btn.textContent = 'Gán phim'; }
    });

    function openDetail(id) {
        const s = state.shows.find(x => x.id === id); if (!s) return;
        state.detail = id;
        const when = v => v ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(v)) : '—';
        $('detailBody').innerHTML = [['Mã suất', s.id], ['Phim', movie(s.movieId)?.title || s.movieId], ['Phòng', room(s.roomId)?.name || s.roomId], ['Bắt đầu', when(s.startTime)], ['Kết thúc', when(s.endTime)], ['Giá vé', money(s.price)], ['Trạng thái', status(s)[0]]]
            .map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('');
        openM('detailOverlay');
    }
    $('detailDelete').addEventListener('click', async () => {
        if (!state.detail || !confirm(`Bạn muốn hủy suất chiếu ${state.detail}?`)) return;
        try { await api('/api/showtimes/' + encodeURIComponent(state.detail), { method: 'DELETE' }); toast('Đã hủy suất chiếu.'); closeM('detailOverlay'); await load(); }
        catch (er) { toast(er.message, true); }
    });

    document.addEventListener('click', e => {
        const d = e.target.closest('[data-day]'), a = e.target.closest('[data-add]'), s = e.target.closest('[data-show]');
        if (d) { state.day = d.dataset.day; render(); }
        else if (a) openAssign(a.dataset.room, Number(a.dataset.slot));
        else if (s) openDetail(s.dataset.show);
    });
    [['assignCancel', 'assignOverlay'], ['detailClose', 'detailOverlay']].forEach(([b, o]) => $(b).addEventListener('click', () => closeM(o)));
    document.querySelectorAll('.overlay').forEach(o => o.addEventListener('click', e => { if (e.target === o) o.classList.remove('open'); }));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') document.querySelectorAll('.overlay').forEach(o => o.classList.remove('open')); });
    const goto = d => { state.week = d; state.day = key(d); render(); };
    $('prevWeek').onclick = () => goto(addDays(state.week, -DAYS));
    $('nextWeek').onclick = () => goto(addDays(state.week, DAYS));
    $('today').onclick = () => goto(parse(key(new Date())));

    async function load() {
        $('error').classList.remove('show');
        try {
            const [m, r, s] = await Promise.all([api('/api/movies'), api('/api/rooms'), api('/api/showtimes')]);
            state.movies = Array.isArray(m) ? m : []; state.rooms = Array.isArray(r) ? r : []; state.shows = Array.isArray(s) ? s : [];
            $('movieCount').textContent = `${state.movies.length} phim`; $('roomCount').textContent = `${state.rooms.length} phòng`; $('showCount').textContent = `${state.shows.length} suất`;
            render();
        } catch (er) {
            $('error').textContent = er.message + ' Hãy kiểm tra server và đăng nhập quản trị.'; $('error').classList.add('show');
            $('stats').textContent = 'Không tải được dữ liệu.';
        }
    }

    $('logout').addEventListener('click', async () => {
        try { await api('/api/auth/logout', { method: 'POST' }); location.assign('/welcome.html'); } catch (er) { toast(er.message, true); }
    });
    const deny = () => { document.body.innerHTML = '<main style="min-height:100vh;display:grid;place-items:center;text-align:center;padding:24px"><section><h1>Cần đăng nhập quyền quản trị</h1><p>Đăng nhập trong ứng dụng để quản lý suất chiếu.</p><a href="/static/html/welcome.html?login=1">Đến trang đăng nhập</a></section></main>'; };
    (async () => {
        try {
            const u = await api('/api/auth/me');
            if (u?.role !== 'ADMIN') return deny();
            const n = u.fullName || u.name || u.displayName || u.email || 'Quản trị viên';
            $('adminName').textContent = n; $('adminEmail').textContent = u.email || u.id || 'Quản trị viên'; $('avatar').textContent = n.trim().slice(0, 2).toUpperCase();
            await load();
        } catch { deny(); }
    })();
})();