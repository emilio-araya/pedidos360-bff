#!/usr/bin/env python3
"""Genera un JWT HMAC solo para el perfil local. No usar fuera de desarrollo."""

import argparse
import base64
import hashlib
import hmac
import json
import os
import time

parser = argparse.ArgumentParser()
parser.add_argument("--role", choices=["Admin", "Operador", "Cliente"], default="Operador")
parser.add_argument("--oid", default="local-user-oid")
parser.add_argument("--hours", type=int, default=1)
args = parser.parse_args()

secret = os.environ.get(
    "LOCAL_JWT_SECRET",
    "pedidos360-local-secret-change-me-32-bytes-minimum",
).encode("utf-8")
now = int(time.time())
header = {"alg": "HS256", "typ": "JWT"}
payload = {
    "iss": "https://login.microsoftonline.com/pedidos360-local/v2.0",
    "aud": "api://150f51db-4084-4979-b1a1-e6a6e7893a01",
    "sub": args.oid,
    "oid": args.oid,
    "iat": now,
    "nbf": now,
    "exp": now + args.hours * 3600,
    "roles": [args.role],
    "name": f"Usuario {args.role}",
}


def encode(value: dict) -> bytes:
    raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


unsigned = encode(header) + b"." + encode(payload)
signature = base64.urlsafe_b64encode(
    hmac.new(secret, unsigned, hashlib.sha256).digest()
).rstrip(b"=")
print((unsigned + b"." + signature).decode("ascii"))
