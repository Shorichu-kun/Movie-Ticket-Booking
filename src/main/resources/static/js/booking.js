"use strict";
(() => {
  const U = CommerceUI,
    $ = (id) => document.getElementById(id);
  const state = {
    step: 1,
    movies: [],
    shows: [],
    rooms: [],
    catalog: {},
    selected: new Set(),
    seats: [],
    hold: null,
    quote: null,
    quoteVersion: 0,
    busy: false,
    combos: [],
    method: "MOMO",
    member: null,
    user: null,
    serverOffset: 0,
    order: null,
  };
  const params = new URLSearchParams(location.search);
  let showId = params.get("showtimeId"),
    timer;
  const show = () => state.shows.find((s) => s.id === showId);
  const movie = () => state.movies.find((m) => m.id === show()?.movieId);
  const room = () => state.rooms.find((r) => r.id === show()?.roomId);
  const future = (s) => U.date(s.startTime).getTime() > Date.now() + 300000;
  const option = (value, text) => {
    const e = U.node("option", text);
    e.value = value;
    return e;
  };
  function setStep(step) {
    state.step = step;
    document
      .querySelectorAll("[data-panel]")
      .forEach((p) => (p.hidden = Number(p.dataset.panel) !== step));
    document.querySelectorAll("[data-step]").forEach((p) => {
      p.classList.toggle("active", Number(p.dataset.step) === step);
      p.classList.toggle("done", Number(p.dataset.step) < step);
      if (Number(p.dataset.step) === step)
        p.setAttribute("aria-current", "step");
      else p.removeAttribute("aria-current");
    });
    U.error("");
    summary();
    document
      .querySelector("h1")
      .scrollIntoView({ behavior: "smooth", block: "start" });
  }
  function summary() {
    $("summaryMovie").textContent = movie()?.title || "Chọn bộ phim yêu thích";
    $("summaryShow").textContent = show()
      ? `${U.time(show().startTime)} · ${room()?.name || ""}`
      : "";
    $("summarySeats").textContent = state.selected.size
      ? "Ghế: " + selectedLabels().join(", ")
      : "";
    $("holdClock").hidden = !state.hold || state.hold.status !== "ACTIVE";
    $("releaseHold").hidden = !state.hold || state.hold.status !== "ACTIVE";
    U.price($("priceSummary"), state.quote);
  }
  const selectedLabels = () =>
    [...state.selected].map((id) => {
      const s = state.seats.find((s) => s.id === id);
      return s ? s.rowLabel + s.seatNumber : id;
    });
  function saveDraft() {
    if (state.hold)
      sessionStorage.setItem(
        "n6-checkout",
        JSON.stringify({
          holdId: state.hold.id,
          combos: state.combos,
          method: state.method,
          points: $("pointsInput").value,
          voucherCode: $("voucherSelect").value,
        }),
      );
  }
  function renderDates() {
    const shows = state.shows.filter(
      (s) => s.movieId === $("movieSelect").value && future(s),
    );
    const dates = [
      ...new Set(shows.map((s) => s.startTime.slice(0, 10))),
    ].sort();
    const oldDate = show()?.startTime.slice(0, 10);
    $("dateSelect").replaceChildren(
      ...dates.map((d) =>
        option(
          d,
          new Intl.DateTimeFormat("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh",
            weekday: "short",
            day: "2-digit",
            month: "2-digit",
          }).format(new Date(d + "T00:00:00+07:00")),
        ),
      ),
    );
    if (dates.includes(oldDate)) $("dateSelect").value = oldDate;
    renderShows();
  }
  function renderShows() {
    const choices = state.shows
      .filter(
        (s) =>
          s.movieId === $("movieSelect").value &&
          s.startTime.startsWith($("dateSelect").value) &&
          future(s),
      )
      .sort((a, b) => U.date(a.startTime) - U.date(b.startTime));
    if (!choices.some((s) => s.id === showId)) showId = choices[0]?.id || null;
    $("showChoices").replaceChildren(
      ...choices.map((s) => {
        const b = U.node("button", null, "choice");
        b.type = "button";
        b.setAttribute("aria-pressed", String(s.id === showId));
        b.append(
          U.node("strong", s.startTime.slice(11, 16)),
          U.node(
            "small",
            state.rooms.find((r) => r.id === s.roomId)?.name || s.roomId,
          ),
          U.node("small", Cinema.money(s.price)),
        );
        b.onclick = () => {
          showId = s.id;
          renderShows();
        };
        return b;
      }),
    );
    if (!choices.length)
      U.empty($("showChoices"), "Chưa có suất chiếu còn mở bán cho ngày này.");
    $("showDescription").textContent = movie()
      ? `${movie().genre || "Điện ảnh"} · ${movie().duration} phút · N6 Cinema`
      : "";
    $("toSeats").disabled = !showId;
    summary();
  }
  async function loadSeats() {
    const data = await U.api("/shows/" + encodeURIComponent(showId) + "/seats");
    state.seats = data.seats;
    if (data.serverTime)
      state.serverOffset = U.date(data.serverTime).getTime() - Date.now();
    renderSeats();
    summary();
  }
  function renderSeats() {
    const rows = new Map();
    state.seats.forEach((s) => {
      if (!rows.has(s.rowLabel)) rows.set(s.rowLabel, []);
      rows.get(s.rowLabel).push(s);
    });
    $("seatMap").replaceChildren();
    const count = Math.max(
      1,
      ...[...rows.values()].map((seats) =>
        Math.max(...seats.map((s) => s.seatNumber)),
      ),
    );
    for (const [label, seats] of rows) {
      const line = U.node("div", null, "seat-line");
      line.style.gridTemplateColumns = `20px repeat(${count},minmax(27px,1fr))`;
      line.append(U.node("span", label, "seat-row-label"));
      for (let n = 1; n <= count; n++) {
        const seat = seats.find((s) => s.seatNumber === n);
        if (!seat) {
          line.append(U.node("span"));
          continue;
        }
        const b = U.node(
          "button",
          String(n),
          "seat-button " +
            (seat.type === "VIP"
              ? "vip"
              : seat.type === "COUPLE"
                ? "couple"
                : ""),
        );
        b.type = "button";
        b.disabled =
          state.busy ||
          (!seat.available &&
            !seat.heldByMe &&
            !state.hold?.seatIds.includes(seat.id));
        b.setAttribute("aria-pressed", String(state.selected.has(seat.id)));
        b.setAttribute(
          "aria-label",
          `Ghế ${label}${n} · ${seat.type} · ${Cinema.money(seat.price)}${b.disabled ? " · Không khả dụng" : ""}`,
        );
        b.onclick = () => {
          if (state.selected.has(seat.id)) state.selected.delete(seat.id);
          else if (state.selected.size < 8) state.selected.add(seat.id);
          else return U.error("Bạn có thể chọn tối đa 8 vị trí ghế.");
          state.quote = null;
          renderSeats();
          summary();
        };
        line.append(b);
      }
      $("seatMap").append(line);
    }
    $("seatSelection").textContent = selectedLabels().join(", ") || "Chưa chọn";
    $("holdSeats").disabled = state.busy || !state.selected.size;
  }
  async function holdSeats() {
    if (!state.user) {
      $("checkoutLogin").hidden = false;
      $("checkoutGrid").hidden = true;
      return;
    }
    state.busy = true;
    renderSeats();
    U.error("");
    try {
      state.hold =
        state.hold?.status === "ACTIVE"
          ? await U.api("/holds/" + state.hold.id + "/seats", {
              method: "PUT",
              body: JSON.stringify({ seatIds: [...state.selected] }),
            })
          : await U.post("/holds", {
              showtimeId: showId,
              seatIds: [...state.selected],
            });
      if (state.hold.serverTime)
        state.serverOffset =
          U.date(state.hold.serverTime).getTime() - Date.now();
      saveDraft();
      startClock();
      setStep(3);
      await quote();
    } catch (e) {
      U.error(e.message);
      await loadSeats().catch(() => {});
    } finally {
      state.busy = false;
      renderSeats();
    }
  }
  function startClock() {
    clearInterval(timer);
    const tick = () => {
      if (!state.hold || state.hold.status !== "ACTIVE") return;
      const left = Math.max(
        0,
        Math.ceil(
          (U.date(state.hold.expiresAt).getTime() -
            Date.now() -
            state.serverOffset) /
            1000,
        ),
      );
      $("countdown").textContent =
        String(Math.floor(left / 60)).padStart(2, "0") +
        ":" +
        String(left % 60).padStart(2, "0");
      $("holdClock").classList.toggle("urgent", left < 60);
      $("toCheckout").disabled = !left;
      if (left) $("backToSeats").textContent = "← Xem lại ghế";
      if (!left) {
        clearInterval(timer);
        state.hold.status = "EXPIRED";
        $("payButton").disabled = true;
        $("toCheckout").disabled = true;
        U.error(
          "Lượt giữ ghế đã hết hạn. Vui lòng chọn lại ghế để cập nhật tình trạng mới nhất.",
        );
        sessionStorage.removeItem("n6-checkout");
        $("backToSeats").textContent = "← Chọn lại ghế";
      }
    };
    tick();
    timer = setInterval(tick, 1000);
    summary();
  }
  function renderProducts() {
    $("products").replaceChildren(
      ...(state.catalog.products || []).map((p) => {
        const card = U.node("article", null, "product-card");
        card.dataset.productId = p.id;
        const art = U.node("div", "🍿 🥤", "product-art");
        art.setAttribute("aria-hidden", "true");
        card.append(
          art,
          U.node("h3", p.name),
          U.node("p", p.description),
          U.node("strong", Cinema.money(p.price)),
        );
        const controls = U.node("div", null, "product-controls"),
          variantLabel = U.node("label", "Lựa chọn", "field"),
          select = U.node("select"),
          qtyLabel = U.node("label", "Số lượng", "field"),
          qty = U.node("input");
        select.setAttribute("aria-label", "Lựa chọn " + p.name);
        const variants = p.variants?.length ? p.variants : ["Mặc định"];
        variants.forEach((v) => select.append(option(v, v)));
        qty.type = "number";
        qty.min = "0";
        qty.max = "5";
        qty.value =
          state.combos.find((c) => c.productId === p.id)?.quantity || 0;
        qty.setAttribute("aria-label", "Số lượng " + p.name);
        const saved = state.combos.find((c) => c.productId === p.id);
        if (saved) select.value = saved.variant;
        const change = () => {
          state.combos = state.combos.filter((c) => c.productId !== p.id);
          const quantity = Number(qty.value);
          if (Number.isInteger(quantity) && quantity > 0)
            state.combos.push({
              productId: p.id,
              quantity,
              variant: select.value,
            });
          saveDraft();
          quote();
        };
        qty.oninput = change;
        qty.onchange = change;
        select.onchange = change;
        variantLabel.append(select);
        qtyLabel.append(qty);
        controls.append(variantLabel, qtyLabel);
        card.append(controls);
        return card;
      }),
    );
  }
  function payload() {
    state.combos = [...$("products").querySelectorAll("[data-product-id]")]
      .map((card) => ({
        productId: card.dataset.productId,
        quantity: Number(card.querySelector("input").value),
        variant: card.querySelector("select").value,
      }))
      .filter((combo) => combo.quantity > 0);
    return {
      holdId: state.hold?.id,
      combos: state.combos,
      voucherCode: $("voucherSelect").value || null,
      points: Number($("pointsInput").value || 0),
      method: state.method,
    };
  }
  async function quote() {
    if (!state.hold || state.hold.status !== "ACTIVE") return;
    const version = ++state.quoteVersion;
    $("payButton").disabled = true;
    try {
      const value = await U.post("/quote", payload());
      if (version !== state.quoteVersion) return;
      state.quote = value;
      if (value.expiresAt) state.hold.expiresAt = value.expiresAt;
      U.price($("checkoutPrice"), value);
      summary();
      saveDraft();
      $("pointsEstimate").textContent =
        `Dự kiến nhận ${value.pointsEarned || 0} điểm sau khi đơn thanh toán thành công. Điểm cuối cùng được ghi nhận bởi máy chủ.`;
      $("payButton").textContent =
        Number(value.total) === 0
          ? "Xác nhận bằng điểm/ưu đãi"
          : state.method === "CASH"
            ? "Xác nhận giữ chỗ tại quầy"
            : `Thanh toán thử ${Cinema.money(value.total)} →`;
      $("payButton").disabled = state.busy;
      U.error("");
    } catch (e) {
      if (version === state.quoteVersion) {
        state.quote = null;
        U.error(e.message);
        $("payButton").textContent = "Cập nhật giá để tiếp tục";
      }
    }
  }
  async function loadMember() {
    state.member = await U.api("/me");
    $("pointsHelp").textContent =
      `Khả dụng: ${state.member.membership.availablePoints} điểm · 1 điểm = 1.000đ · Tối thiểu 10 điểm khi sử dụng.`;
    $("pointsInput").max = state.member.membership.availablePoints;
    const old = $("voucherSelect").value;
    $("voucherSelect").replaceChildren(
      option("", "Ưu đãi tự động, không chọn voucher"),
      ...(state.member.vouchers || [])
        .filter((v) => v.status === "AVAILABLE")
        .map((v) =>
          option(
            v.code || v.campaignCode,
            `${v.code || v.campaignCode} · ${v.title || v.description || ""}`,
          ),
        ),
    );
    if ([...$("voucherSelect").options].some((o) => o.value === old))
      $("voucherSelect").value = old;
  }
  function renderMethods() {
    $("paymentMethods").replaceChildren(
      ...(state.catalog.methods || [])
        .filter((m) => m.code !== "LOYALTY")
        .map((m) => {
          const label = U.node("label", null, "method-option"),
            input = U.node("input");
          input.type = "radio";
          input.name = "method";
          input.value = m.code;
          input.checked = m.code === state.method;
          input.onchange = () => {
            state.method = m.code;
            state.order = null;
            $("methodHelp").textContent =
              m.code === "CASH"
                ? "Chỉ áp dụng suất còn ít nhất 60 phút; giữ tới 15 phút từ lượt giữ đầu. Đây chưa phải vé vào cửa."
                : "Mô phỏng nội bộ N6; không yêu cầu thẻ, tài khoản ngân hàng hoặc OTP thật.";
            quote();
          };
          label.append(input, U.node("span", m.label));
          return label;
        }),
    );
  }
  async function pay(event) {
    event.preventDefault();
    if (state.busy || !state.quote || !event.currentTarget.reportValidity())
      return;
    state.busy = true;
    $("payButton").disabled = true;
    U.error("");
    try {
      const request = {
        ...payload(),
        contact: {
          name: event.currentTarget.elements.name.value,
          email: event.currentTarget.elements.email.value,
          phone: event.currentTarget.elements.phone.value,
        },
      };
      if (!state.order)
        state.order = await U.post(
          "/orders",
          request,
          U.key("order-" + state.hold.id, JSON.stringify(request)),
        );
      sessionStorage.removeItem("n6-checkout");
      if (state.order.status === "PAID") {
        location.assign("/order.html?id=" + encodeURIComponent(state.order.id));
        return;
      }
      const attempt = await U.post(
        "/orders/" + state.order.id + "/payments",
        { method: state.method },
        U.key(
          "payment-" + state.order.id,
          JSON.stringify({ method: state.method }),
        ),
      );
      if (state.method === "CASH")
        location.assign("/order.html?id=" + encodeURIComponent(state.order.id));
      else
        location.assign(
          "/payment-demo.html?attemptId=" +
            encodeURIComponent(attempt.id) +
            "&orderId=" +
            encodeURIComponent(state.order.id),
        );
    } catch (e) {
      U.error(e.message);
      state.busy = false;
      $("payButton").disabled = false;
    }
  }
  async function release(silent = false) {
    if (state.hold?.status === "ACTIVE")
      await U.api("/holds/" + state.hold.id, { method: "DELETE" });
    state.hold = null;
    state.order = null;
    state.quote = null;
    state.selected.clear();
    sessionStorage.removeItem("n6-checkout");
    clearInterval(timer);
    $("toCheckout").disabled = false;
    if (!silent) {
      setStep(2);
      await loadSeats();
    }
  }
  async function init() {
    try {
      [state.movies, state.shows, state.rooms, state.catalog, state.user] =
        await Promise.all([
          Cinema.api("/api/movies"),
          Cinema.api("/api/showtimes"),
          Cinema.api("/api/rooms"),
          U.api("/catalog"),
          Cinema.me(),
        ]);
      if (state.user && state.user.role !== "CUSTOMER") {
        U.error("Hãy dùng tài khoản khách hàng để đặt vé.");
        $("checkoutGrid").hidden = true;
        return;
      }
      $("movieSelect").replaceChildren(
        ...state.movies
          .filter(
            (m) =>
              m.status !== "ENDED" &&
              state.shows.some((s) => s.movieId === m.id && future(s)),
          )
          .map((m) => option(m.id, m.title)),
      );
      if (show()) $("movieSelect").value = show().movieId;
      else if (params.get("movieId"))
        $("movieSelect").value = params.get("movieId");
      renderDates();
      if (state.user) {
        await loadMember();
        const c = state.user.customer;
        for (const k of ["name", "email", "phone"])
          $("paymentForm").elements[k].value = k === "name" ? c.fullName : c[k];
      }
      let draft;
      try {
        draft = JSON.parse(sessionStorage.getItem("n6-checkout"));
      } catch {}
      if (state.user && draft?.holdId) {
        try {
          const hold = await U.api(
            "/holds/" + encodeURIComponent(draft.holdId),
          );
          if (
            hold.status === "ACTIVE" &&
            (!params.get("showtimeId") ||
              params.get("showtimeId") === hold.showtimeId)
          ) {
            state.hold = hold;
            showId = hold.showtimeId;
            state.selected = new Set(hold.seatIds);
            state.combos = draft.combos || [];
            state.method = draft.method || "MOMO";
            $("pointsInput").value = draft.points || 0;
            $("voucherSelect").value = draft.voucherCode || "";
            await loadSeats();
            setStep(3);
            startClock();
          }
        } catch {
          sessionStorage.removeItem("n6-checkout");
        }
      }
      renderProducts();
      renderMethods();
      if (state.hold) await quote();
    } catch (e) {
      U.error("Không tải được thông tin đặt vé. " + e.message);
    }
  }
  $("movieSelect").onchange = renderDates;
  $("dateSelect").onchange = renderShows;
  $("toSeats").onclick = async () => {
    if (!$("ageConsent").checked)
      return U.error(
        "Vui lòng xác nhận đã kiểm tra phim, ngày chiếu và điều kiện độ tuổi.",
      );
    try {
      await loadSeats();
      setStep(2);
    } catch (e) {
      U.error(e.message);
    }
  };
  $("backToShows").onclick = async () => {
    try {
      if (state.hold) await release(true);
      setStep(1);
    } catch (e) {
      U.error(e.message);
    }
  };
  $("holdSeats").onclick = holdSeats;
  $("backToSeats").onclick = async () => {
    try {
      if (state.hold?.status !== "ACTIVE") {
        state.hold = null;
        state.selected.clear();
      }
      await loadSeats();
      setStep(2);
    } catch (e) {
      U.error(e.message);
    }
  };
  $("toCheckout").onclick = async () => {
    setStep(4);
    await quote();
  };
  $("backToCombos").onclick = () => setStep(3);
  $("updateQuote").onclick = quote;
  $("voucherSelect").onchange = quote;
  $("pointsInput").onchange = quote;
  $("paymentForm").onsubmit = pay;
  $("releaseHold").onclick = () => release().catch((e) => U.error(e.message));
  $("checkoutLoginForm").onsubmit = async (e) => {
    e.preventDefault();
    const b = e.currentTarget.querySelector("button");
    b.disabled = true;
    try {
      const user = await Cinema.api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(U.json(e.currentTarget)),
      });
      Cinema.resetCsrf();
      if (user.role !== "CUSTOMER")
        throw new Error("Vui lòng dùng tài khoản khách hàng.");
      state.user = user;
      await loadMember();
      const c = user.customer;
      for (const k of ["name", "email", "phone"])
        $("paymentForm").elements[k].value = k === "name" ? c.fullName : c[k];
      $("checkoutLogin").hidden = true;
      $("checkoutGrid").hidden = false;
      await holdSeats();
    } catch (err) {
      U.error(err.message);
    } finally {
      b.disabled = false;
    }
  };
  init();
})();
