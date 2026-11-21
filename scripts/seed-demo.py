#!/usr/bin/env python3
import argparse
import json
from datetime import datetime, timedelta
from itertools import product
from pathlib import Path
from zoneinfo import ZoneInfo

parser = argparse.ArgumentParser(description='Create cinema demo data in an empty directory')
parser.add_argument('--directory', required=True, help='Empty destination directory for demo data')
args = parser.parse_args()
root = Path(args.directory).resolve()
root.mkdir(parents=True, exist_ok=True)
if any(root.iterdir()):
    raise SystemExit('Directory must be empty. Existing data was not changed.')
movie_groups = {
    'NOW_SHOWING': [
        ('SEED-M001', 'Chuyến Tàu Qua Mùa Hạ', 95, 'Phiêu lưu'),
        ('SEED-M002', 'Thành Phố Trên Mây', 100, 'Khoa học viễn tưởng'),
        ('SEED-M003', 'Nhà Có Năm Người', 105, 'Gia đình'),
        ('SEED-M004', 'Quán Cà Phê Lúc Bình Minh', 110, 'Tình cảm'),
        ('SEED-M005', 'Mật Mã Sông Hồng', 115, 'Hành động'),
        ('SEED-M006', 'Bức Thư Không Người Nhận', 120, 'Tâm lý'),
        ('SEED-M007', 'Biệt Đội Mèo Mun', 125, 'Hoạt hình'),
        ('SEED-M008', 'Đêm Cuối Ở Ga Cũ', 130, 'Kinh dị'),
        ('SEED-M009', 'Bản Nhạc Của Gió', 135, 'Âm nhạc'),
        ('SEED-M010', 'Cuộc Hẹn Ngày Mưa', 140, 'Tình cảm'),
        ('SEED-M011', 'Ngôi Làng Trong Sương', 145, 'Bí ẩn'),
        ('SEED-M012', 'Đường Đua Tuổi Trẻ', 90, 'Thể thao'),
        ('SEED-M013', 'Hành Tinh Màu Ngọc', 95, 'Khoa học viễn tưởng'),
        ('SEED-M014', 'Kỳ Nghỉ Của Bố', 100, 'Hài'),
        ('SEED-M015', 'Bếp Nhỏ Cuối Phố', 105, 'Gia đình'),
        ('SEED-M016', 'Những Người Gác Hải Đăng', 110, 'Phiêu lưu'),
        ('SEED-M017', 'Điệp Vụ Hoa Sen', 115, 'Hành động'),
        ('SEED-M018', 'Chú Rồng Sợ Độ Cao', 120, 'Hoạt hình'),
        ('SEED-M019', 'Một Ngày Làm Siêu Sao', 125, 'Hài'),
        ('SEED-M020', 'Tiếng Gõ Cửa Tầng Mười', 130, 'Kinh dị'),
        ('SEED-M021', 'Mùa Hoa Trên Cao Nguyên', 135, 'Tình cảm'),
        ('SEED-M022', 'Dấu Chân Trên Sao Hỏa', 140, 'Khoa học viễn tưởng'),
        ('SEED-M023', 'Bóng Rổ Sân Trường', 145, 'Thể thao'),
        ('SEED-M024', 'Cánh Diều Của Ngoại', 90, 'Gia đình'),
    ],
    'COMING_SOON': [
        ('SEED-M033', 'Cánh Cổng Đại Dương', 135, 'Phiêu lưu'),
        ('SEED-M034', 'Robot Và Bông Hoa', 140, 'Hoạt hình'),
        ('SEED-M035', 'Bầu Trời Thứ Hai', 145, 'Khoa học viễn tưởng'),
        ('SEED-M036', 'Người Hùng Xóm Nhỏ', 90, 'Hài'),
        ('SEED-M037', 'Lối Về Miền Ký Ức', 95, 'Tâm lý'),
        ('SEED-M038', 'Cuộc Giải Cứu Mặt Trăng', 100, 'Hành động'),
    ],
}
movies = [
    {'id': movie_id, 'title': title, 'duration': duration, 'genre': genre, 'status': status}
    for status, entries in movie_groups.items()
    for movie_id, title, duration, genre in entries
]
rooms = [{'id': f'ROOM{i}', 'name': f'Phòng {i:02}', 'rowCount': 5, 'seatsPerRow': 8} for i in (1, 2)]
seats = [{'id': f'{r["id"]}-{row}{n}', 'roomId': r['id'], 'rowLabel': row, 'seatNumber': n, 'type': 'VIP' if row in 'CD' else 'COUPLE' if row == 'E' else 'NORMAL', 'status': 'ACTIVE'} for r in rooms for row in 'ABCDE' for n in range(1, 9)]
now_showing = [movie for movie in movies if movie['status'] == 'NOW_SHOWING']
first_day = datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).replace(
    hour=0, minute=0, second=0, microsecond=0
) + timedelta(days=1)
slots = product(range(7), (10, 13, 16, 19), rooms)
shows = []
for index, (day, hour, room) in enumerate(slots):
    movie = now_showing[index % len(now_showing)]
    start = first_day + timedelta(days=day, hours=hour)
    end = start + timedelta(minutes=movie['duration'])
    shows.append({
        'id': f'SHOW{index + 1:03}',
        'movieId': movie['id'],
        'roomId': room['id'],
        'startTime': start.strftime('%Y-%m-%dT%H:%M:%S'),
        'endTime': end.strftime('%Y-%m-%dT%H:%M:%S'),
        'price': 80000 if hour < 16 else 90000,
    })
for name, data in [('movies.json', movies), ('cinemaRooms.json', rooms), ('seats.json', seats), ('showtimes.json', shows)]:
    (root / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Demo data created in {root}. Movie titles are fictional; schedules start tomorrow.')
