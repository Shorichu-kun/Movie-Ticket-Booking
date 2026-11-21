const API = "/api";
const LAYOUT_STORAGE_KEY = "neon_room_aisle_layouts";

let rooms = [];
let currentRoomId = null;
let currentSeats = [];
let activeTool = "STANDARD"; // STANDARD | VIP | MAINTENANCE

const roomSelect = document.getElementById("roomSelect");
const cfgRows = document.getElementById("cfgRows");
const cfgLeft = document.getElementById("cfgLeft");
const cfgCenter = document.getElementById("cfgCenter");
const cfgRight = document.getElementById("cfgRight");
const seatRowsEl = document.getElementById("seatRows");
const seatSummaryEl = document.getElementById("seatSummary");
const toastMsg = document.getElementById("toastMsg");
const syncStatus = document.getElementById("syncStatus");

// 1. Hàm gọi API tự động đính kèm CSRF Token của Spring Security
async function apiRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...(options.headers || {}) };

    if (["POST", "PUT", "DELETE"].includes(method)) {
        try {
            const csrfRes = await fetch(`${API}/auth/csrf`);
            if (csrfRes.ok) {
                const csrf = await csrfRes.json();
                if (csrf.headerName && csrf.token) {
                    headers[csrf.headerName] = csrf.token;
                }
            }
        } catch (_) {}
    }

    const res = await fetch(API + path, { ...options, headers });
    const text = await res.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        data = text;
    }

    if (!res.ok) {
        const msg = typeof data === "string" ? data : (data?.message || `Lỗi HTTP ${res.status}`);
        throw new Error(msg);
    }
    return data;
}

function showToast(msg, isError = false) {
    toastMsg.textContent = msg;
    toastMsg.className = "toast " + (isError ? "err" : "ok");
    syncStatus.textContent = isError ? "Lỗi đồng bộ" : "Đã lưu JSON";
    syncStatus.style.color = isError ? "var(--rd)" : "var(--gr)";
    clearTimeout(window._toastTimer);
    window._toastTimer = setTimeout(() => { toastMsg.textContent = ""; }, 3500);
}

// 2. Quản lý chia lối đi (Cột trái - Cột giữa - Cột phải) đồng bộ giữa Admin & Khách
function getAisleLayout(room) {
    const all = JSON.parse(localStorage.getItem(LAYOUT_STORAGE_KEY) || "{}");
    const saved = all[room.id];
    const total = room.seatsPerRow || 14;

    if (saved && (saved.left + saved.center + saved.right === total)) {
        return saved;
    }
    // Tự chia mặc định cân đối (VD: 14 cột -> 3 - 8 - 3)
    const left = total >= 10 ? 3 : (total >= 6 ? 2 : 0);
    const right = total >= 10 ? 3 : (total >= 6 ? 2 : 0);
    const center = Math.max(1, total - left - right);
    return { left, center, right };
}

function saveAisleLayout(roomId, left, center, right) {
    const all = JSON.parse(localStorage.getItem(LAYOUT_STORAGE_KEY) || "{}");
    all[roomId] = { left, center, right };
    localStorage.setItem(LAYOUT_STORAGE_KEY, JSON.stringify(all));
}

// 3. Nạp thông tin Admin ở cột trái
async function loadAdminProfile() {
    try {
        const me = await apiRequest("/auth/me");
        if (me && me.email) {
            document.getElementById("adminName").textContent = me.fullName || me.username || "Admin";
            document.getElementById("adminEmail").textContent = me.email;
            document.getElementById("adminAvatar").textContent = (me.email.slice(0, 2)).toUpperCase();
        }
    } catch (_) {}
}

// 4. Tải danh sách phòng từ Backend
async function loadRooms(preferredId = null) {
    try {
        rooms = await apiRequest("/rooms");
        document.getElementById("statRooms").textContent = rooms.length;
        document.getElementById("tileRoomCount").textContent = `${rooms.length} phòng`;

        roomSelect.innerHTML = "";
        if (rooms.length === 0) {
            roomSelect.innerHTML = `<option value="">Chưa có phòng</option>`;
            currentRoomId = null;
            seatRowsEl.innerHTML = `<p class="sub" style="text-align:center">Bấm "+ Thêm phòng" để tạo phòng chiếu đầu tiên.</p>`;
            return;
        }

        rooms.forEach(r => {
            const opt = document.createElement("option");
            opt.value = r.id;
            opt.textContent = `${r.name} (${r.id})`;
            roomSelect.appendChild(opt);
        });

        if (preferredId && rooms.some(r => r.id === preferredId)) {
            currentRoomId = preferredId;
        } else if (!currentRoomId || !rooms.some(r => r.id === currentRoomId)) {
            currentRoomId = rooms[0].id;
        }

        roomSelect.value = currentRoomId;
        await selectRoom(currentRoomId);
    } catch (err) {
        showToast(err.message, true);
    }
}

// 5. Chọn phòng & Tải sơ đồ ghế
async function selectRoom(roomId) {
    currentRoomId = roomId;
    const room = rooms.find(r => r.id === roomId);
    if (!room) return;

    const layout = getAisleLayout(room);
    cfgRows.value = room.rowCount;
    cfgLeft.value = layout.left;
    cfgCenter.value = layout.center;
    cfgRight.value = layout.right;

    try {
        let seats = await apiRequest(`/seats/room/${encodeURIComponent(roomId)}`);
        // Nếu phòng chưa sinh ma trận ghế thì tự động gọi API sinh ghế ngay
        if (!seats || seats.length === 0) {
            seats = await apiRequest(`/seats/room/${encodeURIComponent(roomId)}/generate`, { method: "POST" });
        }
        currentSeats = seats;
        renderSeatGrid(room, seats, layout);
    } catch (err) {
        showToast(err.message, true);
    }
}

// 6. Vẽ ma trận ghế 3 dãy + 2 lối đi
function renderSeatGrid(room, seats, layout) {
    seatRowsEl.innerHTML = "";
    const seatMap = new Map();
    let stdCount = 0, vipCount = 0, badCount = 0;

    seats.forEach(s => {
        seatMap.set(`${s.rowLabel}${s.seatNumber}`, s);
        if (s.status !== "ACTIVE") badCount++;
        else if (s.type === "VIP") vipCount++;
        else stdCount++;
    });

    const totalSeats = room.rowCount * room.seatsPerRow;
    seatSummaryEl.textContent = `Tổng ${totalSeats} ghế · Standard ${stdCount} · VIP ${vipCount} · Hỏng ${badCount}`;
    document.getElementById("statSeats").textContent = totalSeats;
    document.getElementById("statVip").textContent = vipCount;

    for (let rIdx = 0; rIdx < room.rowCount; rIdx++) {
        const rowLabel = String.fromCharCode(65 + rIdx);
        const rowDiv = document.createElement("div");
        rowDiv.className = "row";

        const leftLabel = document.createElement("span");
        leftLabel.className = "rl";
        leftLabel.textContent = rowLabel;
        leftLabel.title = `Bấm để đổi toàn bộ hàng ${rowLabel}`;
        leftLabel.onclick = () => paintEntireRow(rowLabel);
        rowDiv.appendChild(leftLabel);

        for (let col = 1; col <= room.seatsPerRow; col++) {
            // Chèn khoảng trống lối đi giữa Cột trái - Cột giữa - Cột phải
            if (layout.left > 0 && col === layout.left + 1) {
                const gap1 = document.createElement("span");
                gap1.className = "gap";
                rowDiv.appendChild(gap1);
            }
            if (layout.right > 0 && col === layout.left + layout.center + 1) {
                const gap2 = document.createElement("span");
                gap2.className = "gap";
                rowDiv.appendChild(gap2);
            }

            const key = `${rowLabel}${col}`;
            const seat = seatMap.get(key);
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "seat";
            btn.title = seat ? `${seat.id} (${seat.type} - ${seat.status})` : key;

            if (seat) {
                if (seat.status !== "ACTIVE") btn.classList.add("x");
                else if (seat.type === "VIP") btn.classList.add("v");

                btn.onclick = () => paintSingleSeat(seat);
            } else {
                btn.classList.add("x");
                btn.disabled = true;
            }
            rowDiv.appendChild(btn);
        }

        const rightLabel = document.createElement("span");
        rightLabel.className = "rl";
        rightLabel.textContent = rowLabel;
        rightLabel.title = `Bấm để đổi toàn bộ hàng ${rowLabel}`;
        rightLabel.onclick = () => paintEntireRow(rowLabel);
        rowDiv.appendChild(rightLabel);

        seatRowsEl.appendChild(rowDiv);
    }
}

// 7. Chuyển đổi công cụ cọ vẽ sang dữ liệu Backend
function getToolPayload() {
    if (activeTool === "VIP") return { type: "VIP", status: "ACTIVE" };
    if (activeTool === "MAINTENANCE") return { type: "NORMAL", status: "MAINTENANCE" };
    return { type: "NORMAL", status: "ACTIVE" };
}

// Bấm 1 ghế để đổi loại/trạng thái và lưu xuống Backend
async function paintSingleSeat(seat) {
    const payload = getToolPayload();
    try {
        await apiRequest(`/seats/${encodeURIComponent(seat.id)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        await selectRoom(currentRoomId);
        showToast(`Đã cập nhật ghế ${seat.id}`);
    } catch (err) {
        showToast(err.message, true);
    }
}

// Bấm chữ cái tên hàng (A, B, I, J...) để đổi toàn bộ hàng ghế
async function paintEntireRow(rowLabel) {
    const rowSeats = currentSeats.filter(s => s.rowLabel === rowLabel);
    if (rowSeats.length === 0) return;

    const payload = getToolPayload();
    syncStatus.textContent = `Đang lưu hàng ${rowLabel}...`;

    try {
        await Promise.all(
            rowSeats.map(s =>
                apiRequest(`/seats/${encodeURIComponent(s.id)}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                })
            )
        );
        await selectRoom(currentRoomId);
        showToast(`Đã đổi toàn bộ hàng ${rowLabel}`);
    } catch (err) {
        showToast(err.message, true);
    }
}

// 8. Các sự kiện giao diện
roomSelect.addEventListener("change", () => selectRoom(roomSelect.value));

document.querySelectorAll(".tool").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".tool").forEach(b => b.classList.remove("on"));
        btn.classList.add("on");
        activeTool = btn.dataset.tool;
    });
});

// Cập nhật lưới hàng/cột của phòng hiện tại
document.getElementById("applyGridBtn").addEventListener("click", async () => {
    const room = rooms.find(r => r.id === currentRoomId);
    if (!room) return;

    const newRows = Number(cfgRows.value);
    const left = Number(cfgLeft.value);
    const center = Number(cfgCenter.value);
    const right = Number(cfgRight.value);
    const newSeatsPerRow = left + center + right;

    if (newSeatsPerRow < 1) {
        showToast("Tổng số cột phải lớn hơn 0!", true);
        return;
    }

    saveAisleLayout(room.id, left, center, right);

    try {
        await apiRequest(`/rooms/${encodeURIComponent(room.id)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                id: room.id,
                name: room.name,
                rowCount: newRows,
                seatsPerRow: newSeatsPerRow
            })
        });
        await loadRooms(room.id);
        showToast("Đã cập nhật kích thước phòng & lối đi!");
    } catch (err) {
        showToast(err.message, true);
    }
});

// Modal Thêm phòng mới
const addRoomModal = document.getElementById("addRoomModal");
document.getElementById("openAddModalBtn").onclick = () => {
    document.getElementById("newRoomId").value = `R00${rooms.length + 1}`;
    document.getElementById("newRoomName").value = `Hall ${rooms.length + 1}`;
    addRoomModal.classList.add("on");
};
document.getElementById("closeModalBtn").onclick = () => addRoomModal.classList.remove("on");

document.getElementById("addRoomForm").addEventListener("submit", async e => {
    e.preventDefault();
    const id = document.getElementById("newRoomId").value.trim();
    const name = document.getElementById("newRoomName").value.trim();
    const rowCount = Number(document.getElementById("newRows").value);
    const left = Number(document.getElementById("newLeft").value);
    const center = Number(document.getElementById("newCenter").value);
    const right = Number(document.getElementById("newRight").value);
    const seatsPerRow = left + center + right;

    try {
        const created = await apiRequest("/rooms", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id, name, rowCount, seatsPerRow })
        });
        saveAisleLayout(created.id, left, center, right);
        await apiRequest(`/seats/room/${encodeURIComponent(created.id)}/generate`, { method: "POST" });

        addRoomModal.classList.remove("on");
        await loadRooms(created.id);
        showToast(`Đã tạo ${created.name} (${rowCount * seatsPerRow} ghế)!`);
    } catch (err) {
        showToast(err.message, true);
    }
});

// Xóa phòng hiện tại
document.getElementById("deleteRoomBtn").addEventListener("click", async () => {
    if (!currentRoomId) return;
    if (!confirm(`Xác nhận xóa phòng ${currentRoomId}?`)) return;

    try {
        await apiRequest(`/rooms/${encodeURIComponent(currentRoomId)}`, { method: "DELETE" });
        await loadRooms();
        showToast("Đã xóa phòng chiếu!");
    } catch (err) {
        showToast(err.message, true);
    }
});

// Khởi chạy
loadAdminProfile();
loadRooms();