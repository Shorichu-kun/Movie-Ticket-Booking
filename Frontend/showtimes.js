const API_URL = 'http://localhost:8080/api/showtimes';
const MOVIE_API_URL = 'http://localhost:8080/api/movies';


let movies = [];


document.addEventListener('DOMContentLoaded', () => {
    fetch(MOVIE_API_URL)
        .then(response => response.json())
        .then(data => {
            movies = data;
            fillMovieDropdowns();
            fetchShowtimes();
        })
        .catch(error => console.error('Lỗi khi tải danh sách phim:', error));
});

function fillMovieDropdowns() {
    const movieSelect = document.getElementById('movieId');
    const filterSelect = document.getElementById('filterMovie');

    movieSelect.innerHTML = '';
    filterSelect.innerHTML = '<option value="">Tất cả phim</option>';

    movies.forEach(movie => {
        movieSelect.innerHTML += `<option value="${movie.id}">${movie.title}</option>`;
        filterSelect.innerHTML += `<option value="${movie.id}">${movie.title}</option>`;
    });
}

function getMovieTitle(movieId) {
    const movie = movies.find(m => m.id === movieId);
    return movie ? movie.title : movieId;
}


function formatTime(isoString) {
    const [date, time] = isoString.split('T');
    const [year, month, day] = date.split('-');
    return `${day}/${month}/${year} ${time}`;
}


function fetchShowtimes() {
    const movieId = document.getElementById('filterMovie').value;
    const date = document.getElementById('filterDate').value;

    const params = new URLSearchParams();
    if (movieId) params.append('movieId', movieId);
    if (date) params.append('date', date);

    fetch(`${API_URL}?${params.toString()}`)
        .then(response => response.json())
        .then(data => {
            const tbody = document.getElementById('showtimeTableBody');
            tbody.innerHTML = '';

            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="empty-row">Chưa có suất chiếu nào</td></tr>';
                return;
            }

            data.forEach(s => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><b>${s.id}</b></td>
                    <td>${getMovieTitle(s.movieId)}</td>
                    <td>${s.roomId}</td>
                    <td>${formatTime(s.startTime)}</td>
                    <td>${formatTime(s.endTime)}</td>
                    <td>${s.price.toLocaleString('vi-VN')} đ</td>
                    <td>
                        <button class="btn-delete" onclick="deleteShowtime('${s.id}')">Xóa</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(error => console.error('Lỗi khi tải suất chiếu:', error));
}


document.getElementById('filterMovie').addEventListener('change', fetchShowtimes);
document.getElementById('filterDate').addEventListener('change', fetchShowtimes);
document.getElementById('btnReset').addEventListener('click', () => {
    document.getElementById('filterMovie').value = '';
    document.getElementById('filterDate').value = '';
    fetchShowtimes();
});


document.getElementById('showtimeForm').addEventListener('submit', function (e) {
    e.preventDefault();

    const newShowtime = {
        id: document.getElementById('showtimeId').value,
        movieId: document.getElementById('movieId').value,
        roomId: document.getElementById('roomId').value,
        startTime: document.getElementById('startTime').value,
        price: parseFloat(document.getElementById('price').value)
    };

    fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newShowtime)
    })
        .then(response => response.text().then(message => ({ ok: response.ok, message })))
        .then(result => {
            // Backend trả về thông báo rõ nguyên nhân khi lỗi (trùng lịch, phim ngừng chiếu...)
            alert(result.message);
            if (result.ok) {
                document.getElementById('showtimeForm').reset();
                fetchShowtimes();
            }
        })
        .catch(error => console.error('Lỗi khi thêm suất chiếu:', error));
});


function deleteShowtime(id) {
    if (confirm(`Bạn có chắc muốn xóa suất chiếu ${id}?`)) {
        fetch(`${API_URL}/${id}`, { method: 'DELETE' })
            .then(response => {
                if (response.ok) {
                    alert('Xóa suất chiếu thành công!');
                    fetchShowtimes();
                } else {
                    alert('Xóa thất bại!');
                }
            })
            .catch(error => console.error('Lỗi khi xóa suất chiếu:', error));
    }
}
