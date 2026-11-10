'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    try {
        const user = await Cinema.requireAdmin(); if (!user) return;
        const nav = document.createElement('nav'); nav.className = 'admin-nav'; nav.setAttribute('aria-label', 'Khu vực quản trị');
        nav.style.cssText = 'background:#262c25;color:white;padding:12px 20px;display:flex;flex-wrap:wrap;gap:20px;font:13px sans-serif;margin-bottom:20px;border-radius:6px';
        [['admin.html','← Quản trị'],['movies.html','Phim'],['showtimes.html','Lịch chiếu'],['rooms.html','Phòng & ghế'],['customers.html','Khách hàng'],['welcome.html','Trang khách']].forEach(([href,text]) => { const link=document.createElement('a'); link.href=href; link.textContent=text; link.style.color='inherit'; nav.append(link); });
        document.body.prepend(nav);
    } catch (error) { alert(error.message); }
});
