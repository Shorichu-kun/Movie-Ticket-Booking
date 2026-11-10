"use strict";
(async () => {
  const U = CommerceUI;
  try {
    const [catalog, user] = await Promise.all([U.api("/catalog"), Cinema.me()]);
    let wallet = user?.role === "CUSTOMER" ? (await U.api("/me")).vouchers : [];
    const grid = document.getElementById("offersGrid");
    grid.replaceChildren(
      ...catalog.offers.map((offer, i) => {
        const card = U.node("article", null, "voucher-card"),
          art = U.node(
            "div",
            offer.percent
              ? Math.round(Number(offer.percent) * 100) + "%"
              : Cinema.money(offer.discountAmount),
            "offer-art" + (i % 2 ? " orange" : ""),
          );
        card.append(
          art,
          U.node("p", "ƯU ĐÃI N6 / DEMO", "eyebrow"),
          U.node("h3", offer.title),
          U.node("p", offer.description),
          U.node("code", offer.code, "inline-code"),
          U.node(
            "p",
            "Hạn chương trình: " + U.time(offer.expiresAt),
            "fine-print",
          ),
        );
        const details = U.node("details"),
          summary = U.node("summary", "Điều kiện sử dụng");
        details.append(
          summary,
          U.node(
            "p",
            `Giá trị đơn tối thiểu: ${Cinema.money(offer.minimumAmount)}. Mỗi khách nhận một lượt chương trình; không cộng dồn ưu đãi khác. Điều kiện được máy chủ kiểm tra theo đơn, ngày chiếu và lịch sử giao dịch.`,
            "fine-print",
          ),
        );
        card.append(details);
        const button = U.node(
          "button",
          wallet.some((v) => v.code === offer.code)
            ? "Đã nhận vào ví"
            : "Nhận ưu đãi →",
          "button dark small form-submit",
        );
        button.disabled = wallet.some((v) => v.code === offer.code);
        button.onclick = async () => {
          if (!user) {
            location.assign(
              "/welcome.html?login=1&returnTo=" +
                encodeURIComponent("/offers.html"),
            );
            return;
          }
          if (user.role !== "CUSTOMER")
            return U.error("Ưu đãi này dành cho tài khoản khách hàng.");
          button.disabled = true;
          try {
            const me = await U.post("/vouchers/claim", { code: offer.code });
            wallet = me.vouchers;
            button.textContent = "Đã nhận vào ví";
            U.toast("Đã thêm " + offer.code + " vào ví voucher.");
          } catch (e) {
            U.error(e.message);
            button.disabled = false;
          }
        };
        card.append(button);
        return card;
      }),
    );
  } catch (e) {
    U.error(e.message);
  }
})();
