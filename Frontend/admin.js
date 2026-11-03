"use strict";
const adminEl = (tag, text, cls = "") => {
  const node = document.createElement(tag);
  node.textContent = text;
  node.className = cls;
  return node;
};
let adminData = { tickets: [], movies: [], shows: [], customers: [] };
const paymentLabels = {
  PENDING: "Chờ thanh toán",
  SUCCESS: "Đã thanh toán",
  FAILED: "Đã hủy",
  REFUNDED: "Đã hoàn tiền",
};
async function loadAdminBookings() {
  const refresh = document.getElementById("refreshBookings");
  refresh.disabled = true;
  try {
    const [tickets, movies, shows, customers] = await Promise.all(
      ["/api/bookings", "/api/movies", "/api/showtimes", "/api/customers"].map(
        (path) => Cinema.api(path),
      ),
    );
    adminData = { tickets, movies, shows, customers };
    document.getElementById("bookingStats").textContent =
      `${tickets.length} vé · ${tickets.filter((t) => t.paymentStatus === "PENDING").length} vé chờ thanh toán · Đã thu: ${Cinema.money(tickets.filter((t) => t.paymentStatus === "SUCCESS").reduce((sum, t) => sum + Number(t.price), 0))}`;
    renderAdminBookings();
  } catch (error) {
    document.getElementById("bookingStats").textContent = error.message;
  } finally {
    refresh.disabled = false;
  }
}
function renderAdminBookings() {
  const list = document.getElementById("adminBookings");
  list.replaceChildren();
  const query = document
    .getElementById("bookingSearch")
    .value.toLowerCase()
    .trim();
  let shown = 0;
  adminData.tickets
    .slice()
    .reverse()
    .forEach((ticket) => {
      const show = adminData.shows.find(
        (show) => show.id === ticket.showtimeId,
      );
      const movie = adminData.movies.find(
        (movie) => movie.id === show?.movieId,
      );
      const customer = adminData.customers.find(
        (customer) => customer.id === ticket.customerId,
      );
      if (
        query &&
        !`${ticket.id} ${customer?.fullName} ${customer?.phone} ${movie?.title}`
          .toLowerCase()
          .includes(query)
      )
        return;
      if (shown >= 100) return;
      shown++;
      const row = adminEl("article", "", "ticket-row");
      row.append(
        adminEl(
          "h3",
          `${movie?.title || ticket.showtimeId} · ${Cinema.money(ticket.price)}`,
        ),
        adminEl(
          "p",
          `${customer?.fullName || ticket.customerId} · ${customer?.phone || ""}`,
        ),
        adminEl(
          "p",
          `${show?.startTime?.replace("T", " ") || "Suất đã xóa"} · Ghế ${ticket.seatId}`,
        ),
        adminEl("code", `Mã vé: ${ticket.id}`),
      );
      const actions = adminEl("div", "", "ticket-actions");
      actions.append(
        adminEl(
          "span",
          paymentLabels[ticket.paymentStatus] || ticket.paymentStatus,
          "status-pill" +
            (ticket.paymentStatus === "SUCCESS" ? " success" : ""),
        ),
      );
      if (ticket.paymentStatus === "PENDING") {
        const pay = adminEl(
          "button",
          "Xác nhận đã thu tiền",
          "button dark small",
        );
        pay.onclick = async () => {
          if (
            !confirm(
              `Xác nhận đã nhận ${Cinema.money(ticket.price)} tại quầy cho vé ${ticket.id}?`,
            )
          )
            return;
          pay.disabled = true;
          try {
            await Cinema.api(
              `/api/bookings/${encodeURIComponent(ticket.id)}/confirm-payment`,
              { method: "POST" },
            );
            await loadAdminBookings();
          } catch (error) {
            alert(error.message);
            pay.disabled = false;
          }
        };
        actions.append(pay);
      }
      row.append(actions);
      list.append(row);
    });
  if (shown === 100)
    list.append(
      adminEl(
        "p",
        "Hiển thị tối đa 100 vé cũ phù hợp gần nhất. Tìm theo mã vé để thu hẹp kết quả.",
        "fine-print",
      ),
    );
  if (!list.children.length)
    list.append(adminEl("div", "Không có vé phù hợp.", "empty-state"));
}
document.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("adminLogout").onclick = () =>
    Cinema.logout().catch((error) => alert(error.message));
  document.getElementById("refreshBookings").onclick = loadAdminBookings;
  document.getElementById("bookingSearch").oninput = renderAdminBookings;
  try {
    const user = await Cinema.requireAdmin();
    if (!user) return;
    document.getElementById("adminIdentity").textContent =
      `Đang đăng nhập: ${user.email} · Quản trị viên`;
    await loadAdminBookings();
  } catch (error) {
    document.getElementById("adminIdentity").textContent = error.message;
  }
});
