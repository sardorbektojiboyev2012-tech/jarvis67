# JARVIS backend (media host)
Instagram media'ni ochiq HTTPS URL'dan oladi. Bu server videoni vaqtincha (3 kun) saqlab, URL beradi.
1. Server: har qanday VPS (masalan, Oracle Cloud Always Free) yoki Docker bor kompyuter.
2. `docker build -t jarvis-backend . && docker run -d -p 8080:8080 -e API_KEY=UZUN_MAXFIY_KALIT -e BASE_URL=https://domen.uz -v jarvis-files:/app/files jarvis-backend`
3. HTTPS shart: Caddy (`domen.uz { reverse_proxy localhost:8080 }`) yoki Cloudflare Tunnel.
4. Ilovada: Telegram/backend bo'limiga URL va API_KEY ni kiriting.
Hozircha faqat media hosting. Telefon o'chiq paytda nashr qiluvchi server-tomon rejalashtirgich keyingi bosqichda.
