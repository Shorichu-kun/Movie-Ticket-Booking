const API = "/api";

const roomForm = document.getElementById("roomForm");
const roomList = document.getElementById("roomList");
const seatMatrix = document.getElementById("seatMatrix");
const selectedRoomText = document.getElementById("selectedRoom");
const generateBtn = document.getElementById("generateSeatsBtn");
const message = document.getElementById("message");
const saveRoomBtn = document.getElementById("saveRoomBtn");
const cancelEditBtn = document.getElementById("cancelEditBtn");

let rooms = [];
let selectedRoomId = null;
let editingRoomId = null;

// Goi API va kiem tra loi
async function request(path, options = {}) {
    const response = await fetch(API + path, options);
    const text = await response.text();

    let data;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        data = text;
    }

    if (!response.ok) {
        const errorMessage = typeof data === "string"
            ? data
            : data?.message || `HTTP ${response.status}`;

        throw new Error(errorMessage);
    }

    return data;
}

function showMessage(text, type = "success") {
    message.textContent = text;
    message.className = type;
}

// Tao nut thao tac
function createButton(label, className, action) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.className = className;
    button.addEventListener("click", action);
    return button;
}

// Tai danh sach phong
async function loadRooms() {
    rooms = await request("/rooms");
    renderRooms();

    if (selectedRoomId) {
        const exists = rooms.some(r => r.id === selectedRoomId);

        if (exists) {
            renderSelectedRoom();
            await loadSeats();
        } else {
            selectedRoomId = null;
            renderSelectedRoom();
        }
    }
}

// Hien thi danh sach phong
function renderRooms() {
    roomList.replaceChildren();

    if (rooms.length === 0) {
        roomList.textContent = "Chua co phong chieu.";
        return;
    }

    rooms.forEach(room => {
        const item = document.createElement("div");
        item.className = "room-item";

        if (room.id === selectedRoomId) {
            item.classList.add("selected");
        }

        const info = document.createElement("div");
        info.className = "room-info";

        const title = document.createElement("h3");
        title.textContent = `${room.name} (${room.id})`;

        const detail = document.createElement("p");
        detail.textContent =
            `${room.rowCount} hang x ${room.seatsPerRow} ghe`;

        info.append(title, detail);

        const actions = document.createElement("div");
        actions.className = "room-actions";

        actions.append(
            createButton("Xem ghe", "", () => selectRoom(room.id)),
            createButton("Sua", "btn-edit", () => editRoom(room)),
            createButton("Xoa", "btn-delete", () => deleteRoom(room.id))
        );

        item.append(info, actions);
        roomList.appendChild(item);
    });
}

// Tao hoac sua phong
roomForm.addEventListener("submit", async event => {
    event.preventDefault();

    const room = {
        id: document.getElementById("roomId").value.trim(),
        name: document.getElementById("roomName").value.trim(),
        rowCount: Number(document.getElementById("rowCount").value),
        seatsPerRow: Number(
            document.getElementById("seatsPerRow").value
        )
    };

    const isEditing = editingRoomId !== null;

    try {
        const url = isEditing
            ? `/rooms/${encodeURIComponent(editingRoomId)}`
            : "/rooms";

        const result = await request(url, {
            method: isEditing ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(room)
        });

        resetRoomForm();
        await loadRooms();
        await selectRoom(result.id);

        showMessage(
            isEditing
                ? "Cap nhat phong thanh cong!"
                : "Them phong thanh cong!"
        );

    } catch (error) {
        showMessage(error.message, "error");
    }
});

// Chuyen form sang che do sua
function editRoom(room) {
    editingRoomId = room.id;

    document.getElementById("roomId").value = room.id;
    document.getElementById("roomId").readOnly = true;
    document.getElementById("roomName").value = room.name;
    document.getElementById("rowCount").value = room.rowCount;
    document.getElementById("seatsPerRow").value = room.seatsPerRow;

    saveRoomBtn.textContent = "Cap nhat phong";
    cancelEditBtn.hidden = false;
    roomForm.scrollIntoView({ behavior: "smooth" });
}

function resetRoomForm() {
    editingRoomId = null;
    roomForm.reset();

    document.getElementById("roomId").readOnly = false;
    saveRoomBtn.textContent = "Luu phong chieu";
    cancelEditBtn.hidden = true;
}

cancelEditBtn.addEventListener("click", resetRoomForm);

// Xoa phong
async function deleteRoom(id) {
    if (!confirm(`Ban co chac muon xoa phong ${id}?`)) {
        return;
    }

    try {
        await request(`/rooms/${encodeURIComponent(id)}`, {
            method: "DELETE"
        });

        if (selectedRoomId === id) {
            selectedRoomId = null;
            seatMatrix.replaceChildren();
        }

        await loadRooms();
        showMessage("Xoa phong thanh cong!");

    } catch (error) {
        showMessage(error.message, "error");
    }
}

// Chon phong
async function selectRoom(id) {
    selectedRoomId = id;
    renderRooms();
    renderSelectedRoom();
    await loadSeats();
}

function renderSelectedRoom() {
    const room = rooms.find(r => r.id === selectedRoomId);

    if (!room) {
        selectedRoomText.textContent = "Chua chon phong chieu.";
        generateBtn.disabled = true;
        seatMatrix.replaceChildren();
        return;
    }

    selectedRoomText.textContent =
        `${room.name} - ${room.rowCount} hang x ${room.seatsPerRow} ghe`;

    generateBtn.disabled = false;
}

// Tai danh sach ghe theo phong
async function loadSeats() {
    if (!selectedRoomId) return;

    const roomId = selectedRoomId;

    try {
        const seats = await request(
            `/seats/room/${encodeURIComponent(roomId)}`
        );

        if (selectedRoomId !== roomId) return;

        const room = rooms.find(r => r.id === roomId);
        if (!room) return;

        generateBtn.disabled = seats.length > 0;
        renderSeatMatrix(room, seats);

    } catch (error) {
        showMessage(error.message, "error");
    }
}

// Tao ma tran ghe
generateBtn.addEventListener("click", async () => {
    if (!selectedRoomId) return;

    try {
        await request(
            `/seats/room/${encodeURIComponent(selectedRoomId)}/generate`,
            { method: "POST" }
        );

        await loadSeats();
        showMessage("Tao ma tran ghe thanh cong!");

    } catch (error) {
        showMessage(error.message, "error");
    }
});

// Hien thi ma tran ghe
function renderSeatMatrix(room, seats) {
    seatMatrix.replaceChildren();

    if (seats.length === 0) {
        seatMatrix.textContent =
            "Phong chua co ghe. Bam Tao ma tran ghe.";
        return;
    }

    const seatMap = new Map();

    seats.forEach(seat => {
        seatMap.set(
            `${seat.rowLabel}${seat.seatNumber}`,
            seat
        );
    });

    for (let i = 0; i < room.rowCount; i++) {
        const rowLabel = String.fromCharCode(65 + i);

        const row = document.createElement("div");
        row.className = "seat-row";

        const label = document.createElement("span");
        label.className = "seat-row-label";
        label.textContent = rowLabel;

        const cells = document.createElement("div");
        cells.className = "seat-cells";
        cells.style.gridTemplateColumns =
            `repeat(${room.seatsPerRow}, 56px)`;

        for (let number = 1; number <= room.seatsPerRow; number++) {
            const key = `${rowLabel}${number}`;
            const seat = seatMap.get(key);

            const button = document.createElement("button");
            button.type = "button";
            button.textContent = key;

            if (!seat) {
                button.className = "seat empty";
                button.disabled = true;
            } else {
                const cssStatus = seat.status === "ACTIVE"
                    ? seat.type.toLowerCase()
                    : seat.status.toLowerCase();

                button.className = `seat ${cssStatus}`;
                button.title =
                    `${seat.id} | ${seat.type} | ${seat.status}`;

                button.addEventListener("click", () => {
                    changeSeat(seat);
                });
            }

            cells.appendChild(button);
        }

        row.append(label, cells);
        seatMatrix.appendChild(row);
    }
}

// Doi loai ghe va trang thai
async function changeSeat(seat) {
    let type = prompt(
        "Loai ghe: NORMAL, VIP, COUPLE",
        seat.type
    );

    if (type === null) return;
    type = type.trim().toUpperCase();

    if (!["NORMAL", "VIP", "COUPLE"].includes(type)) {
        showMessage("Loai ghe khong hop le!", "error");
        return;
    }

    let status = prompt(
        "Trang thai: ACTIVE, MAINTENANCE, DISABLED",
        seat.status
    );

    if (status === null) return;
    status = status.trim().toUpperCase();

    if (!["ACTIVE", "MAINTENANCE", "DISABLED"].includes(status)) {
        showMessage("Trang thai ghe khong hop le!", "error");
        return;
    }

    try {
        await request(
            `/seats/${encodeURIComponent(seat.id)}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ type, status })
            }
        );

        await loadSeats();
        showMessage(`Da cap nhat ghe ${seat.id}!`);

    } catch (error) {
        showMessage(error.message, "error");
    }
}

// Khoi dong trang
loadRooms().catch(error => {
    showMessage(error.message, "error");
});
