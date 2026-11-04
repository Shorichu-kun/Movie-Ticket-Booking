#!/usr/bin/env python3
"""Create an isolated cinema demo dataset; never replace existing files."""
import argparse
import json
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--directory', required=True, help='Empty destination directory for demo data')
args = parser.parse_args()
root = Path(args.directory).resolve()
root.mkdir(parents=True, exist_ok=True)
if any(root.iterdir()):
    raise SystemExit('Directory must be empty. Existing data was not changed.')
movies = [
    {'id': 'DEMO01', 'title': 'Chuyến Đi Mùa Hạ', 'duration': 110, 'genre': 'Phiêu lưu · Gia đình', 'status': 'NOW_SHOWING'},
    {'id': 'DEMO02', 'title': 'Giữa Những Vì Sao', 'duration': 125, 'genre': 'Khoa học viễn tưởng', 'status': 'NOW_SHOWING'},
    {'id': 'DEMO03', 'title': 'Một Ngày Rất Khác', 'duration': 100, 'genre': 'Hài · Tình cảm', 'status': 'NOW_SHOWING'},
    {'id': 'DEMO04', 'title': 'Thanh Âm Thành Phố', 'duration': 115, 'genre': 'Âm nhạc · Tâm lý', 'status': 'NOW_SHOWING'},
    {'id': 'DEMO05', 'title': 'Bên Kia Chân Trời', 'duration': 120, 'genre': 'Phiêu lưu', 'status': 'COMING_SOON'},
]
rooms = [{'id': f'ROOM{i}', 'name': f'Phòng {i:02}', 'rowCount': 5, 'seatsPerRow': 8} for i in (1, 2)]
seats = [{'id': f'{r["id"]}-{row}{n}', 'roomId': r['id'], 'rowLabel': row, 'seatNumber': n, 'type': 'VIP' if row in 'CD' else 'COUPLE' if row == 'E' else 'NORMAL', 'status': 'ACTIVE'} for r in rooms for row in 'ABCDE' for n in range(1, 9)]
shows = []
for day in range(1, 8):
    for i, movie in enumerate(movies[:4]):
        start = (datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')) + timedelta(days=day)).replace(hour=14 + i * 2, minute=0, second=0, microsecond=0)
        shows.append({'id': f'SHOW{day}{i}', 'movieId': movie['id'], 'roomId': f'ROOM{i % 2 + 1}', 'startTime': start.strftime('%Y-%m-%dT%H:%M:%S'), 'endTime': (start + timedelta(minutes=movie['duration'])).strftime('%Y-%m-%dT%H:%M:%S'), 'price': 80000 + i * 10000})
for name, data in [('movies.json', movies), ('cinemaRooms.json', rooms), ('seats.json', seats), ('showtimes.json', shows)]:
    (root / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Demo data created in {root}. Movie titles are fictional; schedules start tomorrow.')
