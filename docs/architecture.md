# Arxitektura

## Qat'iy qoidalar

1. **Faqat DNS-only VpnService.** To'liq trafikni yoki SNI'ni tekshirish yo'q.
2. **HTTPS ochilmaydi.** MITM, sertifikat o'rnatish yo'q.
3. **Minimal ma'lumot.** Eng ko'pi: domen, kategoriya, vaqt. Phase 0 da hech narsa saqlanmaydi.
4. **Yashirin ishlamaydi.** Himoya yoqiq paytda doimiy bildirishnoma turadi.
5. **Root talab qilinmaydi.**
6. **Play'da ota-onalar uchun ilova** (Parenting), bolalar ilovasi emas.
7. **100% himoya va'da qilinmaydi**, bu ilova ichida ham yoziladi.

## Himoya qatlamlari

| Qatlam | Nima qiladi | Chegarasi | Bosqich |
|---|---|---|---|
| A. Oilaviy DNS | Millionlab kattalar va zararli domenlarni bloklaydi | Ilova ichidagi kontentni ko'rmaydi | Phase 0 |
| B. Lokal DNS qoidalar | Qimor ro'yxati, oila ro'yxatlari, Safe Search, DoH domenlari | DNS darajasida | Phase 1 |
| C. Chetlab o'tishdan himoya | PIN, Private DNS tekshiruvi, ogohlantirishlar | Root/ADB bilan baribir aylanib o'tiladi | Phase 1 |
| D. Ilovalarni bloklash | Telegram/Instagram/TikTok/YouTube'ni yopish yoki vaqtini cheklash | Ichini filtrlamaydi, ilovani yopadi | Phase 3 |
| E. Heartbeat va xabar | "Qurilma ko'rinmayapti / himoya o'chiq" | Internet o'chiq bo'lsa real vaqtda emas | Phase 2 |

## Phase 0 da tarmoq qanday ishlaydi

```
Ilova (brauzer va h.k.)
   │  DNS so'rovi → 10.111.222.2 (virtual DNS)
   ▼
tun interfeysi  ──  faqat 10.111.222.2/32 shu yerga yo'naltirilgan
   │
   ▼
DnsForwarder  ──  so'rovni o'zgartirmasdan yuboradi
   │  UDP 53 (VPN'dan tashqari soket)
   ▼
Cloudflare for Families: 1.1.1.3, zaxira 1.0.0.3
   │  bloklangan domen uchun 0.0.0.0 qaytaradi
   ▼
javob tun orqali ilovaga qaytadi
```

- Tunnel manzili: `10.111.222.1/32`, virtual DNS: `10.111.222.2`.
- Tizim barcha DNS so'rovlarini virtual DNS'ga yuboradi, IPv6 tarmoqda ham.
- Qolgan trafik (IPv4 va IPv6) tunnelga kirmaydi, odatdagi tarmoqdan o'tadi.
  Shuning uchun tezlik va batareyaga ta'siri kichik.

## Kod tuzilmasi

```
app/src/main/java/uz/himoya/app/
  MainActivity.kt          ruxsatlar va VPN roziligi, ekranni ko'rsatadi
  ui/theme/Theme.kt        ranglar va yirik shriftlar
  ui/home/HomeScreen.kt    bosh ekran: holat va yoqish/o'chirish
  vpn/GuardVpnService.kt   VpnService, tunnel, doimiy bildirishnoma
  vpn/DnsForwarder.kt      tunneldan o'qish, yuqori DNS'ga yuborish
  vpn/DnsPacket.kt         IPv4/UDP paketlarni ajratish va yig'ish (unit test bor)
  vpn/VpnState.kt          xizmat va ekran o'rtasidagi holat
  guard/PinHasher.kt       PIN xeshlash va qulflash muddati (unit test bor)
  guard/PinStore.kt        PIN'ni qurilmada saqlash va tekshirish
  ui/pin/PinScreen.kt      PIN o'rnatish va kiritish ekrani
```

## Ma'lum cheklovlar (hozirgi holat)

- **Bloklangan saytda o'z ekranimiz chiqmaydi.** Brauzer "saytga ulanib bo'lmadi" deb ko'rsatadi.
  HTTPS'ni ochmasdan boshqacha qilib bo'lmaydi. Phase 1 da bildirishnoma orqali blok ekrani ochiladi.
- **Ro'yxatda yo'q DNS xizmatlari.** Mashhur DoH/DoT xizmatlari va ochiq DNS manzillari yopilgan,
  lekin ro'yxatga kirmagan yoki o'zi ko'tarilgan server orqali filtrni aylanib o'tish mumkin.
- **IPv6 orqali to'g'ridan-to'g'ri DNS** (masalan 2001:4860:4860::8888) hali tunnelga olinmagan.
- **TCP orqali DNS** qayta ishlanmaydi (juda kam uchraydi).
- **Telefon qayta yoqilganda** himoya o'zi yoqilmaydi. Phase 1 da.
- **Vaqt mintaqasi qat'iy: Toshkent (UTC+5).** Boshqa mamlakatdagi foydalanuvchilar uchun
  mintaqani ota-ona sozlamasiga chiqarish kerak bo'ladi.
- **Qimor ro'yxati to'liq emas.** Bu saytlar manzilini tez-tez almashtiradi; brend nomi bo'yicha
  tekshiruv ko'pini ushlaydi, lekin hammasini emas.
- **Ota-onaga push-xabar yo'q.** Lokal serverda push yo'q: ota-ona holatni faqat ilovani ochganda
  ko'radi. Haqiqiy backend'da FCM orqali "himoya o'chdi" xabari qo'shiladi.

## Ota-ona PIN kodi (Phase 1.1)

- Birinchi ochilishda ota-ona 4–6 raqamli PIN o'rnatadi.
- Ilova har ochilganda PIN so'raladi; fonga o'tganda qayta qulflanadi. Shuning uchun himoyani
  o'chirish va barcha sozlamalar faqat PIN bilgan odamga ochiq.
- PIN qurilmada oflayn tekshiriladi. Saqlanadigani: tasodifiy tuz va PBKDF2-HMAC-SHA256 xeshi
  (120 000 takror). PIN'ning o'zi saqlanmaydi.
- Har 5 ta xato urinishdan keyin qulf: 30 soniya, 1 daqiqa, 2 daqiqa ... eng ko'pi 15 daqiqa.
- PIN unutilsa, hozircha yagona yo'l ilova ma'lumotlarini tozalash. Tiklash yo'li Phase 2 da.

## "Doim yoqiq VPN" (Phase 1.1)

- Ota-ona tizim sozlamalarida shu ilova uchun "Doim yoqiq VPN"ni yoqadi. Shunda tizim himoyani
  telefon qayta yoqilganda ham o'zi ishga tushiradi. Ilova buni o'zi yoqa olmaydi.
- **"VPN'siz ulanishni bloklash" yoqilmasin.** Bizning VPN faqat DNS'ni tunnelga oladi; bu sozlama
  tunneldan tashqaridagi barcha trafikni to'sadi, ya'ni internet to'xtab qolishi mumkin.
  Ilova bu holatni aniqlab, ogohlantiradi (Android 10+). Emulyatorda sinab tasdiqlash kerak.
- Bola "Doim yoqiq VPN"ni sozlamalardan o'chira oladi. Buni yopish uchun Sozlamalar ilovasini
  PIN bilan qulflash kerak, bu Phase 3 da.

## Lokal qoidalar (Phase 1.2)

Har bir DNS so'rovi oilaviy DNS'ga yuborilishidan oldin `LocalRules` orqali o'tadi:

1. **Shifrlangan DNS xizmatlari bloklanadi.** `dns.google`, `cloudflare-dns.com`, `dns.adguard.com`
   kabi nomlar va ularning quyi domenlari "bunday domen yo'q" javobini oladi. Natijada brauzerdagi
   "Secure DNS" va qo'lda yozilgan Private DNS bu xizmatlar bilan ishlamaydi.
   `use-application-dns.net` ham bloklanadi, shunda Firefox o'z DoH'ini yoqmaydi.
2. **Xavfsiz qidiruv.** Qidiruv tizimi nomi o'rniga uning xavfsiz manzili qaytariladi:

   | So'ralgan nom | Qaytariladigan manzil |
   |---|---|
   | google.com, www.google.* | forcesafesearch.google.com |
   | www.youtube.com, m.youtube.com, youtubei.googleapis.com ... | restrict.youtube.com |
   | www.bing.com | strict.bing.com |
   | duckduckgo.com | safe.duckduckgo.com |
   | yandex.ru/.com/.uz/.kz/.by, ya.ru | familysearch.yandex.ru |

   Yandex va ya.ru yo'naltirishi telefonda sinab tasdiqlanishi kerak.
3. **Mashhur ochiq DNS manzillari tunnelga olinadi** (8.8.8.8, 1.1.1.1, 9.9.9.9 va boshqalar).
   Ularga yuborilgan oddiy DNS filtrdan o'tadi, shifrlangan DNS (853 va 443-port) esa o'tmaydi.
4. **Private DNS tekshiruvi.** Qo'lda yozilgan provayder aniqlansa, bosh ekranda ogohlantirish chiqadi.
   "Avtomatik" rejim filtrga xalaqit bermaydi.

"Bloklandi" hisoblagichi: lokal bloklar va oilaviy DNS 0.0.0.0 qaytargan so'rovlar.

## Blok bildirishnomasi va blok ekrani (Phase 1.3)

- Oilaviy DNS saytni bloklaganda (0.0.0.0 javobi) "Sahifa yopildi" bildirishnomasi chiqadi.
  Bosilsa blok ekrani ochiladi: sabab, domen va "Tushunarli" tugmasi.
- Brauzerning o'zida bola baribir "saytga ulanib bo'lmadi" xatosini ko'radi; HTTPS ochilmagani
  uchun sahifa ichida o'z ekranimizni ko'rsatib bo'lmaydi.
- Bitta domen uchun 30 soniyada ko'pi bilan bitta xabar. Yangi xabar eskisini almashtiradi.
- Shifrlangan DNS xizmatlarining bloklanishi (masalan `dns.google`) uchun xabar chiqmaydi:
  ularni fon ilovalari ham so'raydi va bola uchun ma'nosi yo'q.
- Blok ekrani PIN so'ramaydi va undan hech narsani o'zgartirib bo'lmaydi.
- "Ruxsat so'rash" tugmasi Phase 2 da (server kerak).

## Oila qoidalari (Phase 1.4)

Qoidalar `Policy` da saqlanadi (qurilmada, `SharedPreferences`), "Sozlamalar" ekranidan boshqariladi.
Ekran PIN ortida.

**Qaror tartibi** (har bir DNS so'rovi uchun, `LocalRules.decide`):

1. Shifrlangan DNS xizmati → blok (ruxsat ro'yxati ham bekor qila olmaydi)
2. Qidiruv tizimi → xavfsiz qidiruv manzili
3. Ruxsat berilgan saytlar ro'yxatida → filtrsiz DNS (1.1.1.1) orqali ochiladi
4. Yopilgan saytlar ro'yxatida → blok
5. Qimor (yoqilgan bo'lsa): aniq ro'yxat yoki brend so'zi → blok
6. Qolgani → oilaviy DNS (1.1.1.3)

Ro'yxatdagi domen o'zining barcha quyi domenlarini ham qamraydi (`tiktok.com` → `www.tiktok.com`).

**Uyqu vaqti.** Boshlanish va tugash vaqti 30 daqiqalik qadam bilan belgilanadi, yarim tundan
o'tishi mumkin. Uyqu vaqtida tunnel boshqa rejimda qayta quriladi: butun IPv4/IPv6 trafik tunnelga
yo'naltiriladi va tashlab yuboriladi (o'qilmaydi), DNS so'rovlari "topilmadi" javobini oladi.
Qo'ng'iroq va SMS ishlayveradi.

**Vaqt manbai.** Uyqu vaqti telefon soatiga emas, internet vaqtiga tayanadi (`TrustedClock`).
Himoya yoqilganda vaqt serveridan (Google Public NTP, Cloudflare Time; SNTP, UDP 123) vaqt olinadi
va telefon yoqilgandan beri o'tgan vaqt hisoblagichiga (`elapsedRealtime`) bog'lanadi. Bu
hisoblagichni foydalanuvchi o'zgartira olmaydi, shuning uchun sozlamalarda soatni surish yordam
bermaydi. Vaqt har 6 soatda yangilanadi. Vaqt hali olinmagan bo'lsa (telefon endi yoqilgan va
tarmoq yo'q) vaqtincha telefon soati ishlatiladi; internet paydo bo'lishi bilan to'g'rilanadi.
Vaqt Toshkent mintaqasi bo'yicha hisoblanadi.

Chegara uch joyda tekshiriladi: har DNS so'rovida (sekundiga
ko'pi bilan bir marta), ekran yoqilganda va daqiqada bir.

## Oila sinxronizatsiyasi (Phase 2.1, vaqtinchalik lokal server)

Bitta ilova, ikki rol. Rol birinchi ochilishda (PIN'dan keyin) tanlanadi:

- **Farzand telefoni**: filtr shu yerda ishlaydi. Sozlamalarda "Ota-ona telefoni" bo'limi orqali
  6 xonali kod bilan bog'lanadi.
- **Ota-ona telefoni**: VPN ishlatilmaydi. Bolalar qurilmalari holati, oxirgi yopilgan saytlar va
  qoidalarni masofadan o'zgartirish.

**Server** — `server/server.py`, faqat Python standart kutubxonasi, ma'lumotlar `server/data.json` da.
Bu ishlab chiqish uchun vaqtinchalik server: HTTP (shifrlashsiz), internetga ochilmasligi kerak.
Debug yig'masidagina shifrlashsiz ulanishga ruxsat bor (`app/src/debug/AndroidManifest.xml`).

| So'rov | Kim | Vazifasi |
|---|---|---|
| `POST /api/family` | ota-ona | Oila yaratadi, ota-ona kalitini qaytaradi |
| `POST /api/pair-code` | ota-ona | Bola ismi bilan 6 xonali kod (10 daqiqa, bir martalik) |
| `POST /api/pair` | bola | Kod bo'yicha qurilma kalitini oladi (daqiqasiga 10 urinish) |
| `POST /api/device/sync` | bola | Holat va blok yozuvlarini yuboradi, qoidalarni oladi |
| `GET /api/devices` | ota-ona | Qurilmalar holati, qoidalar, oxirgi 30 ta blok |
| `PUT /api/devices/{id}/policy` | ota-ona | Qoidalarni o'zgartiradi |
| `DELETE /api/devices/{id}` | ota-ona | Qurilmani ro'yxatdan o'chiradi |

**Sinxronlash.** Bola telefoni himoya yoqiq paytda har 15 soniyada `sync` yuboradi (telefon
uxlaganda to'xtaydi). Qoidalar versiya raqami bilan yuradi: ota-ona o'zgartirsa versiya oshadi va
bola telefoni keyingi sinxronlashda yangi qoidalarni qo'llaydi. Qoidalar bola telefonining o'zida
(PIN bilan) o'zgartirilsa, ular serverga yuboriladi. Bir vaqtda ikki tomondan o'zgartirilsa,
oxirgi yozgan yutadi.

**Holat.** Himoya o'chirilganda bola telefoni oxirgi marta "himoya o'chiq" deb xabar beradi.
50 soniyadan ko'p xabar kelmasa, ota-ona ekranida "Ko'rinmayapti" chiqadi.

**Serverga nima yuboriladi.** Himoya holati, hisoblagichlar, ilova versiyasi, qoidalar va blok
yozuvlari (faqat domen, sabab, vaqt). Ochilgan (bloklanmagan) saytlar, sahifa manzillari, qidiruv
so'zlari va sahifa mazmuni yuborilmaydi.

**PIN** har bir telefonda alohida va serverga yuborilmaydi.

Keyingi qadam (Phase 2.2): bola "Ruxsat so'rash" yuboradi, ota-ona 15 daqiqa / 1 soat / bugun
ruxsat beradi yoki rad etadi.

## Ochiq savollar

- Yakuniy brend nomi va `applicationId` (hozir `uz.himoya.app`).
- Cloudflare for Families'ni tijorat mahsulotida ishlatish shartlari.
- Server tomoni: Firebase o'rniga Cloudflare Workers + D1 va bepul FCM varianti.
