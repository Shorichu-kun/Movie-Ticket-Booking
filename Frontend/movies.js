const API_URL = 'http://localhost:8080/api/movies';

// Khi trang web tải xong -> Load danh sách phim
document.addEventListener('DOMContentLoaded', fetchMovies);

// 1. Hàm gọi GET API lấy danh sách phim
function fetchMovies() {
    fetch(API_URL)
        .then(response => response.json())
        .then(data => {
            const tbody = document.getElementById('movieTableBody');
            tbody.innerHTML = ''; // Xóa bảng cũ

            data.forEach(movie => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><b>${movie.id}</b></td>
                    <td>${movie.title}</td>
                    <td>${movie.duration} phút</td>
                    <td>${movie.genre}</td>
                    <td><span style="color: ${movie.status === 'NOW_SHOWING' ? 'green' : 'orange'}">${movie.status}</span></td>
                    <td>
                        <button class="btn-delete" onclick="deleteMovie('${movie.id}')">Xóa</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(error => console.error('Lỗi khi tải danh sách phim:', error));
}

// 2. Xử lý khi Submit Form (Gửi POST API thêm phim)
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

// 3. Hàm xóa phim (Gửi DELETE API)
function deleteMovie(id) {
    if (confirm(`Bạn có chắc muốn xóa phim ${id}?`)) {
        fetch(`${API_URL}/${id}`, {
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