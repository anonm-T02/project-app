# project-app — Oilaviy Himoya (ishchi nom)

Android uchun ota-ona nazorati ilovasi. Yosh foydalanuvchini kattalar kontenti,
qimor, fishing va zararli saytlardan himoya qilishga yordam beradi.

> Bu ilova bolalarni zararli kontentdan himoya qilishga yordam beradi, lekin barcha
> kontentni kafolatli bloklay olmaydi. Ota-ona nazorati va oilaviy qoidalar bilan
> birga ishlating.

## Qanday ishlaydi

- Telefonda **lokal, faqat DNS uchun VPN** yoqiladi. Root kerak emas.
- DNS so'rovlari oilaviy DNS serveriga (Cloudflare for Families, `1.1.1.3`) yuboriladi,
  u kattalar kontenti va zararli saytlarni bloklaydi.
- HTTPS ochilmaydi (MITM yo'q). Sahifalar mazmuni, parollar va xabarlar ilovaga ko'rinmaydi.

## Nimani qila olmaydi

- Telegram, Instagram, TikTok, YouTube **ichidagi** kontentni filtrlamaydi.
  Buning yechimi keyingi bosqichda: ilovani butunlay yoki vaqt bo'yicha yopish.
- Boshqa VPN yoqilsa yoki brauzerda maxsus DNS sozlansa, filtr aylanib o'tiladi.
  Bunga qarshi choralar Phase 1 da.

## Holat

| Bosqich | Mazmuni | Holat |
|---|---|---|
| Phase 0 | Skelet, VPN yoqish/o'chirish, oilaviy DNS orqali filtr | tayyor |
| Phase 1.1 | Ota-ona PIN kodi, "Doim yoqiq VPN" ko'rsatmasi | tayyor |
| Phase 1.2 | DoH bloklash, ochiq DNS manzillari, Private DNS tekshiruvi, xavfsiz qidiruv | tayyor |
| Phase 1.3 | Blok bildirishnomasi va blok ekrani | sinovda |
| Phase 1.4 | Oila ro'yxatlari, qimor ro'yxati, uyqu vaqti, o'chirishdan himoya | navbatda |
| Phase 2 | Oila sinxronizatsiyasi (ota-ona telefoni, so'rovlar) | keyin |
| Phase 3 | Ilovalarni bloklash, kunlik limit | keyin |
| Phase 4 | Play'ga chiqarish | keyin |

Batafsil: [docs/architecture.md](docs/architecture.md)

## Ishga tushirish

1. Android Studio'da `File > Open` orqali shu papkani oching.
2. Gradle sync tugashini kuting (birinchi marta bir necha daqiqa).
3. Telefonni USB orqali ulang yoki emulyator tanlang va `Run` tugmasini bosing.

Talablar: Android 8.0+ (minSdk 26), JDK 17+ (Android Studio ichidagi yetadi).

## Testlar

```
./gradlew testDebugUnitTest
```
