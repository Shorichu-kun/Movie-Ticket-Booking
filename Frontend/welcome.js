"use strict";
const $ = (id) => document.getElementById(id);
const state = {
  movies: [],
  showtimes: [],
  rooms: [],
  filter: "NOW_SHOWING",
  authMode: "login",
};
const el = (tag, text, cls = "") => {
  const node = document.createElement(tag);
  node.textContent = text;
  node.className = cls;
  return node;
};
const body = (value) => JSON.stringify(value);
const dateOf = (value) =>
  new Date(
    /(?:Z|[+-]\d{2}:?\d{2})$/.test(value)
      ? value
      : value.replace(" ", "T") + "+07:00",
  );
const formatTime = (value) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(dateOf(value));
const roomName = (id) => state.rooms.find((room) => room.id === id)?.name || id;
const futureShows = (movieId) =>
  state.showtimes
    .filter(
      (show) => show.movieId === movieId && dateOf(show.startTime) > new Date(),
    )
    .sort((a, b) => dateOf(a.startTime) - dateOf(b.startTime));
let toastTimer;
function toast(message) {
  $("toast").textContent = message;
  $("toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($("toast").hidden = true), 6000);
}
function errorAt(id, message) {
  $(id).textContent = message;
  $(id).hidden = !message;
}
function option(value, text) {
  const node = el("option", text);
  node.value = value;
  return node;
}

async function loadCatalogue() {
  $("catalogueStatus").hidden = false;
  $("catalogueStatus").textContent = "Đang tải danh sách phim…";
  $("reloadCatalogue").hidden = true;
  try {
    [state.movies, state.showtimes, state.rooms] = await Promise.all(
      ["/api/movies", "/api/showtimes", "/api/rooms"].map((path) =>
        Cinema.api(path),
      ),
    );
    renderMovies();
    const oldMovie = $("quickMovie").value;
    $("quickMovie").replaceChildren(
      option("", "Chọn bộ phim bạn thích"),
      ...state.movies
        .filter((movie) => movie.status === "NOW_SHOWING")
        .map((movie) => option(movie.id, movie.title)),
    );
    $("quickMovie").value = oldMovie;
    updateQuickShows();
  } catch (error) {
    $("catalogueStatus").textContent =
      "Chưa tải được phim và lịch chiếu. " + error.message;
    $("reloadCatalogue").hidden = false;
  }
}
function renderMovies() {
  const query = $("movieSearch").value.trim().toLocaleLowerCase("vi");
  $("nowCount").textContent = state.movies.filter(
    (movie) => movie.status === "NOW_SHOWING",
  ).length;
  $("soonCount").textContent = state.movies.filter(
    (movie) => movie.status === "COMING_SOON",
  ).length;
  const movies = state.movies.filter(
    (movie) =>
      movie.status === state.filter &&
      `${movie.title} ${movie.genre}`.toLocaleLowerCase("vi").includes(query),
  );
  $("movieGrid").replaceChildren();
  $("catalogueStatus").hidden = movies.length > 0;
  $("catalogueStatus").textContent = query
    ? "Không tìm thấy phim phù hợp. Thử một tên phim hoặc thể loại khác."
    : "Chưa có phim trong mục này. Lịch phim mới sẽ xuất hiện khi rạp cập nhật.";
  movies.forEach((movie, index) => {
    const card = el("article", "", "movie-card");
    const poster = el("div", "", "poster");
    poster.dataset.style = index % 4;
    poster.setAttribute("aria-hidden", "true");
    poster.append(
      el(
        "span",
        movie.status === "NOW_SHOWING" ? "ĐANG CHIẾU" : "SẮP RA MẮT",
        "movie-tag",
      ),
      el("span", String(index + 1).padStart(2, "0"), "poster-number"),
      el("div", movie.title, "poster-title"),
      el("small", "N6 CINEMA · BIG SCREEN"),
    );
    const action = el(
      "button",
      movie.status === "COMING_SOON" ? "Chưa mở bán" : "Lịch chiếu & đặt vé ↗",
    );
    action.disabled = movie.status === "COMING_SOON";
    action.addEventListener("click", () => openBooking(movie.id));
    card.append(
      poster,
      el("h3", movie.title),
      el("p", `${movie.genre} · ${movie.duration} phút`),
      action,
    );
    $("movieGrid").append(card);
  });
}
function updateQuickShows() {
  const shows = futureShows($("quickMovie").value);
  $("quickShowtime").replaceChildren(
    option("", shows.length ? "Chọn suất chiếu" : "Chưa có suất chiếu"),
    ...shows.map((show) =>
      option(
        show.id,
        `${formatTime(show.startTime)} · ${roomName(show.roomId)}`,
      ),
    ),
  );
}
function renderSession() {
  const user = Cinema.user,
    admin = user?.role === "ADMIN";
  $("loginButton").hidden = !!user;
  $("registerButton").hidden = !!user;
  $("accountButton").hidden = !user || admin;
  $("ticketsButton").hidden = !user || admin;
  $("adminLink").hidden = !admin;
  $("logoutButton").hidden = !user;
  $("accountButton").textContent =
    user?.customer?.fullName?.split(" ").pop() || "Tài khoản";
  $("joinButton").textContent = admin
    ? "Mở trang quản trị ↗"
    : user
      ? "Hồ sơ thành viên ↗"
      : "Tham gia cùng N6 ↗";
}
function setAuthMode(mode) {
  state.authMode = mode;
  const register = mode === "register";
  $("registrationFields").hidden = !register;
  $("confirmPasswordLabel").hidden = !register;
  $("registrationFields")
    .querySelectorAll("input,select")
    .forEach((input) => (input.disabled = !register));
  ["fullName", "phone"].forEach(
    (name) => ($("authForm").elements[name].required = register),
  );
  $("authForm").elements.confirmPassword.required = register;
  $("authForm").elements.confirmPassword.disabled = !register;
  $("password").minLength = register ? 8 : 1;
  $("password").autocomplete = register ? "new-password" : "current-password";
  $("authTitle").textContent = register
    ? "Bắt đầu một cuộc hẹn."
    : "Rất vui gặp lại bạn.";
  $("authSubtitle").textContent = register
    ? "Tạo tài khoản để chọn ghế và nhận ưu đãi thành viên."
    : "Đăng nhập để đặt vé và xem lịch sử của bạn.";
  $("authSubmit").textContent = register ? "Tạo tài khoản →" : "Đăng nhập →";
  $("loginTab").classList.toggle("active", !register);
  $("registerTab").classList.toggle("active", register);
  errorAt("authError", "");
  updateRegistrationType();
}
function openAuth(mode = "login") {
  $("authForm").reset();
  setAuthMode(mode);
  $("authDialog").showModal();
}
function updateRegistrationType() {
  const student =
    state.authMode === "register" && $("registrationType").value === "STUDENT";
  $("registrationStudent").hidden = !student;
  $("authForm").elements.studentId.required = student;
  $("authForm").elements.studentId.disabled = !student;
  $("studentCheckStatus").textContent = "";
}
async function submitAuth(event) {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const values = Object.fromEntries(new FormData(form));
  if (
    state.authMode === "register" &&
    values.password !== values.confirmPassword
  )
    return errorAt("authError", "Hai mật khẩu chưa khớp nhau.");
  delete values.confirmPassword;
  if (state.authMode === "register")
    values.studentId =
      values.customerType === "STUDENT"
        ? values.studentId.trim().toUpperCase()
        : null;
  $("authFields").disabled = true;
  $("authSubmit").disabled = true;
  errorAt("authError", "");
  try {
    Cinema.user = await Cinema.api(`/api/auth/${state.authMode}`, {
      method: "POST",
      body: body(values),
    });
    Cinema.resetCsrf();
    renderSession();
    $("authDialog").close();
    form.reset();
    if (Cinema.user.role === "ADMIN") {
      location.assign("/admin.html");
      return;
    }
    toast("Chào mừng bạn đến N6 Cinema!");
    const returnTo = new URLSearchParams(location.search).get("returnTo");
    if (returnTo) {
      const target = new URL(returnTo, location.origin);
      if (
        target.origin === location.origin &&
        target.pathname.endsWith(".html")
      )
        location.assign(target.href);
    }
  } catch (error) {
    errorAt("authError", error.message);
  } finally {
    $("authFields").disabled = false;
    $("authSubmit").disabled = false;
  }
}
async function checkStudent() {
  const id = $("authForm").elements.studentId.value.trim().toUpperCase();
  $("checkRegistrationStudent").disabled = true;
  try {
    const result = await Cinema.api(
      "/api/customers/student-id/check?" +
        new URLSearchParams({ studentId: id }),
    );
    $("studentCheckStatus").textContent = result.message;
  } catch (error) {
    $("studentCheckStatus").textContent = error.message;
  } finally {
    $("checkRegistrationStudent").disabled = false;
  }
}
function openBooking(movieId, showtimeId = "") {
  const params = new URLSearchParams({ movieId });
  if (showtimeId) params.set("showtimeId", showtimeId);
  location.assign("/booking.html?" + params);
}
function openProfile() {
  location.assign("/account.html");
}
function openTickets() {
  location.assign("/account.html#orders");
}
async function init() {
  document.querySelectorAll("[data-close]").forEach((button) =>
    button.addEventListener("click", () => {
      $(button.dataset.close).close();
    }),
  );
  $("loginButton").onclick = () => openAuth();
  $("registerButton").onclick = () => openAuth("register");
  $("loginTab").onclick = () => setAuthMode("login");
  $("registerTab").onclick = () => setAuthMode("register");
  $("authForm").onsubmit = submitAuth;
  $("registrationType").onchange = updateRegistrationType;
  $("checkRegistrationStudent").onclick = checkStudent;
  $("logoutButton").onclick = () =>
    Cinema.logout().catch((error) => toast(error.message));
  $("accountButton").onclick = openProfile;
  $("ticketsButton").onclick = openTickets;
  $("quickMovie").onchange = updateQuickShows;
  $("quickBookButton").onclick = () => {
    if (!$("quickMovie").value) {
      $("quickMovie").focus();
      toast("Hãy chọn phim trước.");
      return;
    }
    openBooking($("quickMovie").value, $("quickShowtime").value);
  };
  $("joinButton").onclick = () =>
    Cinema.user?.role === "ADMIN"
      ? location.assign("/admin.html")
      : Cinema.user
        ? openProfile()
        : openAuth("register");
  $("movieSearch").oninput = renderMovies;
  $("reloadCatalogue").onclick = loadCatalogue;
  [
    ["nowShowing", "NOW_SHOWING"],
    ["comingSoon", "COMING_SOON"],
  ].forEach(
    ([id, filter]) =>
      ($(id).onclick = () => {
        state.filter = filter;
        ["nowShowing", "comingSoon"].forEach((tab) => {
          $(tab).classList.toggle("active", tab === id);
          $(tab).setAttribute("aria-pressed", tab === id);
        });
        renderMovies();
      }),
  );
  await Promise.all([
    loadCatalogue(),
    Cinema.me()
      .then(renderSession)
      .catch((error) => toast(error.message)),
    Cinema.api("/api/customers/policies")
      .then((policies) => {
        $("studentBenefit").textContent =
          `−${Math.round(policies.STUDENT * 100)}%`;
        $("vipBenefit").textContent = `−${Math.round(policies.VIP * 100)}%`;
      })
      .catch(() => {
        $("studentBenefit").textContent = "Theo hạng";
        $("vipBenefit").textContent = "Theo hạng";
      }),
  ]);
  const params = new URLSearchParams(location.search);
  if (params.has("login") && !Cinema.user) openAuth();
  if (params.has("register") && !Cinema.user) openAuth("register");
  const movieId = params.get("movie");
  if (movieId) openBooking(movieId, params.get("showtime") || "");
}
document.addEventListener("DOMContentLoaded", init);
