'use strict';
/* Quản lý phim: CRUD qua /api/movies, ảnh bìa qua POST /api/movies/poster (multipart, field "file") → { "url": "/posters/xxx.jpg" } */
(() => {
    const { esc, toast } = App, $ = s => document.querySelector(s);
    const ST = { NOW_SHOWING: ['Đang chiếu', 'now'], COMING_SOON: ['Sắp chiếu', 'soon'], ENDED: ['Ngừng chiếu', 'end'] };
    const RATE = ['P', 'T13', 'T16', 'T18'], PS = 8;
    let movies = [], tab = 'ALL', q = '', page = 1, cur = null, file = null, preview = '', poster = '';

    async function load() {
        try { const d = await Cinema.api('/api/movies'); movies = Array.isArray(d) ? d : []; }
        catch (e) { toast(e.message, 'err'); }
        render();
    }

    function render() {
        const count = s => movies.filter(m => s === 'ALL' || m.status === s).length;
        $('#tabs').innerHTML = [['ALL', 'Tất cả'], ...Object.entries(ST).map(([k, v]) => [k, v[0]])]
            .map(([k, l]) => `<button class="n-pill ${k === tab ? 'on' : ''}" data-t="${k}">${l} <small>${count(k)}</small></button>`).join('');
        const list = movies.filter(m => (tab === 'ALL' || m.status === tab) && (m.title || '').toLowerCase().includes(q.toLowerCase()));
        const pages = Math.max(1, Math.ceil(list.length / PS)); page = Math.min(page, pages);
        $('#rows').innerHTML = list.slice((page - 1) * PS, page * PS).map(m => {
            const s = ST[m.status] || [m.status || '—', 'end'];
            return `<tr><td>${m.poster ? `<img class="n-th" src="${esc(m.poster)}" alt="">` : '<span class="n-th">🎞</span>'}</td>
<td><b>${esc(m.title)}</b><div class="n-sub">${esc(m.id)}</div></td><td>${esc(m.duration)} phút</td><td>${esc(m.genre)}</td><td>${esc(m.ageRating || '—')}</td>
<td><span class="n-tag ${s[1]}">${s[0]}</span></td>
<td class="r"><button class="n-btn g sm" data-e="${esc(m.id)}">Sửa</button> <button class="n-btn d sm" data-d="${esc(m.id)}">Xóa</button></td></tr>`;
        }).join('') || '<tr><td colspan="7" class="n-sub">Không có phim phù hợp</td></tr>';
        $('#pager').innerHTML = `<button class="n-btn g sm" data-p="-1" ${page <= 1 ? 'disabled' : ''}>←</button><span class="n-sub">Trang ${page}/${pages} · ${list.length} phim</span><button class="n-btn g sm" data-p="1" ${page >= pages ? 'disabled' : ''}>→</button>`;
        App.tiles('movies.html', { 'movies.html': movies.length + ' phim' });
    }

    /* ---- modal ---- */
    const ov = $('#ov'), md = $('#md');
    const open = html => { md.innerHTML = html; ov.classList.add('on'); };
    function close() {
        ov.classList.remove('on');
        if (preview.startsWith('blob:')) URL.revokeObjectURL(preview);
        file = null; preview = ''; poster = '';
    }

    function edit(id) {
        const isNew = !id;
        cur = movies.find(m => m.id === id) || { id: '', title: '', duration: 120, genre: '', ageRating: 'T13', status: 'NOW_SHOWING', poster: '' };
        file = null; poster = cur.poster || ''; preview = poster;
        const genres = [...new Set(movies.map(m => m.genre).filter(Boolean))];
        open(`<h3>${isNew ? 'Thêm phim' : 'Sửa phim'}</h3><form id="mf">
<label>Ảnh bìa</label>
<div class="n-dz" id="dz"><img id="pv" ${preview ? `src="${esc(preview)}"` : 'hidden'} alt=""><span>Kéo thả ảnh vào đây hoặc bấm để chọn<br><small>JPG, PNG, WEBP · tự thu nhỏ trước khi tải lên</small></span></div>
<input type="file" id="fi" accept="image/*" hidden>
<button type="button" class="n-btn d sm" id="rm" style="margin-top:6px" ${preview ? '' : 'hidden'}>Gỡ ảnh</button>
<div class="n-two"><div><label>Mã phim</label><input id="f0" value="${esc(cur.id)}" ${isNew ? 'placeholder="Tự tạo nếu để trống"' : 'readonly'}></div>
<div><label>Thời lượng (phút)</label><input id="f2" type="number" min="1" max="600" required value="${esc(cur.duration)}"></div></div>
<label>Tên phim</label><input id="f1" required value="${esc(cur.title)}">
<div class="n-two"><div><label>Thể loại</label><input id="f3" list="gl" required value="${esc(cur.genre)}"><datalist id="gl">${genres.map(g => `<option value="${esc(g)}">`).join('')}</datalist></div>
<div><label>Độ tuổi</label><select id="f4">${RATE.map(r => `<option ${r === cur.ageRating ? 'selected' : ''}>${r}</option>`).join('')}</select></div></div>
<label>Trạng thái</label><select id="f5">${Object.entries(ST).map(([k, v]) => `<option value="${k}" ${k === cur.status ? 'selected' : ''}>${v[0]}</option>`).join('')}</select>
<div class="n-ft"><button type="button" class="n-btn g" data-x>Hủy</button><button class="n-btn" id="sv">Lưu</button></div></form>`);
        const dz = $('#dz');
        dz.onclick = () => $('#fi').click();
        $('#fi').onchange = e => pick(e.target.files[0]);
        ['dragenter', 'dragover'].forEach(n => dz.addEventListener(n, e => { e.preventDefault(); dz.classList.add('hv'); }));
        ['dragleave', 'drop'].forEach(n => dz.addEventListener(n, e => { e.preventDefault(); dz.classList.remove('hv'); }));
        dz.addEventListener('drop', e => pick(e.dataTransfer.files[0]));
        $('#rm').onclick = () => { file = null; poster = ''; preview = ''; $('#pv').hidden = true; $('#rm').hidden = true; };
        $('#mf').onsubmit = e => { e.preventDefault(); save(isNew); };
    }

    function pick(f) {
        if (!f) return;
        if (!f.type.startsWith('image/')) return toast('Vui lòng chọn file ảnh', 'err');
        if (f.size > 10 * 1024 * 1024) return toast('Ảnh tối đa 10 MB', 'err');
        if (preview.startsWith('blob:')) URL.revokeObjectURL(preview);
        file = f; preview = URL.createObjectURL(f);
        $('#pv').src = preview; $('#pv').hidden = false; $('#rm').hidden = false;
    }

    // Thu nhỏ ≤ 800px, JPEG → tránh vượt giới hạn multipart mặc định 1 MB của Spring
    const shrink = f => new Promise((ok, no) => {
        const im = new Image(), u = URL.createObjectURL(f);
        im.onload = () => {
            const k = Math.min(1, 800 / im.width), c = document.createElement('canvas');
            c.width = Math.round(im.width * k); c.height = Math.round(im.height * k);
            c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); URL.revokeObjectURL(u);
            c.toBlob(b => b ? ok(b) : no(new Error('Không xử lý được ảnh')), 'image/jpeg', .85);
        };
        im.onerror = () => no(new Error('File không phải ảnh hợp lệ'));
        im.src = u;
    });

    async function save(isNew) {
        const btn = $('#sv'); btn.disabled = true;
        try {
            const id = isNew ? ($('#f0').value.trim() || 'M' + Date.now().toString(36).toUpperCase()) : cur.id;
            if (isNew && movies.some(m => m.id === id)) throw new Error('Mã phim đã tồn tại');
            let url = poster;
            if (file) {
                const fd = new FormData(); fd.append('file', await shrink(file), 'poster.jpg');
                const r = await fetch('/api/movies/poster', { method: 'POST', body: fd });   // fetch đã được session.js gắn CSRF
                if (!r.ok) throw new Error('Tải ảnh lên thất bại (' + r.status + ')');
                url = (await r.json()).url;
            }
            const body = { id, title: $('#f1').value.trim(), duration: +$('#f2').value, genre: $('#f3').value.trim(), ageRating: $('#f4').value, status: $('#f5').value, poster: url };
            await Cinema.api(isNew ? '/api/movies' : '/api/movies/' + encodeURIComponent(id), { method: isNew ? 'POST' : 'PUT', body: JSON.stringify(body) });
            toast(isNew ? 'Đã thêm phim' : 'Đã cập nhật phim'); close(); await load();
        } catch (e) { toast(e.message, 'err'); btn.disabled = false; }
    }

    function del(id) {
        const m = movies.find(x => x.id === id);
        open(`<h3>Xóa phim?</h3><p class="n-sub">“${esc(m?.title)}” sẽ bị xóa khỏi hệ thống.</p><div class="n-ft"><button class="n-btn g" data-x>Hủy</button><button class="n-btn d" id="ok">Xóa</button></div>`);
        $('#ok').onclick = async () => {
            try { await Cinema.api('/api/movies/' + encodeURIComponent(id), { method: 'DELETE' }); toast('Đã xóa phim'); close(); await load(); }
            catch (e) { toast(e.message, 'err'); }
        };
    }

    /* ---- sự kiện ---- */
    document.addEventListener('click', e => {
        const b = e.target.closest('[data-t],[data-e],[data-d],[data-p],[data-x]');
        if (e.target === ov) return close();
        if (!b) return;
        if (b.dataset.t) { tab = b.dataset.t; page = 1; render(); }
        else if (b.dataset.e) edit(b.dataset.e);
        else if (b.dataset.d) del(b.dataset.d);
        else if (b.dataset.p) { page += +b.dataset.p; render(); }
        else if ('x' in b.dataset) close();
    });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });
    $('#add').onclick = () => edit('');
    $('#q').oninput = e => { q = e.target.value; page = 1; render(); };
    load();
})();