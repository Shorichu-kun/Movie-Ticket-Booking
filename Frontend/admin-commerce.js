"use strict";
(() => {
  const U = CommerceUI,
    $ = (id) => document.getElementById(id);
  let orders = [];
  function error(message) {
    $("commerceAdminError").textContent = message || "";
    $("commerceAdminError").hidden = !message;
  }
  async function load() {
    try {
      orders = await U.api("/admin/orders");
      const paid = orders.filter((o) => o.status === "PAID");
      $("commerceAdminStats").textContent =
        `${orders.length} đơn · ${orders.filter((o) => o.status === "PENDING_PAYMENT").length} chờ thanh toán · ${orders.filter((o) => o.status === "REFUND_PENDING").length} yêu cầu hoàn · Tiền thử ở đơn đã trả: ${Cinema.money(paid.reduce((sum, o) => sum + Number(o.quote.total), 0))}`;
      render();
    } catch (e) {
      error(e.message);
    }
  }
  function render() {
    const query = $("commerceOrderSearch").value.trim().toLocaleLowerCase("vi"),
      filter = $("commerceOrderFilter").value;
    const selected = orders
      .filter(
        (o) =>
          (!filter || o.status === filter) &&
          (!query ||
            `${o.id} ${o.code} ${o.movie?.title} ${o.contact?.email}`
              .toLocaleLowerCase("vi")
              .includes(query)),
      )
      .sort((a, b) => U.date(b.createdAt) - U.date(a.createdAt));
    $("commerceAdminOrders").replaceChildren(
      ...selected.slice(0, 100).map((o) => {
        const card = U.node("article", null, "order-card"),
          top = U.node("div", null, "split-row");
        top.append(
          U.node("span", o.code || o.id, "mono"),
          U.node("span", U.labels[o.status] || o.status, "status-badge"),
        );
        card.append(
          top,
          U.node("h3", o.movie?.title),
          U.node(
            "p",
            `${U.time(o.showtime?.startTime)} · ${o.room?.name || ""} · ${(o.seatLabels || []).join(", ")}`,
          ),
          U.node(
            "p",
            `${o.contact?.name || ""} · ${o.contact?.email || ""} · ${Cinema.money(o.quote?.total)} · ${o.method}`,
            "fine-print",
          ),
        );
        const detail = U.node("details"),
          title = U.node("summary", "Xem giao dịch & vé");
        detail.append(title);
        for (const a of o.attempts || [])
          detail.append(
            U.node(
              "p",
              `${a.id} · ${a.method} · ${U.labels[a.status] || a.status} · ${Cinema.money(a.amount)}`,
              "mono",
            ),
          );
        for (const t of o.tickets || [])
          detail.append(
            U.node(
              "p",
              `Ghế ${t.seatLabel} · ${U.labels[t.status] || t.status} · QR token: ${t.token}`,
              "mono",
            ),
          );
        for (const event of o.events || [])
          detail.append(
            U.node("p", `${U.time(event.at)} — ${event.message}`, "fine-print"),
          );
        card.append(detail);
        const actions = U.node("div", null, "admin-order-actions");
        function action(text, path, body) {
          const b = U.node("button", text, "button dark small");
          b.onclick = async () => {
            b.disabled = true;
            error("");
            try {
              await U.post(path, body || {});
              await load();
            } catch (e) {
              error(e.message);
              b.disabled = false;
            }
          };
          actions.append(b);
        }
        if (o.status === "PENDING_PAYMENT" && o.method === "CASH")
          action(
            "Xác nhận đã thu tiền thử",
            "/admin/orders/" + o.id + "/confirm-counter",
          );
        if (o.status === "REFUND_PENDING") {
          action(
            "Duyệt hoàn toàn bộ (demo)",
            "/admin/orders/" + o.id + "/refund",
            { approved: true },
          );
          action("Từ chối yêu cầu hoàn", "/admin/orders/" + o.id + "/refund", {
            approved: false,
          });
        }
        const pending = (o.attempts || []).find(
          (a) =>
            ["CREATED", "PROCESSING", "EXPIRED"].includes(a.status) &&
            a.method !== "CASH",
        );
        if (pending)
          action(
            "Kiểm thử: thành công đến muộn",
            "/payments/" + pending.id + "/confirm",
            { scenario: "LATE_SUCCESS" },
          );
        card.append(actions);
        return card;
      }),
    );
    if (!selected.length)
      U.empty($("commerceAdminOrders"), "Chưa có đơn phù hợp.");
    if (selected.length > 100)
      $("commerceAdminOrders").append(
        U.node(
          "p",
          "Đang hiển thị 100 đơn mới nhất. Dùng tìm kiếm để thu hẹp.",
          "fine-print",
        ),
      );
  }
  document.addEventListener("DOMContentLoaded", () => {
    $("refreshCommerceOrders").onclick = load;
    $("commerceOrderSearch").oninput = render;
    $("commerceOrderFilter").onchange = render;
    $("checkInForm").onsubmit = async (e) => {
      e.preventDefault();
      const form = e.currentTarget,
        b = form.querySelector("button");
      b.disabled = true;
      error("");
      try {
        const token = form.elements.token.value.trim().replace(/^N6-DEMO:/, "");
        const order = await U.post("/admin/check-in", {
          token,
          collectCombo: form.elements.collectCombo.checked,
        });
        $("checkInResult").textContent =
          "Đã ghi nhận sử dụng vé thuộc đơn " + order.code;
        await load();
      } catch (err) {
        error(err.message);
      } finally {
        b.disabled = false;
      }
    };
    Cinema.me()
      .then((user) => {
        if (user?.role === "ADMIN") load();
      })
      .catch((e) => error(e.message));
  });
})();
