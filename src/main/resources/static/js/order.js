"use strict";
(() => {
  const U = CommerceUI,
    $ = (id) => document.getElementById(id),
    id = new URLSearchParams(location.search).get("id");
  let order,
    catalog,
    busy = false,
    poll;
  const descriptions = {
    PAID: [
      "Đã xác nhận buổi hẹn.",
      "Xuất trình QR tại rạp. Vé và giao dịch trong phiên này là dữ liệu thử.",
    ],
    PENDING_PAYMENT: [
      "Đơn đang chờ thanh toán.",
      "Ghế có thời hạn giữ. Chỉ có vé vào cửa sau khi thanh toán thành công.",
    ],
    EXPIRED: [
      "Lượt giữ chỗ đã hết hạn.",
      "Ghế và quyền lợi chưa sử dụng đã được giải phóng. Bạn có thể đặt một đơn mới.",
    ],
    CANCELLED: [
      "Đơn đã được hủy.",
      "Cảm ơn bạn đã thông báo. Hẹn bạn ở một buổi phim khác.",
    ],
    REFUND_PENDING: [
      "Đang xử lý hoàn thử.",
      "Bạn có thể theo dõi kết quả tại đây. Vé tạm thời không được sử dụng.",
    ],
    REFUNDED: [
      "Đã hoàn giao dịch thử.",
      "Tiền thử và quyền lợi được hoàn theo chính sách của đơn.",
    ],
  };
  async function action(path, payload = {}) {
    if (busy) return;
    busy = true;
    U.error("");
    try {
      await U.post(path, payload);
      await load();
    } catch (e) {
      U.error(e.message);
    } finally {
      busy = false;
    }
  }
  function button(text, fn, className = "button light small") {
    const b = U.node("button", text, className);
    b.onclick = fn;
    return b;
  }
  async function load() {
    order = await U.api("/orders/" + encodeURIComponent(id));
    render();
  }
  function render() {
    $("orderContent").hidden = false;
    const title = descriptions[order.status] || [
      U.labels[order.status] || order.status,
      "",
    ];
    $("orderTitle").textContent = title[0];
    $("orderCaption").textContent = title[1];
    $("receiptIcon").textContent =
      order.status === "PAID"
        ? "✓"
        : order.status === "PENDING_PAYMENT"
          ? "◷"
          : order.status === "REFUNDED"
            ? "↩"
            : "—";
    $("orderMovie").textContent = order.movie?.title || "Vé N6";
    $("orderStatus").textContent = U.labels[order.status] || order.status;
    $("orderStatus").className =
      "status-badge " + (order.status === "PAID" ? "positive" : "");
    $("orderMeta").textContent =
      `${U.time(order.showtime?.startTime)} · ${order.room?.name || ""} · Ghế ${(order.seatLabels || order.seatIds || []).join(", ")}`;
    $("orderCode").textContent =
      `Mã đơn: ${order.code || id} · Tạo lúc ${U.time(order.createdAt)}`;
    $("orderItems").replaceChildren(
      ...(order.items || []).map((item) => {
        const li = U.node("li");
        li.append(
          U.node("span", `${item.quantity} × ${item.title}`),
          U.node("strong", Cinema.money(item.total)),
        );
        return li;
      }),
    );
    $("orderContact").textContent = order.contact
      ? `Thông tin nhận vé: ${order.contact.name} · ${order.contact.email} · ${order.contact.phone}`
      : "";
    $("orderTickets").replaceChildren(
      ...(order.tickets || []).map((ticket) => {
        const card = U.node("article", null, "ticket-card"),
          copy = U.node("div");
        copy.append(
          U.node("p", "N6 CINEMA · VÉ DEMO", "eyebrow"),
          U.node("h3", "Ghế " + ticket.seatLabel),
          U.node(
            "span",
            U.labels[ticket.status] || ticket.status,
            "status-badge",
          ),
          U.node("p", ticket.id, "mono"),
        );
        card.append(copy);
        if (ticket.status === "ISSUED" && order.status === "PAID") {
          const img = U.node("img");
          img.src =
            "/api/commerce/tickets/" + encodeURIComponent(ticket.id) + "/qr";
          img.alt = "QR vào cửa thử cho ghế " + ticket.seatLabel;
          card.append(img);
        }
        return card;
      }),
    );
    U.price(
      $("orderPrice"),
      order.quote,
      order.status === "PENDING_PAYMENT"
        ? "Còn thanh toán"
        : "Tiền thử theo đơn",
    );
    $("orderReward").textContent =
      order.status === "PAID"
        ? `Điểm theo đơn: ${order.quote?.pointsEarned || 0}. Xem sổ điểm để kiểm tra bút toán đã ghi.`
        : "";
    $("orderTimeline").replaceChildren(
      ...(order.events || []).map((event) => {
        const li = U.node("li");
        li.append(
          U.node("time", U.time(event.at)),
          U.node("span", event.message),
        );
        return li;
      }),
    );
    $("orderAttempts").replaceChildren(
      ...(order.attempts || []).map((a) => {
        const row = U.node("div", null, "split-row");
        row.append(
          U.node("span", `${a.method} · ${U.labels[a.status] || a.status}`),
          U.node("strong", Cinema.money(a.amount)),
          U.node("span", a.id, "mono"),
        );
        return row;
      }),
    );
    const actions = $("orderActions");
    actions.replaceChildren();
    $("retryPayment").hidden = true;
    const unresolved = (order.attempts || []).find(
      (a) =>
        ["CREATED", "PROCESSING"].includes(a.status) && a.method !== "CASH",
    );
    if (order.status === "PENDING_PAYMENT") {
      actions.append(
        button("Hủy đơn chưa thanh toán", () => {
          if (confirm("Hủy đơn và giải phóng ghế đang giữ?"))
            action("/orders/" + id + "/cancel");
        }),
      );
      if (unresolved)
        actions.append(
          U.link(
            "Tiếp tục giao dịch thử →",
            "/payment-demo.html?attemptId=" +
              encodeURIComponent(unresolved.id) +
              "&orderId=" +
              encodeURIComponent(id),
            "button orange small",
          ),
        );
      else if (order.method === "CASH")
        actions.append(
          U.node(
            "p",
            "Đang chờ admin xác nhận thu tiền thử tại quầy. Hạn: " +
              U.time(order.expiresAt),
            "fine-print",
          ),
        );
      else {
        $("retryPayment").hidden = false;
        $("retryMethod").replaceChildren(
          ...(catalog.methods || [])
            .filter((m) => m.code !== "LOYALTY")
            .map((m) => {
              const o = U.node("option", m.label);
              o.value = m.code;
              return o;
            }),
        );
      }
    }
    if (order.status === "PAID")
      actions.append(
        button("Yêu cầu hoàn toàn bộ đơn", () => {
          if (
            confirm(
              "Gửi yêu cầu hoàn toàn bộ đơn chưa sử dụng? Vé sẽ tạm ngừng check-in trong lúc xử lý.",
            )
          )
            action("/orders/" + id + "/refund");
        }),
      );
    actions.append(
      U.link(
        "Đặt buổi phim mới ↗",
        "/welcome.html#movies",
        "button light small",
      ),
    );
    clearTimeout(poll);
    if (order.status === "PENDING_PAYMENT" || order.status === "REFUND_PENDING")
      poll = setTimeout(() => load().catch((e) => U.error(e.message)), 10000);
  }
  $("retryButton").onclick = async () => {
    if (busy) return;
    busy = true;
    $("retryButton").disabled = true;
    try {
      const method = $("retryMethod").value;
      const attempt = await U.post(
        "/orders/" + id + "/payments",
        { method },
        U.key(
          "retry-" + id,
          JSON.stringify({ method, count: order.attempts?.length || 0 }),
        ),
      );
      if (method === "CASH") await load();
      else
        location.assign(
          "/payment-demo.html?attemptId=" +
            encodeURIComponent(attempt.id) +
            "&orderId=" +
            encodeURIComponent(id),
        );
    } catch (e) {
      U.error(e.message);
    } finally {
      busy = false;
      $("retryButton").disabled = false;
    }
  };
  $("printReceipt").onclick = () => window.print();
  (async () => {
    try {
      if (!(await U.customer())) return;
      if (!id)
        throw new Error("Thiếu mã đơn. Vui lòng mở đơn từ lịch sử đặt vé.");
      catalog = await U.api("/catalog");
      await load();
    } catch (e) {
      U.error(e.message);
      $("orderTitle").textContent = "Không mở được đơn hàng.";
    }
  })();
})();
