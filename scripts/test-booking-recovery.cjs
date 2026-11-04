"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

class Element {
  constructor(tag = "div") {
    this.tag = tag;
    this.children = [];
    this.dataset = {};
    this.style = {};
    this.attributes = {};
    this.value = "";
    this.disabled = false;
    this.hidden = false;
    this.classList = { toggle() {} };
    this.elements = { name: {}, email: {}, phone: {} };
  }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) {
    this.children = children;
    if (this.tag === "select") this.value = children[0]?.value || "";
  }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  scrollIntoView() {}
  get options() { return this.children; }
  querySelectorAll() { return []; }
}

async function exerciseRecovery({ quoteFails = false } = {}) {
  let now = Date.parse("2030-01-01T02:00:00Z");
  class BrowserDate extends Date {
    static now() { return now; }
  }
  const elements = new Map();
  const selectIds = new Set(["movieSelect", "dateSelect", "voucherSelect"]);
  const element = (id) => {
    if (!elements.has(id)) elements.set(id, new Element(selectIds.has(id) ? "select" : "div"));
    return elements.get(id);
  };
  const panels = [1, 2, 3, 4].map((number) => {
    const panel = new Element();
    panel.dataset.panel = String(number);
    return panel;
  });
  const intervals = new Map();
  let nextTimer = 0, holds = 0, failQuote = false;
  const storage = new Map();
  const cinema = {
    money: String,
    me: async () => ({ role: "CUSTOMER", customer: { fullName: "Customer", email: "c@example.test", phone: "0901234567" } }),
    api: async (path) => ({
      "/api/movies": [{ id: "MOVIE", title: "Movie", duration: 120, status: "NOW_SHOWING" }],
      "/api/showtimes": [{ id: "SHOW", movieId: "MOVIE", roomId: "ROOM", startTime: "2030-01-02T20:00:00+07:00" }],
      "/api/rooms": [{ id: "ROOM", name: "Room" }],
    })[path],
  };
  const ui = {
    date: (value) => new Date(value), time: String, error() {}, price() {}, empty() {},
    node: (tag, text) => Object.assign(new Element(tag), { textContent: text }),
    api: async (path) => {
      if (path === "/catalog") return { products: [], methods: [] };
      if (path === "/me") return { membership: { availablePoints: 0 }, vouchers: [] };
      if (path.endsWith("/seats")) return { serverTime: new Date(now).toISOString(), seats: [{ id: "SEAT", rowLabel: "A", seatNumber: 1, type: "NORMAL", available: true, price: 100000 }] };
      throw new Error("Unexpected API " + path);
    },
    post: async (path) => {
      if (path === "/holds") return { id: "HOLD-" + ++holds, status: "ACTIVE", showtimeId: "SHOW", seatIds: ["SEAT"], serverTime: new Date(now).toISOString(), expiresAt: new Date(now + (holds === 1 ? 2000 : 420000)).toISOString() };
      if (path === "/quote") {
        if (failQuote) { failQuote = false; throw new Error("Temporary quote failure"); }
        return { total: 100000, pointsEarned: 3 };
      }
      throw new Error("Unexpected POST " + path);
    },
  };
  vm.runInNewContext(fs.readFileSync("Frontend/booking.js", "utf8"), {
    CommerceUI: ui, Cinema: cinema, Date: BrowserDate, URLSearchParams,
    location: { search: "?showtimeId=SHOW" },
    sessionStorage: { getItem: (key) => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: (key) => storage.delete(key) },
    document: { getElementById: element, querySelector: () => new Element(), querySelectorAll: (selector) => selector === "[data-panel]" ? panels : [] },
    setInterval: (callback) => { intervals.set(++nextTimer, callback); return nextTimer; },
    clearInterval: (id) => intervals.delete(id),
  });
  await new Promise(setImmediate);
  element("ageConsent").checked = true;
  await element("toSeats").onclick();
  const selectSeat = () => element("seatMap").children[0].children.find((child) => child.tag === "button").onclick();
  selectSeat();
  await element("holdSeats").onclick();
  now += 3000;
  [...intervals.values()].forEach((tick) => tick());
  assert.equal(element("toCheckout").disabled, true, "expired hold must disable continuation");
  await element("backToSeats").onclick();
  selectSeat();
  failQuote = quoteFails;
  await element("holdSeats").onclick();
  assert.equal(holds, 2, "recovery must obtain a fresh server hold");
  assert.equal(element("toCheckout").disabled, false, "fresh active hold must enable combo continuation");
  await element("toCheckout").onclick();
  assert.equal(panels[3].hidden, false, "recovered checkout reaches payment step");
  assert.equal(element("payButton").disabled, false, "quote retry enables payment after recovery");
}

(async () => {
  await exerciseRecovery();
  await exerciseRecovery({ quoteFails: true });
  process.stdout.write("Booking expiry recovery: 2 scenarios passed\n");
})().catch((error) => { console.error(error); process.exitCode = 1; });
