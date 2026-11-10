"use strict";
window.CommerceUI = (() => {
  const labels = {
    PENDING_PAYMENT: "Chờ thanh toán",
    PAID: "Đã thanh toán",
    CANCELLED: "Đã hủy",
    EXPIRED: "Hết hạn",
    REFUND_PENDING: "Đang xử lý hoàn",
    REFUNDED: "Đã hoàn",
    CREATED: "Đã tạo",
    PROCESSING: "Đang xác minh",
    SUCCEEDED: "Thành công",
    FAILED: "Thất bại",
    ISSUED: "Chưa sử dụng",
    USED: "Đã sử dụng",
    VOID: "Đã vô hiệu",
    AVAILABLE: "Có thể dùng",
    RESERVED: "Đang giữ",
  };
  const date = (value) =>
    value
      ? new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : value + "+07:00")
      : null;
  const time = (value) =>
    value
      ? new Intl.DateTimeFormat("vi-VN", {
          timeZone: "Asia/Ho_Chi_Minh",
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        }).format(date(value))
      : "Chưa xác định";
  const node = (tag, text, className) => {
    const e = document.createElement(tag);
    if (text != null) e.textContent = text;
    if (className) e.className = className;
    return e;
  };
  const link = (text, href, className = "text-button") => {
    const e = node("a", text, className);
    e.href = href;
    return e;
  };
  const api = (path, options = {}) =>
    Cinema.api("/api/commerce" + path, options);
  const post = (path, data = {}, key) =>
    api(path, {
      method: "POST",
      body: JSON.stringify(data),
      headers: key ? { "Idempotency-Key": key } : {},
    });
  const error = (message) => {
    const box = document.getElementById("pageError");
    if (box) {
      box.textContent = message || "";
      box.hidden = !message;
    }
  };
  const toast = (message) => {
    const box = document.getElementById("notice");
    if (box) {
      box.textContent = message;
      box.hidden = false;
    }
  };
  function price(container, quote, totalLabel = "Còn thanh toán") {
    container.replaceChildren();
    if (!quote) {
      container.append(
        node("p", "Giá sẽ được máy chủ tính sau khi chọn ghế.", "muted"),
      );
      return;
    }
    const lines = [
      ["Vé & phụ phí", quote.ticketSubtotal],
      ["Bắp nước", quote.comboSubtotal],
      [quote.discountLabel || "Ưu đãi", -Number(quote.discount || 0)],
      [
        `Điểm sử dụng (${quote.pointsUsed || 0})`,
        -Number(quote.pointsValue || 0),
      ],
      [totalLabel, quote.total],
    ];
    lines.forEach(([label, amount], i) => {
      const row = node(
        "div",
        null,
        "price-line" + (i === lines.length - 1 ? " price-total" : ""),
      );
      row.append(node("span", label), node("strong", Cinema.money(amount)));
      container.append(row);
    });
  }
  function orderCard(order) {
    const row = node("article", null, "order-card");
    const top = node("div", null, "split-row");
    top.append(
      node("span", order.code || order.id, "mono"),
      node(
        "span",
        labels[order.status] || order.status,
        "status-badge " + (order.status === "PAID" ? "positive" : ""),
      ),
    );
    row.append(
      top,
      node("h3", order.movie?.title || "Đơn vé N6"),
      node(
        "p",
        `${time(order.showtime?.startTime)} · ${order.room?.name || ""}`,
        "muted",
      ),
      node("p", `Ghế: ${(order.seatLabels || order.seatIds || []).join(", ")}`),
    );
    const bottom = node("div", null, "split-row");
    bottom.append(
      node("strong", Cinema.money(order.quote?.total)),
      link("Chi tiết đơn →", "/order.html?id=" + encodeURIComponent(order.id)),
    );
    row.append(bottom);
    return row;
  }
  function empty(container, text) {
    container.replaceChildren(node("p", text, "empty-message"));
  }
  async function customer() {
    const user = await Cinema.me();
    if (!user) {
      location.assign(
        "/welcome.html?login=1&returnTo=" +
          encodeURIComponent(
            location.pathname + location.search + location.hash,
          ),
      );
      return null;
    }
    if (user.role !== "CUSTOMER") {
      error(
        "Trang này dành cho tài khoản khách hàng. Bạn đang đăng nhập quản trị viên.",
      );
      return null;
    }
    return user;
  }
  const json = (form) => Object.fromEntries(new FormData(form));
  const key = (name, payload = "") => {
    const storageKey = "n6-command-" + name;
    let saved;
    try {
      saved = JSON.parse(sessionStorage.getItem(storageKey));
    } catch {}
    if (!saved || saved.payload !== payload) {
      saved = { payload, value: crypto.randomUUID() };
      sessionStorage.setItem(storageKey, JSON.stringify(saved));
    }
    return saved.value;
  };
  return {
    labels,
    date,
    time,
    node,
    link,
    api,
    post,
    error,
    toast,
    price,
    orderCard,
    empty,
    customer,
    json,
    key,
  };
})();
