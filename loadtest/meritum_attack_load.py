#!/usr/bin/env python3
"""Testy obciążeniowe i scenariusze ataku na działający stos MERITUM (`make up`).

Tylko biblioteka standardowa (Python 3.9+). Domyślnie: backend :8080, ai-gateway :8000, frontend :3000, gotenberg :3001.
Skrypt NIE zmienia stanu cykli — wykonuje odczyty, logowania i wywołania AI (te tworzą wpisy w audit_logs;
po teście można przywrócić seed przez `make reset`).

    python3 loadtest/meritum_attack_load.py            # pełny zestaw
    python3 loadtest/meritum_attack_load.py --quick    # mniejsze wolumeny
"""
import argparse
import json
import random
import statistics
import string
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional, Tuple

TIMEOUT_S = 60
HR, EMPLOYEE, ADMIN = 4, 3, 5
MAX_CYCLE_ID = 17


@dataclass
class Result:
    name: str
    statuses: Dict[int, int] = field(default_factory=dict)
    latencies: List[float] = field(default_factory=list)
    wall: float = 0.0
    note: str = ""

    def add(self, status: int, latency: float) -> None:
        self.statuses[status] = self.statuses.get(status, 0) + 1
        self.latencies.append(latency)

    def pct(self, p: float) -> float:
        if not self.latencies:
            return 0.0
        s = sorted(self.latencies)
        return s[min(len(s) - 1, int(p / 100 * len(s)))] * 1000

    def row(self) -> str:
        n = len(self.latencies)
        rps = n / self.wall if self.wall else 0
        codes = " ".join(f"{k}×{v}" for k, v in sorted(self.statuses.items()))
        return (f"{self.name:<44} n={n:<5} rps={rps:7.1f}  p50={self.pct(50):7.1f}ms  p95={self.pct(95):7.1f}ms  "
                f"p99={self.pct(99):8.1f}ms  [{codes}] {self.note}")


def request(method: str, url: str, body: Optional[bytes] = None, headers: Optional[Dict[str, str]] = None) -> Tuple[int, bytes, Dict[str, str], float]:
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_S) as res:
            data = res.read()
            return res.status, data, dict(res.headers), time.perf_counter() - start
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers), time.perf_counter() - start
    except Exception:  # noqa: BLE001 — timeout / odmowa połączenia liczymy jako status 0
        return 0, b"", {}, time.perf_counter() - start


def jbody(obj) -> bytes:
    return json.dumps(obj).encode()


JSON = {"Content-Type": "application/json"}


def burst(name: str, n: int, concurrency: int, call: Callable[[int], Tuple[int, float]], note: str = "") -> Result:
    res = Result(name, note=note)
    lock = threading.Lock()

    def one(i: int) -> None:
        status, latency = call(i)
        with lock:
            res.add(status, latency)

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(one, range(n)))
    res.wall = time.perf_counter() - start
    return res


class Stack:
    def __init__(self, backend: str, ai: str, frontend: str, gotenberg: str):
        self.backend, self.ai, self.frontend, self.gotenberg = backend, ai, frontend, gotenberg
        self.tokens: Dict[int, str] = {}

    def token(self, user_id: int) -> str:
        if user_id not in self.tokens:
            status, data, _, _ = request("POST", f"{self.backend}/api/auth/gov-login", jbody({"userId": user_id}), JSON)
            if status != 200:
                raise SystemExit(f"gov-login dla {user_id} zwrócił {status} — czy stos działa (`make up`)?")
            self.tokens[user_id] = json.loads(data)["accessToken"]
        return self.tokens[user_id]

    def auth(self, user_id: int) -> Dict[str, str]:
        return {"Authorization": f"Bearer {self.token(user_id)}", **JSON}

    def get(self, path: str, user_id: int) -> Tuple[int, float]:
        s, _, _, t = request("GET", self.backend + path, headers=self.auth(user_id))
        return s, t


def socratic_body(notes: List[str]) -> bytes:
    return jbody({"employee_name": "Anna Nowak", "sheet_type": "C",
                  "criteria_scores": [{"name": "Rzetelność", "score": 5}], "notes": notes})


def run(args) -> None:
    st = Stack(args.backend, args.ai, args.frontend, args.gotenberg)
    scale = 0.25 if args.quick else 1.0
    n = lambda x: max(10, int(x * scale))  # noqa: E731
    results: List[Result] = []
    findings: List[Tuple[str, str]] = []

    def finding(severity: str, text: str) -> None:
        findings.append((severity, text))

    for uid in (1, 2, EMPLOYEE, HR, ADMIN):  # tokeny przed testem zalewania logowania (limit 429)
        st.token(uid)

    print("== Obciążenie ==")
    results.append(burst("GET /api/health (bez auth)", n(1000), 50,
                         lambda i: request("GET", f"{st.backend}/api/health")[::3]))
    results.append(burst("GET /api/cycles (HR, Bearer)", n(1000), 50, lambda i: st.get("/api/cycles", HR)))
    results.append(burst("GET /api/stats/summary (DG agregaty)", n(300), 30, lambda i: st.get("/api/stats/summary", 1)))
    results.append(burst("GET /api/notifications (Kierownik)", n(300), 30, lambda i: st.get("/api/notifications", 2)))
    results.append(burst("GET /api/hr/reports/export CSV", n(200), 20, lambda i: st.get("/api/hr/reports/export", HR)))
    ai = burst("POST /api/core-ai/socratic-draft (MOCK)", n(200), 20,
               lambda i: request("POST", f"{st.backend}/api/core-ai/socratic-draft?cycleId=1",
                                 socratic_body([f"Notatka {i}: terminowo wdrożyła moduł."]), st.auth(2))[::3],
               note="(429 powyżej limitu AI na użytkownika)")
    results.append(ai)
    if 429 not in ai.statuses:
        finding("WYSOKIE", f"Brak rate-limitingu AI: {ai.statuses}")
    pdf = burst("GET /api/cycles/1/pdf (Gotenberg)", n(40), 10, lambda i: st.get("/api/cycles/1/pdf", HR))
    results.append(pdf)
    if pdf.statuses.get(200, 0) < len(pdf.latencies):
        finding("ŚREDNIE", f"Eksport PDF pod obciążeniem: {pdf.statuses} — Gotenberg jest wąskim gardłem (brak kolejki/cache).")

    print("== Logowanie i uwierzytelnienie ==")
    login = burst("POST /api/auth/gov-login flood", n(300), 30,
                  lambda i: request("POST", f"{st.backend}/api/auth/gov-login", jbody({"userId": random.choice([1, 2, 3, 4, 5])}), JSON)[::3])
    results.append(login)
    if 429 not in login.statuses:
        finding("WYSOKIE", f"Brak rate-limitingu logowania: {login.statuses.get(200, 0)}/{len(login.latencies)} żądań gov-login "
                           "przyjętych — każde wydaje JWT dowolnej osobie (symulacja WK bez weryfikacji tożsamości).")
    rand_tok = lambda: ".".join("".join(random.choices(string.ascii_letters, k=30)) for _ in range(3))  # noqa: E731
    brute = burst("Bearer brute-force (losowe tokeny)", n(300), 30,
                  lambda i: request("GET", f"{st.backend}/api/cycles", headers={"Authorization": f"Bearer {rand_tok()}"})[::3])
    results.append(brute)
    if set(brute.statuses) != {401}:
        finding("KRYTYCZNE", f"Losowe tokeny nie zawsze dają 401: {brute.statuses}")
    status, _, _, _ = request("GET", f"{st.backend}/api/audit-logs?limit=5", headers={"X-Demo-User-Id": str(ADMIN)})
    if status == 200:
        finding("KRYTYCZNE (prod)", "X-Demo-User-Id włączony: dowolny klient podszywa się pod Admina KPRM bez tokena "
                                    "(DEMO_HEADER_ENABLED=true). W produkcji MUSI być false.")
    status, data, _, _ = request("GET", f"{st.backend}/api/auth/demo-users")
    if status == 200 and b"@" in data:
        finding("ŚREDNIE", f"/api/auth/demo-users jest publiczne i zwraca e-maile/stanowiska {len(json.loads(data))} osób (wyciek PII).")

    print("== Autoryzacja (IDOR) ==")
    own = {c["id"] for c in json.loads(request("GET", f"{st.backend}/api/cycles", headers=st.auth(EMPLOYEE))[1])}
    leaks = [cid for cid in range(1, MAX_CYCLE_ID + 1)
             if cid not in own and st.get(f"/api/cycles/{cid}", EMPLOYEE)[0] == 200]
    print(f"   oceniany #{EMPLOYEE}: własne cykle {sorted(own)}, cudze dostępne: {leaks or 'brak'}")
    if leaks:
        finding("KRYTYCZNE", f"IDOR: oceniany widzi cudze cykle {leaks}")
    for cid in sorted(own):
        if st.get(f"/api/cycles/{cid}/hr-facts", EMPLOYEE)[0] == 200:
            finding("NISKIE", f"Oceniany widzi /hr-facts własnego cyklu #{cid} — liczbę i datę prywatnych notatek kierownika "
                              "(sprzeczne z decyzją D4: oceniany nie widzi bieżących notatek).")
            break

    print("== Granica AI (AI Act art. 26 — audyt) ==")
    for label, url in [("frontend proxy /api/ai/*", f"{st.frontend}/api/ai/socratic-draft"),
                       ("ai-gateway bezpośrednio :8000", f"{st.ai}/api/ai/socratic-draft")]:
        s, _, _, _ = request("POST", url, socratic_body(["test"]), JSON)
        print(f"   {label}: HTTP {s} bez uwierzytelnienia")
        if s == 200:
            finding("WYSOKIE", f"Obejście audytu AI: {label} odpowiada bez logowania i bez wpisu w audit_logs.")
    s, _, _, _ = request("GET", f"{st.gotenberg}/health")
    if s == 200:
        finding("ŚREDNIE", "Gotenberg wystawiony na hoście (:3001) bez uwierzytelnienia — konwersja HTML→PDF dla każdego (SSRF/DoS).")

    print("== Duże i złośliwe payloady ==")
    big = "Pracownik terminowo realizuje zadania. " * 25_000  # ~1 MB tekstu „łagodnego”
    s, _, _, t = request("POST", f"{st.backend}/api/core-ai/socratic-draft?cycleId=1", socratic_body([big]), st.auth(1))
    print(f"   1 MB notatki przez core-ai: HTTP {s} w {t:.2f}s")
    if s == 200:
        finding("ŚREDNIE", f"Brak limitu rozmiaru: 1 MB tekstu przyjęte ({t:.1f}s); masked_prompt trafia w całości do audit_logs (JSONB).")
    adversarial = "Ab" * 15_000  # 30 KB — wcześniej koszt kwadratowy w PiiMasker
    s, _, _, t = request("POST", f"{st.backend}/api/core-ai/socratic-draft?cycleId=1", socratic_body([adversarial]), st.auth(HR))
    print(f"   30 KB złośliwego tekstu („AbAb…”) przez core-ai: HTTP {s} w {t:.2f}s")
    if t > 2:
        finding("WYSOKIE", f"DoS algorytmiczny w PiiMasker: 30 KB tekstu = {t:.1f}s na jedno żądanie.")

    print("== Wstrzyknięcia i nagłówki ==")
    for q in ["' OR '1'='1", "\"; DROP TABLE users; --", "%"]:
        s, _, _, _ = request("GET", f"{st.backend}/api/hr/employees?q={urllib.request.quote(q)}", headers=st.auth(HR))
        if s >= 500:
            finding("WYSOKIE", f"Błąd 5xx dla q={q!r}")
    s, _, h, _ = request("OPTIONS", f"{st.backend}/api/cycles",
                         headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"})
    if h.get("Access-Control-Allow-Origin"):
        finding("WYSOKIE", f"CORS akceptuje obcy origin: {h.get('Access-Control-Allow-Origin')}")
    _, _, h, _ = request("GET", f"{st.backend}/api/health")
    missing = [k for k in ("X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy") if k not in h]
    if missing:
        finding("NISKIE", f"Brak nagłówków bezpieczeństwa: {missing}")
    if "Strict-Transport-Security" not in h and "Content-Security-Policy" not in h:
        finding("NISKIE", "Brak HSTS i Content-Security-Policy (wymagane za reverse proxy z TLS w produkcji).")

    print("\n== WYNIKI OBCIĄŻENIA ==")
    for r in results:
        print(r.row())
    errors = sum(v for r in results for k, v in r.statuses.items() if k == 0 or k >= 500)
    print(f"\nBłędy 5xx / timeouty łącznie: {errors}")
    print("\n== USTALENIA ==")
    for sev, text in findings:
        print(f" [{sev}] {text}")
    if not findings:
        print(" brak")


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--backend", default="http://localhost:8080")
    p.add_argument("--ai", default="http://localhost:8000")
    p.add_argument("--frontend", default="http://localhost:3000")
    p.add_argument("--gotenberg", default="http://localhost:3001")
    p.add_argument("--quick", action="store_true", help="mniejsze wolumeny (≈25%)")
    run(p.parse_args())
