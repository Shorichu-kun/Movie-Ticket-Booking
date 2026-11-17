'use strict';
(() => {
    const $ = id => document.getElementById(id);
    const state = { movies: [], rooms: [], shows: [] };
    const demoMode = location.protocol === 'file:';
    const demoKey = 'neon-cinema-showtimes-demo-v1';
    let csrfPromise;

    const csrf = () => csrfPromise ||= fetch('/api/auth/csrf', { credentials: 'same-origin' })
        .then(async response => { if (!response.ok) throw new Error('Không thể khởi tạo phiên.'); return response.json(); })
        .catch(error => { csrfPromise = null; throw error; });

    async function api(path, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            const token = await csrf();
            headers.set(token.headerName, token.token);
        }
        const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
        const raw = await response.text();
        let data;
        try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
        if (!response.ok) throw new Error(typeof data === 'string' ? data : (data?.message || 'Yêu cầu chưa thành công.'));
        return data;
    }

    const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
    const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value) || 0);
    const localDateTime = value => {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.valueOf()) ? escape(value) : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date);
    };
    const today = () => {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    };
    function demoSeed() {
        const day = today();
        const movies = [
            { id: 'MV001', title: 'Event Horizon: Neon', duration: 148, genre: 'Sci-fi' },
            { id: 'MV002', title: 'Night Protocol', duration: 126, genre: 'Hành động' },
            { id: 'MV003', title: 'Pixel Panda', duration: 105, genre: 'Hoạt hình' },
            { id: 'MV004', title: 'Crimson Orbit', duration: 132, genre: 'Phiêu lưu' },
            { id: 'MV005', title: 'Silent Opera', duration: 119, genre: 'Tâm lý' }
        ];
        const rooms = [{ id: 'R1', name: 'Neon 1' }, { id: 'R2', name: 'Neon 2' }, { id: 'R3', name: 'Neon 3' }];
        const shows = [
            { id: 'ST001', movieId: 'MV001', roomId: 'R1', startTime: `${day}T18:00`, endTime: `${day}T20:28`, price: 95000 },
            { id: 'ST002', movieId: 'MV002', roomId: 'R2', startTime: `${day}T18:30`, endTime: `${day}T20:36`, price: 85000 },
            { id: 'ST003', movieId: 'MV003', roomId: 'R3', startTime: `${day}T19:00`, endTime: `${day}T20:45`, price: 75000 },
            { id: 'ST004', movieId: 'MV004', roomId: 'R1', startTime: `${day}T21:00`, endTime: `${day}T23:12`, price: 95000 },
            { id: 'ST005', movieId: 'MV005', roomId: 'R2', startTime: `${day}T21:15`, endTime: `${day}T23:14`, price: 80000 }
        ];
        return { movies, rooms, shows };
    }
    function toast(message, error = false) {
        const box = document.createElement('div');
        box.className = `toast${error ? ' err' : ''}`;
        box.textContent = message;
        document.body.append(box);
        setTimeout(() => box.remove(), 3600);
    }
    function showError(message) { $('error').textContent = message; $('error').classList.add('show'); }
    function persistDemo() { localStorage.setItem(demoKey, JSON.stringify({ movies: state.movies, rooms: state.rooms, shows: state.shows })); }
    function fillSelects() {
        const movieOptions = state.movies.map(movie => `<option value="${escape(movie.id)}">${escape(movie.title || movie.id)}</option>`).join('');
        $('filterMovie').innerHTML = '<option value="">Tất cả phim</option>' + movieOptions;
        $('form').elements.movieId.innerHTML = movieOptions || '<option value="">Chưa có phim</option>';
        $('form').elements.roomId.innerHTML = state.rooms.map(room => `<option value="${escape(room.id)}">${escape(room.name || room.id)}</option>`).join('') || '<option value="">Chưa có phòng</option>';
        $('movieCount').textContent = `${state.movies.length} phim`;
        $('roomCount').textContent = `${state.rooms.length} phòng`;
    }
    function render() {
        const moviesById = new Map(state.movies.map(movie => [String(movie.id).toLowerCase(), movie]));
        const roomsById = new Map(state.rooms.map(room => [String(room.id).toLowerCase(), room]));
        const query = $('search').value.trim().toLocaleLowerCase('vi');
        const movieId = $('filterMovie').value;
        const date = $('filterDate').value;
        const list = state.shows.filter(show => {
            const movie = moviesById.get(String(show.movieId).toLowerCase());
            const room = roomsById.get(String(show.roomId).toLowerCase());
            const matchesText = [show.id, show.movieId, show.roomId, movie?.title, room?.name].some(value => String(value || '').toLocaleLowerCase('vi').includes(query));
            return (!movieId || show.movieId === movieId) && (!date || String(show.startTime || '').startsWith(date)) && (!query || matchesText);
        }).sort((a, b) => String(a.startTime || '').localeCompare(String(b.startTime || '')));
        $('showCount').textContent = `${state.shows.length} suất`;
        $('stats').textContent = `Có ${state.shows.length} suất chiếu · ${list.length} kết quả phù hợp`;
        $('resultCount').textContent = `Hiển thị ${list.length} / ${state.shows.length} suất`;
        $('rows').innerHTML = list.map(show => {
            const movie = moviesById.get(String(show.movieId).toLowerCase());
            const room = roomsById.get(String(show.roomId).toLowerCase());
            const started = show.startTime && new Date(show.startTime) < new Date();
            const tag = started ? ['Đã bắt đầu', 'ended'] : ['Sắp chiếu', 'upcoming'];
            return `<tr><td><span class="title">${escape(show.id)}</span></td><td><span class="title">${escape(movie?.title || show.movieId || 'Không rõ phim')}</span><div class="subline">${escape(movie?.genre || 'Suất chiếu')}</div></td><td>${escape(room?.name || show.roomId || '—')}</td><td>${localDateTime(show.startTime)}</td><td>${localDateTime(show.endTime)}</td><td>${money(show.price)}</td><td><span class="tag ${tag[1]}">${tag[0]}</span></td><td><button class="btn danger small" data-delete="${escape(show.id)}">Xóa</button></td></tr>`;
        }).join('') || '<tr><td colspan="8" class="empty">Không tìm thấy suất chiếu nào.</td></tr>';
        $('rows').querySelectorAll('[data-delete]').forEach(button => button.addEventListener('click', () => removeShow(button.dataset.delete)));
    }
    async function load() {
        $('error').classList.remove('show');
        $('rows').innerHTML = '<tr><td colspan="8" class="empty">Đang tải dữ liệu…</td></tr>';
        try {
            if (demoMode) {
                const saved = localStorage.getItem(demoKey);
                const demo = saved ? JSON.parse(saved) : demoSeed();
                state.movies = demo.movies || []; state.rooms = demo.rooms || []; state.shows = demo.shows || [];
                if (!saved) persistDemo();
            } else {
                const [movies, rooms, shows] = await Promise.all([api('/api/movies'), api('/api/rooms'), api('/api/showtimes')]);
                state.movies = Array.isArray(movies) ? movies : [];
                state.rooms = Array.isArray(rooms) ? rooms : [];
                state.shows = Array.isArray(shows) ? shows : [];
            }
            fillSelects(); render();
        } catch (error) {
            showError(error.message + ' Hãy kiểm tra server và đăng nhập quản trị.');
            $('stats').textContent = 'Không tải được dữ liệu.';
            $('rows').innerHTML = '<tr><td colspan="8" class="empty">Không thể kết nối dữ liệu.</td></tr>';
        }
    }
    function openModal() {
        if (!state.movies.length || !state.rooms.length) { toast('Cần có ít nhất một phim và một phòng chiếu trước.', true); return; }
        $('formError').classList.remove('show'); $('overlay').classList.add('open'); $('form').elements.id.focus();
    }
    function closeModal() { $('overlay').classList.remove('open'); }
    async function removeShow(id) {
        if (!confirm(`Bạn muốn xóa suất chiếu ${id}?`)) return;
        try {
            if (demoMode) { state.shows = state.shows.filter(show => show.id !== id); persistDemo(); }
            else await api('/api/showtimes/' + encodeURIComponent(id), { method: 'DELETE' });
            toast('Đã xóa suất chiếu.'); await load();
        } catch (error) { toast(error.message, true); }
    }

    $('form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget, button = $('save');
        button.disabled = true; button.textContent = 'Đang lưu…'; $('formError').classList.remove('show');
        const show = { id: form.elements.id.value.trim(), movieId: form.elements.movieId.value, roomId: form.elements.roomId.value, startTime: form.elements.startTime.value, price: Number(form.elements.price.value) };
        try {
            if (demoMode) {
                if (state.shows.some(existing => existing.id.toLowerCase() === show.id.toLowerCase())) throw new Error('Mã suất chiếu đã tồn tại.');
                const movie = state.movies.find(item => item.id === show.movieId);
                if (!movie) throw new Error('Vui lòng chọn phim.');
                const end = new Date(show.startTime); end.setMinutes(end.getMinutes() + Number(movie.duration || 120));
                const pad = n => String(n).padStart(2, '0');
                show.endTime = `${end.getFullYear()}-${pad(end.getMonth()+1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}`;
                state.shows.push(show); persistDemo();
            } else {
                const message = await api('/api/showtimes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(show) });
                toast(typeof message === 'string' ? message : 'Đã tạo suất chiếu.');
            }
            closeModal(); form.reset(); form.elements.price.value = '80000';
            if (demoMode) toast('Đã thêm suất chiếu vào bản demo trên thiết bị này.');
            await load();
        } catch (error) { $('formError').textContent = error.message; $('formError').classList.add('show'); }
        finally { button.disabled = false; button.textContent = 'Lưu suất chiếu'; }
    });

    $('addShowtime').addEventListener('click', openModal);
    $('cancel').addEventListener('click', closeModal);
    $('overlay').addEventListener('click', event => { if (event.target === $('overlay')) closeModal(); });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });
    $('refresh').addEventListener('click', load);
    $('reset').addEventListener('click', () => { $('search').value = ''; $('filterMovie').value = ''; $('filterDate').value = ''; render(); });
    ['search', 'filterMovie', 'filterDate'].forEach(id => $(id).addEventListener(id === 'search' ? 'input' : 'change', render));
    $('logout').addEventListener('click', async () => {
        if (demoMode) { toast('Đang ở chế độ xem thử, không có phiên đăng nhập để đăng xuất.'); return; }
        try { await api('/api/auth/logout', { method: 'POST' }); location.assign('/welcome.html'); }
        catch (error) { toast(error.message, true); }
    });

    if (demoMode) {
        $('adminName').textContent = 'Bản xem thử';
        $('adminEmail').textContent = 'Dữ liệu mẫu · lưu trên thiết bị này';
        $('avatar').textContent = 'DE';
        $('adminChip').textContent = 'DEMO';
        $('logout').hidden = true;
        load();
    } else {
        (async () => {
            try {
                const user = await api('/api/auth/me');
                if (user?.role !== 'ADMIN') { document.body.innerHTML = '<main style="min-height:100vh;display:grid;place-items:center;text-align:center;padding:24px"><section><h1>Cần đăng nhập quyền quản trị</h1><p>Đăng nhập trong ứng dụng để quản lý suất chiếu.</p><a href="/static/html/welcome.html?login=1">Đến trang đăng nhập</a></section></main>'; return; }
                const name = user.fullName || user.name || user.displayName || user.email || 'Quản trị viên';
                $('adminName').textContent = name; $('adminEmail').textContent = user.email || user.id || 'Quản trị viên'; $('avatar').textContent = name.trim().slice(0, 2).toUpperCase();
                await load();
            } catch { document.body.innerHTML = '<main style="min-height:100vh;display:grid;place-items:center;text-align:center;padding:24px"><section><h1>Cần đăng nhập quyền quản trị</h1><p>Đăng nhập trong ứng dụng để quản lý suất chiếu.</p><a href="/static/html/welcome.html?login=1">Đến trang đăng nhập</a></section></main>'; }
        })();
    }
})();