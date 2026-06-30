"""Auto-provision the demo's HTTPS cert for the CURRENT LAN IP.

The phone needs HTTPS (camera = secure context) and the cert must match the host it connects to.
The PC's IP is handed out by DHCP and CHANGES, so a hardcoded cert/IP breaks phone access. This
detects the current LAN IPv4, (re)generates a self-signed cert covering that IP + 127.0.0.1 +
localhost when needed, and prints the exact URL to open on the phone.

Run (from anywhere):  python frontend/demo/setup_cert.py
It only regenerates when the IP changed, so the phone keeps trusting the same cert across restarts.
"""
from __future__ import annotations

import os
import shutil
import socket
import ssl
import subprocess
import sys
from pathlib import Path

CERT_DIR = Path(__file__).with_name("certs")
CERT = CERT_DIR / "cert.pem"
KEY = CERT_DIR / "key.pem"
PORT = 8095


def lan_ip() -> str:
    """Best-effort primary LAN IPv4 (no traffic actually sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def cert_covers(ip: str) -> bool:
    if not CERT.exists():
        return False
    try:
        info = ssl._ssl._test_decode_cert(str(CERT))  # type: ignore[attr-defined]
        sans = {v for typ, v in info.get("subjectAltName", ()) if typ == "IP Address"}
        return ip in sans
    except Exception:  # noqa: BLE001
        return False


def find_openssl() -> str | None:
    found = shutil.which("openssl")
    if found:
        return found
    for cand in (r"C:\Program Files\Git\mingw64\bin\openssl.exe",
                 r"C:\Program Files\Git\usr\bin\openssl.exe"):
        if os.path.exists(cand):
            return cand
    return None


def regenerate(ip: str) -> None:
    openssl = find_openssl()
    if not openssl:
        sys.exit("openssl khong tim thay — cai Git for Windows (kem openssl) hoac them openssl vao PATH.")
    CERT_DIR.mkdir(exist_ok=True)
    subprocess.run([
        openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes",
        "-keyout", str(KEY), "-out", str(CERT), "-days", "825",
        "-subj", f"/CN={ip}",
        "-addext", f"subjectAltName=IP:{ip},IP:127.0.0.1,DNS:localhost",
    ], check=True)


def main() -> None:
    ip = lan_ip()
    if cert_covers(ip):
        status = f"cert da khop IP {ip} (khong sinh lai)"
    else:
        regenerate(ip)
        status = f"da sinh cert moi cho IP {ip} (dien thoai can bam Tin tuong/Proceed lan dau)"
    print("=" * 60)
    print(f"  {status}")
    print("  MO TREN:")
    print(f"     Laptop:     https://localhost:{PORT}")
    print(f"     Dien thoai: https://{ip}:{PORT}   (cung Wi-Fi)")
    print("=" * 60)


if __name__ == "__main__":
    main()
