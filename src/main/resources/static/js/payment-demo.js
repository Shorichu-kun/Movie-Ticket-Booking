"use strict";
(async () => {
  const U = CommerceUI,
    $ = (id) => document.getElementById(id),
    params = new URLSearchParams(location.search);
  const attemptId = params.get("attemptId"),
    orderId = params.get("orderId");
  let busy = false,
    order;
  const names = {
    MOMO: "MoMo — mô phỏng",
    ZALOPAY: "ZaloPay — mô phỏng",
    SHOPEEPAY: "ShopeePay — mô phỏng",
    VNPAY: "VNPAY-QR — mô phỏng",
    ATM: "Thẻ nội địa — mô phỏng",
    CARD: "Thẻ quốc tế — mô phỏng",
    TRANSFER: "Chuyển khoản — mô phỏng",
  };
  try {
    if (!(await U.customer())) return;
    if (!attemptId || !orderId)
      throw new Error(
        "Thiếu mã giao dịch. Vui lòng mở giao dịch từ chi tiết đơn.",
      );
    order = await U.api("/orders/" + encodeURIComponent(orderId));
    const attempt = (order.attempts || []).find((a) => a.id === attemptId);
    if (!attempt)
      throw new Error("Không tìm thấy lần thanh toán thuộc đơn này.");
    $("backToOrder").href = "/order.html?id=" + encodeURIComponent(orderId);
    $("providerTitle").textContent = names[attempt.method] || "Thanh toán demo";
    $("providerSymbol").textContent = attempt.method;
    $("paymentReference").textContent =
      `${order.code || orderId} / ${attempt.id}`;
    $("paymentAmount").textContent = Cinema.money(attempt.amount);
    if (["VNPAY", "TRANSFER"].includes(attempt.method)) {
      $("paymentQr").src =
        "/api/commerce/payments/" + encodeURIComponent(attempt.id) + "/qr";
      $("paymentQr").hidden = false;
    }
    $("paymentDeadline").textContent =
      "Hạn giao dịch: " + U.time(attempt.expiresAt || order.expiresAt);
    if (
      ["PAID", "REFUNDED", "REFUND_PENDING"].includes(order.status) ||
      !["CREATED", "PROCESSING"].includes(attempt.status)
    ) {
      $("scenarioButtons").hidden = true;
      U.toast(
        "Giao dịch đã có kết quả: " +
          (U.labels[attempt.status] || attempt.status) +
          ". Xem trạng thái mới nhất trong chi tiết đơn.",
      );
    }
    document.querySelectorAll("[data-scenario]").forEach(
      (button) =>
        (button.onclick = async () => {
          if (busy) return;
          busy = true;
          document
            .querySelectorAll("[data-scenario]")
            .forEach((b) => (b.disabled = true));
          U.error("");
          try {
            const result = await U.post(
              "/payments/" + encodeURIComponent(attemptId) + "/confirm",
              { scenario: button.dataset.scenario },
            );
            if (
              button.dataset.scenario === "PROCESSING" &&
              result.status === "PENDING_PAYMENT"
            ) {
              U.toast(
                "Giao dịch đang chờ xử lý thử. Bạn có thể xác nhận kết quả cuối hoặc theo dõi trong chi tiết đơn.",
              );
            } else
              location.assign("/order.html?id=" + encodeURIComponent(orderId));
          } catch (e) {
            U.error(e.message);
          } finally {
            busy = false;
            document
              .querySelectorAll("[data-scenario]")
              .forEach((b) => (b.disabled = false));
          }
        }),
    );
  } catch (e) {
    U.error(e.message);
    $("scenarioButtons").hidden = true;
  }
})();
