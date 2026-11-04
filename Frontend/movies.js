const escapeMarkup = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const API_URL = '/api/movies';

// Khi trang web tải xong -> Load danh sách phim
document.addEventListener('DOMContentLoaded', fetchMovies);

//  Hàm gọi GET API lấy danh sách phim
function fetchMovies() {
    fetch(API_URL)
        .then(response => response.json())
        .then(data => {
            const tbody = document.getElementById('movieTableBody');
            tbody.innerHTML = ''; // Xóa bảng cũ

            data.forEach(movie => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><b>${escapeMarkup(movie.id)}</b></td>
                    <td>${escapeMarkup(movie.title)}</td>
                    <td>${escapeMarkup(movie.duration)} phút</td>
                    <td>${escapeMarkup(movie.genre)}</td>
                    <td><span style="color: ${movie.status === 'NOW_SHOWING' ? 'green' : 'orange'}">${escapeMarkup(movie.status)}</span></td>
                    <td>
                        <button class="btn-delete" data-delete-id="${escapeMarkup(movie.id)}">Xóa</button>
                    </td>
                `;
                tr.querySelector('[data-delete-id]').addEventListener('click', () => deleteMovie(movie.id));
                tbody.appendChild(tr);
            });
        })
        .catch(error => alert('Không tải được danh sách phim: ' + error.message));
}

//  Xử lý khi Submit Form (Gửi POST API thêm phim)
document.getElementById('movieForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const newMovie = {
        id: document.getElementById('movieId').value,
        title: document.getElementById('title').value,
        duration: parseInt(document.getElementById('duration').value),
        genre: document.getElementById('genre').value,
        status: document.getElementById('status').value
    };

    fetch(API_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(newMovie)
    })
        .then(response => {
            if (response.ok) {
                alert('Thêm phim thành công!');
                document.getElementById('movieForm').reset(); // Xóa trắng form
                fetchMovies(); // Reload lại bảng
            } else {
                alert('Thêm phim thất bại! Kiểm tra lại ID.');
            }
        })
        .catch(error => console.error('Lỗi khi thêm phim:', error));
});

//  Hàm xóa phim
function deleteMovie(id) {
    if (confirm(`Bạn có chắc muốn xóa phim ${id}?`)) {
        fetch(`${API_URL}/${encodeURIComponent(id)}`, {
            method: 'DELETE'
        })
            .then(response => {
                if (response.ok) {
                    alert('Xóa phim thành công!');
                    fetchMovies(); // Reload lại bảng
                } else {
                    alert('Xóa thất bại!');
                }
            })
            .catch(error => console.error('Lỗi khi xóa phim:', error));
    }
}