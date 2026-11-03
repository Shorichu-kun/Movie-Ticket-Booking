'use strict';
const $ = id => document.getElementById(id);
const state = { movies: [], showtimes: [], rooms: [], filter: 'NOW_SHOWING', authMode: 'login', selected: new Set(), seats: [], movie: null, quoteVersion: 0, seatVersion: 0, bookingBusy: false, pendingBooking: null };
const types = { NORMAL: 'Khách thường', STUDENT: 'Sinh viên', VIP: 'Thành viên VIP' };
const labels = { PENDING: 'Chờ thanh toán tại quầy', SUCCESS: 'Đã thanh toán', FAILED: 'Đã hủy', REFUNDED: 'Đã hoàn tiền' };
const el = (tag, text, cls = '') => { const node = document.createElement(tag); node.textContent = text; node.className = cls; return node; };
const body = value => JSON.stringify(value);
const dateOf = value => new Date(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : value.replace(' ', 'T') + '+07:00');
const formatTime = value => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }).format(dateOf(value));
const roomName = id => state.rooms.find(room => room.id === id)?.name || id;
const futureShows = movieId => state.showtimes.filter(show => show.movieId === movieId && dateOf(show.startTime) > new Date()).sort((a, b) => dateOf(a.startTime) - dateOf(b.startTime));
let toastTimer;
function toast(message) { $('toast').textContent = message; $('toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').hidden = true, 6000); }
function errorAt(id, message) { $(id).textContent = message; $(id).hidden = !message; }
function option(value, text) { const node = el('option', text); node.value = value; return node; }

async function loadCatalogue() {
    $('catalogueStatus').hidden = false; $('catalogueStatus').textContent = 'Đang tải danh sách phim…'; $('reloadCatalogue').hidden = true;
    try {
        [state.movies, state.showtimes, state.rooms] = await Promise.all(['/api/movies', '/api/showtimes', '/api/rooms'].map(path => Cinema.api(path)));
        renderMovies();
        const oldMovie = $('quickMovie').value;
        $('quickMovie').replaceChildren(option('', 'Chọn bộ phim bạn thích'), ...state.movies.filter(movie => movie.status === 'NOW_SHOWING').map(movie => option(movie.id, movie.title)));
        $('quickMovie').value = oldMovie; updateQuickShows();
    } catch (error) { $('catalogueStatus').textContent = 'Chưa tải được phim và lịch chiếu. ' + error.message; $('reloadCatalogue').hidden = false; }
}
function renderMovies() {
    const query = $('movieSearch').value.trim().toLocaleLowerCase('vi');
    $('nowCount').textContent = state.movies.filter(movie => movie.status === 'NOW_SHOWING').length;
    $('soonCount').textContent = state.movies.filter(movie => movie.status === 'COMING_SOON').length;
    const movies = state.movies.filter(movie => movie.status === state.filter && `${movie.title} ${movie.genre}`.toLocaleLowerCase('vi').includes(query));
    $('movieGrid').replaceChildren(); $('catalogueStatus').hidden = movies.length > 0;
    $('catalogueStatus').textContent = query ? 'Không tìm thấy phim phù hợp. Thử một tên phim hoặc thể loại khác.' : 'Chưa có phim trong mục này. Lịch phim mới sẽ xuất hiện khi rạp cập nhật.';
    movies.forEach((movie, index) => {
        const card = el('article', '', 'movie-card');
        const poster = el('div', '', 'poster'); poster.dataset.style = index % 4; poster.setAttribute('aria-hidden', 'true');
        poster.append(el('span', movie.status === 'NOW_SHOWING' ? 'ĐANG CHIẾU' : 'SẮP RA MẮT', 'movie-tag'), el('span', String(index + 1).padStart(2, '0'), 'poster-number'), el('div', movie.title, 'poster-title'), el('small', 'N6 CINEMA · BIG SCREEN'));
        const action = el('button', movie.status === 'COMING_SOON' ? 'Chưa mở bán' : 'Lịch chiếu & đặt vé ↗');
        action.disabled = movie.status === 'COMING_SOON'; action.addEventListener('click', () => openBooking(movie.id));
        card.append(poster, el('h3', movie.title), el('p', `${movie.genre} · ${movie.duration} phút`), action); $('movieGrid').append(card);
    });
}
function updateQuickShows() {
    const shows = futureShows($('quickMovie').value);
    $('quickShowtime').replaceChildren(option('', shows.length ? 'Chọn suất chiếu' : 'Chưa có suất chiếu'), ...shows.map(show => option(show.id, `${formatTime(show.startTime)} · ${roomName(show.roomId)}`)));
}
function renderSession() {
    const user = Cinema.user, admin = user?.role === 'ADMIN';
    $('loginButton').hidden = !!user; $('registerButton').hidden = !!user;
    $('accountButton').hidden = !user || admin; $('ticketsButton').hidden = !user || admin;
    $('adminLink').hidden = !admin; $('logoutButton').hidden = !user;
    $('accountButton').textContent = user?.customer?.fullName?.split(' ').pop() || 'Tài khoản';
    $('joinButton').textContent = admin ? 'Mở trang quản trị ↗' : user ? 'Hồ sơ thành viên ↗' : 'Tham gia cùng N6 ↗';
}
function setAuthMode(mode) {
    state.authMode = mode; const register = mode === 'register';
    $('registrationFields').hidden = !register; $('confirmPasswordLabel').hidden = !register;
    $('registrationFields').querySelectorAll('input,select').forEach(input => input.disabled = !register);
    ['fullName', 'phone'].forEach(name => $('authForm').elements[name].required = register);
    $('authForm').elements.confirmPassword.required = register; $('authForm').elements.confirmPassword.disabled = !register;
    $('password').minLength = register ? 8 : 1; $('password').autocomplete = register ? 'new-password' : 'current-password';
    $('authTitle').textContent = register ? 'Bắt đầu một cuộc hẹn.' : 'Rất vui gặp lại bạn.';
    $('authSubtitle').textContent = register ? 'Tạo tài khoản để chọn ghế và nhận ưu đãi thành viên.' : 'Đăng nhập để đặt vé và xem lịch sử của bạn.';
    $('authSubmit').textContent = register ? 'Tạo tài khoản →' : 'Đăng nhập →';
    $('loginTab').classList.toggle('active', !register); $('registerTab').classList.toggle('active', register);
    errorAt('authError', ''); updateRegistrationType();
}
function openAuth(mode = 'login') { $('authForm').reset(); setAuthMode(mode); $('authDialog').showModal(); }
function updateRegistrationType() {
    const student = state.authMode === 'register' && $('registrationType').value === 'STUDENT';
    $('registrationStudent').hidden = !student; $('authForm').elements.studentId.required = student; $('authForm').elements.studentId.disabled = !student; $('studentCheckStatus').textContent = '';
}
async function submitAuth(event) {
    event.preventDefault(); const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const values = Object.fromEntries(new FormData(form));
    if (state.authMode === 'register' && values.password !== values.confirmPassword) return errorAt('authError', 'Hai mật khẩu chưa khớp nhau.');
    delete values.confirmPassword;
    if (state.authMode === 'register') values.studentId = values.customerType === 'STUDENT' ? values.studentId.trim().toUpperCase() : null;
    $('authFields').disabled = true; $('authSubmit').disabled = true; errorAt('authError', '');
    try {
        Cinema.user = await Cinema.api(`/api/auth/${state.authMode}`, { method: 'POST', body: body(values) });
        Cinema.resetCsrf(); renderSession(); $('authDialog').close(); form.reset();
        if (Cinema.user.role === 'ADMIN') { location.assign('/admin.html'); return; }
        toast('Chào mừng bạn đến N6 Cinema!');
        if (state.pendingBooking) { const pending = state.pendingBooking; state.pendingBooking = null; await openBooking(...pending); }
    } catch (error) { errorAt('authError', error.message); }
    finally { $('authFields').disabled = false; $('authSubmit').disabled = false; }
}
async function checkStudent() {
    const id = $('authForm').elements.studentId.value.trim().toUpperCase();
    $('checkRegistrationStudent').disabled = true;
    try { const result = await Cinema.api('/api/customers/student-id/check?' + new URLSearchParams({ studentId: id })); $('studentCheckStatus').textContent = result.message; }
    catch (error) { $('studentCheckStatus').textContent = error.message; }
    finally { $('checkRegistrationStudent').disabled = false; }
}
async function openBooking(movieId, showtimeId = '') {
    if (!Cinema.user) { state.pendingBooking = [movieId, showtimeId]; openAuth(); return; }
    if (Cinema.user.role !== 'CUSTOMER') { toast('Tài khoản quản trị dùng để quản lý rạp. Hãy dùng tài khoản khách hàng để đặt vé.'); return; }
    state.movie = state.movies.find(movie => movie.id === movieId); if (!state.movie) return;
    $('bookingTitle').textContent = state.movie.title; $('bookingMeta').textContent = `${state.movie.genre} · ${state.movie.duration} phút · ${types[Cinema.user.customer.customerType]}`;
    const shows = futureShows(movieId);
    $('bookingShowtime').replaceChildren(...(shows.length ? shows.map(show => option(show.id, `${formatTime(show.startTime)} · ${roomName(show.roomId)} · từ ${Cinema.money(show.price)}`)) : [option('', 'Chưa có suất chiếu mở bán')]));
    if (shows.some(show => show.id === showtimeId)) $('bookingShowtime').value = showtimeId;
    $('bookingDialog').showModal(); await loadSeats();
}
async function loadSeats() {
    const version = ++state.seatVersion; ++state.quoteVersion; state.selected.clear(); state.seats = [];
    $('seatGrid').replaceChildren(); $('selectedSeats').textContent = 'Chưa chọn ghế'; $('quoteTotal').textContent = '—'; $('quoteDiscount').textContent = 'Giá vé được tính theo hạng thành viên.'; $('confirmBooking').disabled = true; errorAt('bookingError', '');
    const id = $('bookingShowtime').value;
    if (!id) { $('bookingStatus').textContent = 'Rạp chưa mở lịch cho phim này. Bạn hãy chọn phim khác hoặc quay lại sau.'; return; }
    $('bookingStatus').textContent = 'Đang tải sơ đồ ghế…';
    try {
        const seats = await Cinema.api(`/api/bookings/showtimes/${encodeURIComponent(id)}/seats`);
        if (version !== state.seatVersion) return;
        state.seats = seats; renderSeats();
        $('bookingStatus').textContent = seats.length ? 'Chọn ghế yêu thích. Ghế được kiểm tra lại khi xác nhận đặt vé.' : 'Phòng chiếu chưa có sơ đồ ghế. Vui lòng chọn suất khác.';
    } catch (error) { if (version === state.seatVersion) { $('bookingStatus').textContent = ''; errorAt('bookingError', error.message); } }
}
function renderSeats() {
    $('seatGrid').replaceChildren(); const rows = [...new Set(state.seats.map(seat => seat.rowLabel))].sort();
    rows.forEach(rowLabel => {
        const row = el('div', '', 'seat-row'); row.append(el('span', rowLabel, 'row-label'));
        state.seats.filter(seat => seat.rowLabel === rowLabel).sort((a, b) => a.seatNumber - b.seatNumber).forEach(seat => {
            const button = el('button', seat.rowLabel + seat.seatNumber, 'seat' + (seat.type !== 'NORMAL' ? ' vip' : '') + (state.selected.has(seat.id) ? ' selected' : ''));
            button.disabled = !seat.available || state.bookingBusy; button.setAttribute('aria-pressed', state.selected.has(seat.id));
            button.setAttribute('aria-label', `Ghế ${seat.rowLabel}${seat.seatNumber}, ${seat.type === 'NORMAL' ? 'thường' : seat.type === 'VIP' ? 'VIP' : 'đôi'}, ${seat.available ? Cinema.money(seat.price) : 'không khả dụng'}`);
            button.addEventListener('click', () => { state.selected.has(seat.id) ? state.selected.delete(seat.id) : state.selected.add(seat.id); renderSeats(); updateQuote(); }); row.append(button);
        }); $('seatGrid').append(row);
    });
}
function bookingPayload() { return { showtimeId: $('bookingShowtime').value, seatIds: [...state.selected] }; }
async function updateQuote() {
    const version = ++state.quoteVersion; $('confirmBooking').disabled = true; errorAt('bookingError', '');
    $('selectedSeats').textContent = state.seats.filter(seat => state.selected.has(seat.id)).map(seat => seat.rowLabel + seat.seatNumber).join(', ') || 'Chưa chọn ghế';
    $('quoteTotal').textContent = state.selected.size ? 'Đang tính…' : '—';
    if (!state.selected.size) return;
    try {
        const quote = await Cinema.api('/api/bookings/quote', { method: 'POST', body: body(bookingPayload()) });
        if (version !== state.quoteVersion) return;
        $('quoteTotal').textContent = Cinema.money(quote.total); $('quoteDiscount').textContent = `Ưu đãi ${Math.round(quote.discountRate * 100)}% · Tiết kiệm ${Cinema.money(quote.discountAmount)}`; $('confirmBooking').disabled = false;
    } catch (error) { if (version === state.quoteVersion) { $('quoteTotal').textContent = '—'; errorAt('bookingError', error.message); if (error.status === 401) { Cinema.user = null; renderSession(); } } }
}
async function confirmBooking() {
    if (state.bookingBusy || !state.selected.size) return;
    state.bookingBusy = true; $('confirmBooking').disabled = true; $('bookingShowtime').disabled = true; $('confirmBooking').textContent = 'Đang đặt vé…'; renderSeats();
    try {
        const tickets = await Cinema.api('/api/bookings', { method: 'POST', body: body(bookingPayload()) });
        $('bookingDialog').close(); toast(`Đã đặt ${tickets.length} vé. Vui lòng thanh toán tại quầy.`); await openTickets();
    } catch (error) {
        if (error.status === 401) { $('bookingDialog').close(); Cinema.user = null; renderSession(); state.pendingBooking = [state.movie.id, $('bookingShowtime').value]; openAuth(); }
        else { await loadSeats(); errorAt('bookingError', error.message + ' Sơ đồ ghế đã được cập nhật; vui lòng chọn lại.'); }
    } finally { state.bookingBusy = false; $('bookingShowtime').disabled = false; $('confirmBooking').textContent = 'Xác nhận đặt vé →'; renderSeats(); }
}
function openProfile() {
    if (!Cinema.user?.customer) return;
    const customer = Cinema.user.customer, form = $('profileForm'); form.reset();
    $('profileType').replaceChildren(option('NORMAL', 'Khách thường'), option('STUDENT', 'Sinh viên'));
    if (customer.customerType === 'VIP') $('profileType').append(option('VIP', 'Thành viên VIP'));
    $('profileType').disabled = customer.customerType === 'VIP';
    ['fullName', 'phone', 'email', 'customerType', 'studentId'].forEach(name => form.elements[name].value = customer[name] || '');
    $('membershipInfo').textContent = `${types[customer.customerType]} · Ưu đãi ${Math.round(customer.discountRate * 100)}%`;
    updateProfileType(); errorAt('profileError', ''); $('profileDialog').showModal();
}
function updateProfileType() { const student = $('profileType').value === 'STUDENT'; $('profileStudentLabel').hidden = !student; $('profileForm').elements.studentId.required = student; $('profileForm').elements.studentId.disabled = !student; }
async function submitProfile(event) {
    event.preventDefault(); const form = event.currentTarget, values = Object.fromEntries(new FormData(form));
    values.customerType = $('profileType').value; values.studentId = values.customerType === 'STUDENT' ? values.studentId.trim().toUpperCase() : null;
    $('profileFields').disabled = true; form.querySelector('[type=submit]').disabled = true;
    try { Cinema.user = await Cinema.api('/api/auth/profile', { method: 'PUT', body: body(values) }); renderSession(); $('profileDialog').close(); toast('Đã lưu thông tin khách hàng.'); }
    catch (error) { errorAt('profileError', error.message); }
    finally { $('profileFields').disabled = false; form.querySelector('[type=submit]').disabled = false; }
}
async function openTickets() {
    if (!$('ticketsDialog').open) $('ticketsDialog').showModal();
    $('ticketsList').replaceChildren(el('p', 'Đang tải vé…', 'muted'));
    try {
        const tickets = await Cinema.api('/api/bookings/mine');
        $('ticketsList').replaceChildren();
        if (!tickets.length) $('ticketsList').append(el('div', 'Bạn chưa có vé. Khám phá lịch chiếu và chọn một bộ phim nhé.', 'empty-state'));
        tickets.slice().reverse().forEach(ticket => {
            const show = state.showtimes.find(show => show.id === ticket.showtimeId), movie = state.movies.find(movie => movie.id === show?.movieId);
            const card = el('article', '', 'ticket-row');
            card.append(el('h3', movie?.title || 'Vé xem phim'), el('p', show ? `${formatTime(show.startTime)} · ${roomName(show.roomId)} · Ghế ${ticket.seatId}` : `Suất ${ticket.showtimeId} · Ghế ${ticket.seatId}`), el('p', Cinema.money(ticket.price)), el('code', `Mã vé: ${ticket.id}`));
            const actions = el('div', '', 'ticket-actions'); actions.append(el('span', labels[ticket.paymentStatus] || ticket.paymentStatus, 'status-pill' + (ticket.paymentStatus === 'SUCCESS' ? ' success' : ticket.paymentStatus === 'FAILED' ? ' cancelled' : '')));
            if (ticket.paymentStatus === 'PENDING') {
                const cancel = el('button', 'Hủy vé', 'text-button');
                cancel.addEventListener('click', async () => {
                    if (!confirm('Hủy vé chưa thanh toán này và trả ghế về rạp?')) return;
                    cancel.disabled = true;
                    try { await Cinema.api(`/api/bookings/${encodeURIComponent(ticket.id)}/cancel`, { method: 'POST' }); await openTickets(); }
                    catch (error) { toast(error.message); cancel.disabled = false; }
                }); actions.append(cancel);
            }
            card.append(actions); $('ticketsList').append(card);
        });
    } catch (error) { $('ticketsList').replaceChildren(el('p', error.message, 'form-error')); }
}
async function init() {
    document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => { if (button.dataset.close === 'bookingDialog' && state.bookingBusy) return; $(button.dataset.close).close(); }));
    $('bookingDialog').addEventListener('cancel', event => { if (state.bookingBusy) event.preventDefault(); });
    $('bookingDialog').addEventListener('close', () => { ++state.seatVersion; ++state.quoteVersion; });
    $('loginButton').onclick = () => openAuth(); $('registerButton').onclick = () => openAuth('register');
    $('loginTab').onclick = () => setAuthMode('login'); $('registerTab').onclick = () => setAuthMode('register');
    $('authForm').onsubmit = submitAuth; $('registrationType').onchange = updateRegistrationType; $('checkRegistrationStudent').onclick = checkStudent;
    $('logoutButton').onclick = () => Cinema.logout().catch(error => toast(error.message));
    $('accountButton').onclick = openProfile; $('profileType').onchange = updateProfileType; $('profileForm').onsubmit = submitProfile;
    $('ticketsButton').onclick = openTickets; $('bookingShowtime').onchange = loadSeats; $('confirmBooking').onclick = confirmBooking;
    $('quickMovie').onchange = updateQuickShows; $('quickBookButton').onclick = () => { if (!$('quickMovie').value) { $('quickMovie').focus(); toast('Hãy chọn phim trước.'); return; } openBooking($('quickMovie').value, $('quickShowtime').value); };
    $('joinButton').onclick = () => Cinema.user?.role === 'ADMIN' ? location.assign('/admin.html') : Cinema.user ? openProfile() : openAuth('register');
    $('movieSearch').oninput = renderMovies; $('reloadCatalogue').onclick = loadCatalogue;
    [['nowShowing', 'NOW_SHOWING'], ['comingSoon', 'COMING_SOON']].forEach(([id, filter]) => $(id).onclick = () => { state.filter = filter; ['nowShowing', 'comingSoon'].forEach(tab => { $(tab).classList.toggle('active', tab === id); $(tab).setAttribute('aria-pressed', tab === id); }); renderMovies(); });
    await Promise.all([loadCatalogue(), Cinema.me().then(renderSession).catch(error => toast(error.message)), Cinema.api('/api/customers/policies').then(policies => { $('studentBenefit').textContent = `−${Math.round(policies.STUDENT * 100)}%`; $('vipBenefit').textContent = `−${Math.round(policies.VIP * 100)}%`; }).catch(() => { $('studentBenefit').textContent = 'Theo hạng'; $('vipBenefit').textContent = 'Theo hạng'; })]);
    const params = new URLSearchParams(location.search);
    if (params.has('login') && !Cinema.user) openAuth();
    const movieId = params.get('movie'); if (movieId) openBooking(movieId, params.get('showtime') || '');
}
document.addEventListener('DOMContentLoaded', init);
