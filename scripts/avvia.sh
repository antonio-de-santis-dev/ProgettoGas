#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo 'Installa Docker con Docker Compose prima di avviare.' >&2; exit 1; }
python3 - <<'PY'
from pathlib import Path
import secrets,os,re
p=Path('.env');text=p.read_text() if p.exists() else Path('.env.example').read_text()
match=re.search(r'^DB_PASSWORD=(.*)$',text,re.M)
if not match or not match.group(1).strip():
    value='DB_PASSWORD='+secrets.token_hex(24)
    text=re.sub(r'^DB_PASSWORD=.*$',value,text,flags=re.M) if match else text+'\n'+value+'\n'
    p.write_text(text);os.chmod(p,0o600)
PY
docker compose up --build -d --wait
echo 'Progetto Gas pronto. Porta predefinita: http://localhost:8090 (GAS_PORT nel file .env).'
