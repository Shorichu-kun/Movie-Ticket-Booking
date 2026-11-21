"use strict";
(() => {
  const U = CommerceUI,
    $ = (id) => document.getElementById(id);
  let user,
    data,
    catalog,
    page = 1,
    noticeTimer;
  const pageSize = 8;
  const tiers = [
    ["MEMBER", "Member", 0, 3],
    ["SILVER", "Silver", 1500000, 4],
    ["GOLD", "Gold", 3000000, 5],
    ["PLATINUM", "Platinum", 6000000, 7],
  ];
  const sideLinks = [...document.querySelectorAll(".side a")];
  const sectionAliases = { personal: "profile", transactions: "orders" };

  function currentSection() {
    const hash = location.hash.slice(1);
    const section = Object.hasOwn(sectionAliases, hash) ? sectionAliases[hash] : hash;
    const current = sideLinks.find((link) => link.hash === "#" + section) || sideLinks[0];
    for (const link of sideLinks) {
      link.removeAttribute("aria-current");
    }
    current.setAttribute("aria-current", "location");
    return document.getElementById(section);
  }

  function tableEmpty(container, columns, message) {
    const row = U.node("tr");
    const cell = U.node("td", message, "empty-message");
    cell.colSpan = columns;
    row.append(cell);
    container.replaceChildren(row);
  }

  function orderRow(order) {
    const row = U.node("tr");
    const movie = U.node("td");
    movie.append(
      U.node("strong", order.movie?.title || "Đơn vé"),
      U.node("span", order.code || order.id, "fine-print mono"),
    );
    const screening = U.node("td", U.time(order.showtime?.startTime));
    const seats = (order.seatLabels || order.seatIds || []).join(", ");
    const screeningDetails = [order.room?.name, seats].filter(Boolean).join(" · ");
    if (screeningDetails) screening.append(U.node("span", screeningDetails, "fine-print"));
    const status = U.node("td");
    const statusColors = { PAID: "positive", REFUNDED: "negative", CANCELLED: "negative" };
    status.append(
      U.node("span", U.labels[order.status] || order.status, "status-badge " + (statusColors[order.status] || "")),
    );
    const details = U.node("td");
    const link = U.link("Xem", "/order.html?id=" + encodeURIComponent(order.id));
    link.setAttribute("aria-label", "Xem đơn " + (order.code || order.id));
    details.append(link);
    row.append(movie, screening, status, U.node("td", Cinema.money(order.quote?.total), "amount"), details);
    return row;
  }

  function voucherCard(v) {
    const card = U.node("article", null, "voucher-card voucher");
    card.append(
      U.node("span", U.labels[v.status] || v.status, "status-badge"),
      U.node("h3", v.title || v.code),
      U.node("code", v.code, "inline-code"),
      U.node("p", "Hạn: " + U.time(v.expiresAt), "fine-print"),
    );
    const campaign = (catalog.offers || []).find((c) => c.code === v.code);
    card.append(
      U.node(
        "p",
        campaign?.description ||
          "Voucher combo: áp dụng khi đơn có combo, không cộng dồn ưu đãi khác.",
        "muted",
      ),
    );
    if (v.status === "AVAILABLE")
      card.append(U.link("Chọn phim & sử dụng →", "/welcome.html#movies"));
    return card;
  }
  function render() {
    const m = data.membership,
      c = user.customer;
    $("memberName").textContent = c.fullName;
    $("memberEmail").textContent = c.email;
    $("memberAvatar").textContent = c.fullName.trim().split(/\s+/)
      .slice(-2).map((part) => Array.from(part)[0]).join("").toLocaleUpperCase("vi");
    const tier = m.tier.toLowerCase();
    const customerType = c.customerType.toLowerCase();
    document.body.dataset.tier = tier === "member" ? customerType : tier;
    $("overviewTier").textContent = m.tier;
    $("cardName").textContent = c.fullName;
    $("cardId").textContent = "THÀNH VIÊN / " + c.id;
    const progress = m.nextThreshold
      ? Math.min(100, (Number(m.yearlySpend) / Number(m.nextThreshold)) * 100)
      : 100;
    $("memberProgressFill").style.width = progress + "%";
    $("memberProgress").setAttribute(
      "aria-valuenow",
      String(Math.round(progress)),
    );
    $("nextTierText").textContent = m.nextTier
      ? `Còn ${Cinema.money(m.remainingToNext)} để lên ${m.nextTier}. Chi tiêu được xét trong 365 ngày gần nhất.`
      : "Bạn đang ở hạng cao nhất. Cảm ơn bạn đã đồng hành cùng Neon Cinema.";
    $("cardPoints").textContent = m.availablePoints;
    $("availablePoints").textContent = m.availablePoints;
    $("paidOrders").textContent = data.orders.filter((order) => order.status === "PAID").length;
    $("memberSpend").textContent = Cinema.money(m.yearlySpend);
    const availableVouchers = data.vouchers.filter((voucher) => voucher.status === "AVAILABLE");
    $("voucherCount").textContent = availableVouchers.length;
    const expiring = $("expiringVouchers");
    expiring.replaceChildren(
      ...availableVouchers.sort(byExpiry).slice(0, 2).map((voucher) =>
        U.link(
          `${voucher.title || voucher.code} · Hạn ${U.time(voucher.expiresAt)}`,
          "#vouchers",
          "perk-link",
        ),
      ),
    );
    expiring.hidden = !availableVouchers.length;
    const upcoming = data.orders
      .filter(
        (o) =>
          o.status === "PAID" && U.date(o.showtime?.startTime) > new Date(),
      )
      .sort(
        (a, b) => U.date(a.showtime.startTime) - U.date(b.showtime.startTime),
      )
      .slice(0, 2);
    $("upcomingOrders").replaceChildren(...upcoming.map(U.orderCard));
    if (!upcoming.length)
      U.empty(
        $("upcomingOrders"),
        "Chưa có buổi phim sắp tới. Chọn một bộ phim và lên lịch nhé.",
      );
    $("pointsAvailable").textContent = m.availablePoints;
    $("pointsReserved").textContent = m.reservedPoints;
    $("pointsExpiring").textContent = m.expiringPoints;
    $("pointDebt").hidden = Number(m.points) >= 0;
    $("pointDebt").textContent =
      `Có ${Math.abs(Number(m.points))} điểm cần bù do hoàn đơn có điểm thưởng đã được sử dụng. Điểm tích mới sẽ bù khoản này trước khi trở thành điểm khả dụng.`;
    $("pointLedger").replaceChildren(
      ...[...(data.pointEntries || [])].reverse().map((e) => {
        const tr = U.node("tr");
        tr.append(
          U.node("td", U.time(e.createdAt)),
          U.node("td", e.description || e.type),
          U.node("td", (e.points > 0 ? "+" : "") + e.points),
          U.node("td", e.expiresAt ? U.time(e.expiresAt) : "—"),
        );
        return tr;
      }),
    );
    if (!$("pointLedger").children.length)
      tableEmpty($("pointLedger"), 4, "Chưa có giao dịch điểm.");
    $("pointRewards").replaceChildren(
      ...(catalog.rewards || []).map((r) => {
        const card = U.node("article", null, "voucher-card");
        card.append(
          U.node("h3", r.title),
          U.node("p", `${r.points} điểm · Dùng trong đơn có combo`),
        );
        const b = U.node(
          "button",
          m.availablePoints >= r.points
            ? "Đổi điểm nhận voucher →"
            : "Chưa đủ điểm",
          "button",
        );
        b.disabled = m.availablePoints < r.points;
        b.onclick = () =>
          mutate(b, async () => {
            data = await U.post(
              "/rewards/redeem",
              { rewardId: r.id },
              U.key(
                "reward-" + r.id,
                JSON.stringify({ count: data.pointEntries.length }),
              ),
            );
            render();
            showNotice("Đã đổi điểm và thêm voucher vào ví.");
          });
        card.append(b);
        return card;
      }),
    );
    $("membershipExplain").textContent =
      `Hạng hiện tại: ${m.tier} · Tỷ lệ tích điểm ${Math.round(Number(m.earnRate) * 100)}% trên tiền đủ điều kiện. Tổng chi tiêu kỳ xét: ${Cinema.money(m.yearlySpend)}.`;
    $("tierCards").replaceChildren(
      ...tiers.map(([code, title, threshold, rate]) => {
        const card = U.node(
          "article",
          null,
          "tier-card" +
            (String(m.tier).toUpperCase() === code ? " current" : ""),
        );
        card.append(
          U.node(
            "p",
            String(m.tier).toUpperCase() === code
              ? "HẠNG HIỆN TẠI"
              : "NEON CINEMA",
            "label",
          ),
          U.node("h3", title),
          U.node(
            "p",
            threshold
              ? "Từ " + Cinema.money(threshold) + " / 365 ngày"
              : "Đăng ký miễn phí",
          ),
          U.node("strong", "Tích " + rate + "%"),
          U.node(
            "p",
            "Ví voucher, quyền lợi theo chương trình và quà sinh nhật đủ điều kiện.",
            "fine-print",
          ),
        );
        return card;
      }),
    );
    $("legacyBenefit").textContent =
      c.customerType === "VIP"
        ? "Bạn có ưu đãi VIP cũ 20% trên vé và phụ phí. Quyền lợi này độc lập với hạng hội viên mới."
        : c.customerType === "STUDENT"
          ? "Bạn có hồ sơ sinh viên, ưu đãi giá theo cấu hình hiện tại. Kiểm tra mã SV không đồng nghĩa xác minh với nhà trường."
          : "Bạn đang dùng hồ sơ khách thường. Hạng hội viên sẽ tăng theo chi tiêu đủ điều kiện.";
    $("accountNotifications").replaceChildren(
      ...[...(data.notifications || [])].reverse().map((n) => {
        const card = U.node("article", null, "order-card");
        card.append(
          U.node("p", U.time(n.createdAt), "fine-print"),
          U.node("h3", n.title),
          U.node("p", n.message),
        );
        if (n.orderId)
          card.append(
            U.link(
              "Xem đơn →",
              "/order.html?id=" + encodeURIComponent(n.orderId),
            ),
          );
        return card;
      }),
    );
    if (!$("accountNotifications").children.length)
      U.empty(
        $("accountNotifications"),
        "Chưa có thông báo. Các cập nhật mới sẽ xuất hiện tại đây.",
      );
    renderOrders();
    renderVouchers();
  }
  function renderProfile() {
    const fields = $("accountProfileForm").elements;
    for (const name of ["fullName", "email", "phone"]) {
      fields[name].value = user.customer[name] || "";
    }
  }

  function renderPreferences() {
    const pref = data.preferences || {};
    const fields = $("preferencesForm").elements;
    fields.birthDate.value = pref.birthDate || "";
    fields.birthDate.readOnly = Boolean(pref.birthDate);
    fields.favoriteGenre.value = pref.favoriteGenre || "";
    fields.notifyShowtime.checked = Boolean(pref.notifyShowtime);
    fields.notifyOffers.checked = Boolean(pref.notifyOffers);
  }

  function renderOrders() {
    const query = $("orderSearch").value.trim().toLocaleLowerCase("vi"),
      filter = $("orderFilter").value;
    const orders = data.orders
      .filter(
        (o) =>
          (!filter || o.status === filter) &&
          (!query ||
            `${o.code} ${o.id} ${o.movie?.title}`
              .toLocaleLowerCase("vi")
              .includes(query)),
      )
      .sort((a, b) => U.date(b.createdAt) - U.date(a.createdAt));
    const pages = Math.max(1, Math.ceil(orders.length / pageSize));
    page = Math.min(page, pages);
    $("accountOrders").replaceChildren(
      ...orders.slice((page - 1) * pageSize, page * pageSize).map(orderRow),
    );
    if (!orders.length) tableEmpty($("accountOrders"), 5, "Không có đơn phù hợp.");
    $("orderPagination").replaceChildren();
    if (orders.length > pageSize) {
      const prev = U.node("button", "← Trang trước", "text-button"),
        next = U.node("button", "Trang sau →", "text-button");
      prev.disabled = page === 1;
      next.disabled = page === pages;
      prev.onclick = () => {
        page--;
        renderOrders();
      };
      next.onclick = () => {
        page++;
        renderOrders();
      };
      $("orderPagination").append(
        prev,
        U.node("span", `${page} / ${pages}`, "fine-print"),
        next,
      );
    }
  }
  function byExpiry(a, b) {
    return U.date(a.expiresAt) - U.date(b.expiresAt);
  }

  function renderVouchers() {
    const f = $("voucherFilter").value;
    const vouchers = data.vouchers.filter((v) => !f || v.status === f).sort(byExpiry);
    $("voucherWallet").replaceChildren(...vouchers.map(voucherCard));
    if (!vouchers.length)
      U.empty($("voucherWallet"), "Chưa có voucher ở trạng thái này.");
  }
  function showNotice(message) {
    U.toast(message);
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => { $("notice").hidden = true; }, 4500);
  }

  async function mutate(button, fn) {
    button.disabled = true;
    U.error("");
    $("notice").hidden = true;
    try {
      await fn();
    } catch (e) {
      U.error(e.message);
    } finally {
      button.disabled = false;
    }
  }
  window.addEventListener("hashchange", currentSection);
  $("orderSearch").oninput = () => {
    page = 1;
    renderOrders();
  };
  $("orderFilter").onchange = () => {
    page = 1;
    renderOrders();
  };
  $("voucherFilter").onchange = renderVouchers;
  $("accountLogout").onclick = () =>
    Cinema.logout().catch((e) => U.error(e.message));
  $("claimVoucherForm").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    mutate(form.querySelector("button"), async () => {
      data = await U.post("/vouchers/claim", {
        code: form.elements.code.value.trim(),
      });
      form.reset();
      render();
      showNotice("Đã nhận voucher vào ví.");
    });
  };
  $("accountProfileForm").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    mutate(form.querySelector("button"), async () => {
      const values = U.json(form);
      values.customerType = user.customer.customerType;
      values.studentId = user.customer.studentId || null;
      user = await Cinema.api("/api/auth/profile", {
        method: "PUT",
        body: JSON.stringify(values),
      });
      Cinema.user = user;
      form.elements.currentPassword.value = "";
      renderProfile();
      render();
      showNotice("Đã cập nhật hồ sơ.");
    });
  };
  $("preferencesForm").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    mutate(form.querySelector("button"), async () => {
      await U.api("/me/preferences", {
        method: "PUT",
        body: JSON.stringify({
          birthDate: form.elements.birthDate.value || null,
          favoriteGenre: form.elements.favoriteGenre.value,
          notifyShowtime: form.elements.notifyShowtime.checked,
          notifyOffers: form.elements.notifyOffers.checked,
        }),
      });
      data = await U.api("/me");
      renderPreferences();
      render();
      showNotice("Đã lưu tùy chọn tài khoản.");
    });
  };
  $("passwordForm").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    $("notice").hidden = true;
    if (form.elements.newPassword.value !== form.elements.confirmation.value)
      return U.error("Hai mật khẩu mới chưa khớp nhau.");
    mutate(form.querySelector("button"), async () => {
      await Cinema.api("/api/auth/change-password", {
        method: "POST",
        body: JSON.stringify({
          currentPassword: form.elements.currentPassword.value,
          newPassword: form.elements.newPassword.value,
        }),
      });
      form.reset();
      Cinema.user = null;
      Cinema.resetCsrf();
      location.assign("/welcome.html?login=1&passwordChanged=1");
    });
  };
  (async () => {
    try {
      user = await U.customer();
      if (!user) return;
      [data, catalog] = await Promise.all([U.api("/me"), U.api("/catalog")]);
      renderProfile();
      renderPreferences();
      render();
      $("memberAvatar").hidden = false;
      $("accountLoading").hidden = true;
      $("accountContent").hidden = false;
      const section = currentSection();
      if (section) requestAnimationFrame(() => section.scrollIntoView());
      try {
        const tickets = await Cinema.api("/api/bookings/mine");
        $("legacyTickets").replaceChildren(
          ...tickets.map((t) => {
            const card = U.node("article", null, "order-card");
            card.append(
              U.node("span", t.id, "mono"),
              U.node("p", `Suất ${t.showtimeId} · Ghế ${t.seatId}`),
              U.node(
                "p",
                `${Cinema.money(t.price)} · ${U.labels[t.paymentStatus] || t.paymentStatus}`,
              ),
            );
            return card;
          }),
        );
        if (!tickets.length) U.empty($("legacyTickets"), "Không có vé cũ.");
      } catch (e) {
        U.empty($("legacyTickets"), e.message);
      }
    } catch (e) {
      U.error(e.message);
    } finally {
      $("accountLoading").hidden = true;
    }
  })();
})();
