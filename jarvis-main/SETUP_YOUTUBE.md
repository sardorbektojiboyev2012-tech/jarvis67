# YouTube ulash (Google Cloud)
1. console.cloud.google.com: yangi loyiha oching.
2. "APIs & Services > Library": "YouTube Data API v3" ni yoqing.
3. "OAuth consent screen": External; o'zingizning Gmail'ingizni Test user qilib qo'shing.
   Scope'lar: youtube.upload, youtube.readonly.
   Eslatma: "Testing" holatida refresh token odatda 7 kunda tugaydi. Shaxsiy foydalanish uchun
   "Publish app" (In production) qiling; tasdiqlanmagan ogohlantirish chiqadi, o'zingiz uchun bu normal.
4. "Credentials > Create OAuth client ID > Android":
   Package name: com.jarvis.agent
   SHA-1: imzolovchi keystore'ning SHA-1 (6-qismda qat'iy keystore beraman; debug keystore har yig'ishda o'zgaradi).
5. Olingan Client ID'ni GitHub Secrets'ga GOOGLE_CLIENT_ID nomi bilan qo'shing (yoki mahalliy yig'ishda env o'zgaruvchi).
6. Kvota: standart 10 000 birlik/kun; bitta yuklash ~1600 birlik.
   Audit o'tmagan API loyihasidan yuklangan videolar private bo'lib qolishi mumkin (ilova buni bildirishnomada ko'rsatadi).
