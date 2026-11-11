#!/usr/bin/env python3
"""Oilaviy Himoya — vaqtinchalik lokal server (faqat ishlab chiqish va sinov uchun).

Ota-ona va bola telefonlari shu server orqali bog'lanadi: juftlash (pairing),
qoidalarni sinxronlash va bola qurilmasining holati.

Ishga tushirish:   python server/server.py
Standart manzil:   http://0.0.0.0:8787  (emulyatordan: http://10.0.2.2:8787)

Diqqat: bu server shifrlashsiz (HTTP) ishlaydi va internetga ochilmasligi kerak.
Haqiqiy foydalanish uchun keyin HTTPS va push-xabarli backend'ga ko'chiriladi.
Qo'shimcha kutubxona kerak emas, faqat Python 3.8+.
"""
import json
import os
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("HIMOYA_HOST", "0.0.0.0")
PORT = int(os.environ.get("HIMOYA_PORT", "8787"))
DATA_FILE = os.environ.get(
    "HIMOYA_DATA", os.path.join(os.path.dirname(os.path.abspath(__file__)), "data.json")
)

PAIR_CODE_TTL = 600          # juftlash kodi 10 daqiqa amal qiladi
MAX_EVENTS = 100             # har bir qurilma uchun saqlanadigan oxirgi bloklar
MAX_BODY = 256 * 1024
MAX_LIST = 500               # ro'yxatdagi domenlar soni chegarasi
PAIR_ATTEMPTS_PER_MINUTE = 10
MAX_REQUESTS = 50            # har bir qurilma uchun saqlanadigan ruxsat so'rovlari
REQUEST_KEEP_MS = 7 * 24 * 3600 * 1000
TASHKENT_OFFSET_MS = 5 * 3600 * 1000
DAY_MS = 24 * 3600 * 1000

LOCK = threading.Lock()
DATA = {"families": {}, "devices": {}}
CODES = {}                   # kod -> {familyId, childName, expiresAt}; faqat xotirada
PAIR_ATTEMPTS = {}           # ip -> [vaqtlar]


def now_ms():
    return int(time.time() * 1000)


def load():
    global DATA
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            DATA = json.load(f)
    DATA.setdefault("families", {})
    DATA.setdefault("devices", {})


def save():
    tmp = DATA_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(DATA, f, ensure_ascii=False, indent=1)
    os.replace(tmp, DATA_FILE)


def end_of_day_tashkent(ms):
    """Toshkent vaqti bo'yicha shu kunning oxiri (yarim tun), UTC millisekundda."""
    local = ms + TASHKENT_OFFSET_MS
    return (local // DAY_MS + 1) * DAY_MS - TASHKENT_OFFSET_MS


def prune_requests(d):
    """Eski so'rovlarni tashlaydi va muddati o'tgan ruxsatlarni belgilaydi."""
    now = now_ms()
    kept = []
    for r in d.get("requests", []):
        if r["createdAt"] < now - REQUEST_KEEP_MS:
            continue
        if r["status"] == "APPROVED" and r.get("until", 0) <= now:
            r["status"] = "EXPIRED"
        kept.append(r)
    d["requests"] = kept[-MAX_REQUESTS:]
    return d["requests"]


def temp_allow(d):
    """Hozir amalda bo'lgan vaqtinchalik ruxsatlar."""
    return [
        {"domain": r["domain"], "until": r["until"]}
        for r in prune_requests(d)
        if r["status"] == "APPROVED"
    ]


def clean_domain(value):
    if not isinstance(value, str):
        raise ApiError(400, "domen kerak")
    domain = value.strip().lower().rstrip(".")
    if domain.startswith("www."):
        domain = domain[4:]
    ok = 3 < len(domain) <= 253 and "." in domain and all(
        c.isascii() and (c.isalnum() or c in "-.") for c in domain
    )
    if not ok:
        raise ApiError(400, "domen noto'g'ri")
    return domain


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def clean_domains(value):
    if not isinstance(value, list):
        return []
    out = []
    for item in value[:MAX_LIST]:
        if isinstance(item, str) and 0 < len(item) <= 253:
            out.append(item.strip().lower())
    return sorted(set(out))


def clean_minute(value, default):
    return value if isinstance(value, int) and not isinstance(value, bool) and 0 <= value < 1440 else default


def clean_policy(raw):
    """Faqat ma'lum maydonlarni, to'g'ri turda qabul qiladi."""
    if not isinstance(raw, dict):
        raise ApiError(400, "policy noto'g'ri")
    return {
        "bedtimeEnabled": bool(raw.get("bedtimeEnabled", False)),
        "bedtimeStart": clean_minute(raw.get("bedtimeStart"), 22 * 60),
        "bedtimeEnd": clean_minute(raw.get("bedtimeEnd"), 7 * 60),
        "blockGambling": bool(raw.get("blockGambling", True)),
        "blacklist": clean_domains(raw.get("blacklist")),
        "whitelist": clean_domains(raw.get("whitelist")),
    }


def clean_status(raw):
    if not isinstance(raw, dict):
        return {}
    def num(key):
        v = raw.get(key, 0)
        return v if isinstance(v, int) and not isinstance(v, bool) and v >= 0 else 0
    always_on = raw.get("alwaysOn")
    return {
        "protectionOn": bool(raw.get("protectionOn", False)),
        "bedtimeActive": bool(raw.get("bedtimeActive", False)),
        "alwaysOn": always_on if isinstance(always_on, bool) else None,
        "checked": num("checked"),
        "blocked": num("blocked"),
        "appVersion": str(raw.get("appVersion", ""))[:40],
    }


def clean_events(raw):
    out = []
    if isinstance(raw, list):
        for e in raw[:50]:
            if not isinstance(e, dict):
                continue
            domain, reason, at = e.get("domain"), e.get("reason"), e.get("at")
            if isinstance(domain, str) and isinstance(reason, str) and isinstance(at, int):
                out.append({"domain": domain[:253], "reason": reason[:40], "at": at})
    return out


def device_view(device_id, d):
    return {
        "id": device_id,
        "name": d["name"],
        "lastSeenAt": d.get("lastSeenAt", 0),
        "status": d.get("status", {}),
        "policy": d.get("policy"),
        "policyVersion": d.get("policyVersion", 0),
        "events": list(reversed(d.get("events", [])))[:30],
        "requests": list(reversed(prune_requests(d)))[:20],
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "HimoyaDev/0.1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s  %s\n" % (time.strftime("%H:%M:%S"), fmt % args))

    # ---- yordamchi funksiyalar

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            raise ApiError(413, "so'rov juda katta")
        if length == 0:
            return {}
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "JSON noto'g'ri")
        if not isinstance(data, dict):
            raise ApiError(400, "JSON obyekt bo'lishi kerak")
        return data

    def token(self):
        header = self.headers.get("Authorization", "")
        return header[7:].strip() if header.startswith("Bearer ") else ""

    def parent_family(self):
        token = self.token()
        if token:
            for family_id, family in DATA["families"].items():
                if secrets.compare_digest(family["parentToken"], token):
                    return family_id
        raise ApiError(401, "ota-ona tokeni noto'g'ri")

    def device(self):
        token = self.token()
        if token:
            for device_id, d in DATA["devices"].items():
                if secrets.compare_digest(d["token"], token):
                    return device_id, d
        raise ApiError(401, "qurilma tokeni noto'g'ri")

    def owned_device(self, family_id, device_id):
        d = DATA["devices"].get(device_id)
        if d is None or d["familyId"] != family_id:
            raise ApiError(404, "qurilma topilmadi")
        return d

    # ---- yo'naltirish

    def handle_request(self, method):
        path = self.path.split("?", 1)[0].rstrip("/")
        parts = [p for p in path.split("/") if p]
        try:
            body = self.read_json() if method in ("POST", "PUT") else {}
            with LOCK:
                status, payload = self.route(method, parts, body)
            self.send_json(status, payload)
        except ApiError as e:
            self.send_json(e.status, {"error": e.message})
        except Exception as e:  # kutilmagan xato server ishini to'xtatmasin
            self.log_message("xato: %r", e)
            self.send_json(500, {"error": "server xatosi"})

    def do_GET(self):
        self.handle_request("GET")

    def do_POST(self):
        self.handle_request("POST")

    def do_PUT(self):
        self.handle_request("PUT")

    def do_DELETE(self):
        self.handle_request("DELETE")

    def route(self, method, parts, body):
        if parts[:1] != ["api"]:
            raise ApiError(404, "topilmadi")
        rest = parts[1:]

        if method == "GET" and rest == ["health"]:
            return 200, {"ok": True, "serverTime": now_ms()}

        # --- ota-ona
        if method == "POST" and rest == ["family"]:
            family_id = secrets.token_hex(8)
            token = secrets.token_urlsafe(32)
            DATA["families"][family_id] = {"parentToken": token, "createdAt": now_ms()}
            save()
            return 200, {"familyId": family_id, "parentToken": token}

        if method == "POST" and rest == ["pair-code"]:
            family_id = self.parent_family()
            name = str(body.get("childName", "")).strip()[:40]
            if not name:
                raise ApiError(400, "bola ismi kerak")
            for code in [c for c, v in CODES.items() if v["expiresAt"] < now_ms()]:
                del CODES[code]
            while True:
                code = "%06d" % secrets.randbelow(1_000_000)
                if code not in CODES:
                    break
            CODES[code] = {
                "familyId": family_id,
                "childName": name,
                "expiresAt": now_ms() + PAIR_CODE_TTL * 1000,
            }
            return 200, {"code": code, "expiresInSeconds": PAIR_CODE_TTL}

        if method == "GET" and rest == ["devices"]:
            family_id = self.parent_family()
            devices = [
                device_view(i, d) for i, d in DATA["devices"].items() if d["familyId"] == family_id
            ]
            devices.sort(key=lambda d: d["name"].lower())
            return 200, {"serverTime": now_ms(), "devices": devices}

        if len(rest) == 3 and rest[0] == "devices" and rest[2] == "policy" and method == "PUT":
            family_id = self.parent_family()
            d = self.owned_device(family_id, rest[1])
            d["policy"] = clean_policy(body.get("policy"))
            d["policyVersion"] = d.get("policyVersion", 0) + 1
            save()
            return 200, {"policyVersion": d["policyVersion"]}

        if len(rest) == 2 and rest[0] == "devices" and method == "DELETE":
            family_id = self.parent_family()
            self.owned_device(family_id, rest[1])
            del DATA["devices"][rest[1]]
            save()
            return 200, {"ok": True}

        # --- bola qurilmasi
        if method == "POST" and rest == ["pair"]:
            ip = self.client_address[0]
            recent = [t for t in PAIR_ATTEMPTS.get(ip, []) if t > time.time() - 60]
            if len(recent) >= PAIR_ATTEMPTS_PER_MINUTE:
                raise ApiError(429, "juda ko'p urinish, bir daqiqadan keyin urinib ko'ring")
            recent.append(time.time())
            PAIR_ATTEMPTS[ip] = recent

            code = str(body.get("code", "")).strip()
            entry = CODES.get(code)
            if entry is None or entry["expiresAt"] < now_ms():
                CODES.pop(code, None)
                raise ApiError(404, "kod noto'g'ri yoki muddati o'tgan")
            del CODES[code]  # kod bir martalik
            device_id = secrets.token_hex(8)
            token = secrets.token_urlsafe(32)
            DATA["devices"][device_id] = {
                "familyId": entry["familyId"],
                "token": token,
                "name": entry["childName"],
                "policy": None,
                "policyVersion": 0,
                "status": {},
                "lastSeenAt": now_ms(),
                "events": [],
                "requests": [],
                "createdAt": now_ms(),
            }
            save()
            return 200, {"deviceId": device_id, "deviceToken": token, "childName": entry["childName"]}

        if method == "POST" and rest == ["device", "sync"]:
            _, d = self.device()
            changed = False
            d["lastSeenAt"] = now_ms()
            d["status"] = clean_status(body.get("status"))
            events = clean_events(body.get("events"))
            if events:
                d["events"] = (d.get("events", []) + events)[-MAX_EVENTS:]
                changed = True
            if body.get("policy") is not None:
                # Qoidalar bola telefonida (PIN bilan) o'zgartirilgan.
                d["policy"] = clean_policy(body["policy"])
                d["policyVersion"] = d.get("policyVersion", 0) + 1
                changed = True
            if changed:
                save()
            return 200, {
                "policy": d.get("policy"),
                "policyVersion": d.get("policyVersion", 0),
                "tempAllow": temp_allow(d),
                "serverTime": now_ms(),
            }

        # Bola: "Ruxsat so'rash"
        if method == "POST" and rest == ["device", "requests"]:
            _, d = self.device()
            domain = clean_domain(body.get("domain"))
            note = str(body.get("note", "")).strip()[:120]
            requests = prune_requests(d)
            for r in requests:
                # Shu sayt uchun javobsiz so'rov bor bo'lsa, yangisi ochilmaydi.
                if r["domain"] == domain and r["status"] == "PENDING":
                    if note:
                        r["note"] = note
                        save()
                    return 200, r
            request = {
                "id": secrets.token_hex(8),
                "domain": domain,
                "note": note,
                "status": "PENDING",
                "until": 0,
                "createdAt": now_ms(),
            }
            requests.append(request)
            d["requests"] = requests[-MAX_REQUESTS:]
            save()
            return 200, request

        if method == "GET" and len(rest) == 3 and rest[:2] == ["device", "requests"]:
            _, d = self.device()
            for r in prune_requests(d):
                if r["id"] == rest[2]:
                    return 200, dict(r, serverTime=now_ms())
            raise ApiError(404, "so'rov topilmadi")

        # Ota-ona: so'rovga javob
        if method == "POST" and len(rest) == 3 and rest[0] == "requests" and rest[2] == "resolve":
            family_id = self.parent_family()
            for d in DATA["devices"].values():
                if d["familyId"] != family_id:
                    continue
                for r in prune_requests(d):
                    if r["id"] != rest[1]:
                        continue
                    action = body.get("action")
                    if action == "deny":
                        r["status"] = "DENIED"
                        r["until"] = 0
                    elif action == "allow":
                        minutes = body.get("minutes")
                        if minutes == "today":
                            r["until"] = end_of_day_tashkent(now_ms())
                        elif isinstance(minutes, int) and not isinstance(minutes, bool) and 1 <= minutes <= 1440:
                            r["until"] = now_ms() + minutes * 60_000
                        else:
                            raise ApiError(400, "muddat noto'g'ri")
                        r["status"] = "APPROVED"
                    else:
                        raise ApiError(400, "action noto'g'ri")
                    r["resolvedAt"] = now_ms()
                    save()
                    return 200, r
            raise ApiError(404, "so'rov topilmadi")

        if method == "POST" and rest == ["device", "unpair"]:
            device_id, _ = self.device()
            del DATA["devices"][device_id]
            save()
            return 200, {"ok": True}

        raise ApiError(404, "topilmadi")


def main():
    load()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    print("Oilaviy Himoya lokal serveri: http://%s:%d" % (HOST, PORT))
    print("Emulyatordan manzil:          http://10.0.2.2:%d" % PORT)
    print("Ma'lumotlar fayli:            %s" % DATA_FILE)
    print("To'xtatish uchun Ctrl+C")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nTo'xtatildi.")


if __name__ == "__main__":
    main()
