#!/usr/bin/env bash
# Mede a API com o k6 num ambiente descartável parecido com o Cloud Run: a imagem do backend/Dockerfile
# num contêiner com 1 CPU, 512 MiB e pool de 5 conexões, contra um PostgreSQL 15. Tudo roda no Docker e
# é removido no fim; só a pasta de resultados fica. Uso e opções em tests/load/README.md.
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash no Windows: não converter caminhos de dentro dos contêineres.

SCRIPT=eventos-feed
PERFIL=academic
BASE=demonstracao
ROTEIRO=site
VERSAO=HEAD
SAIDA=""
PORTA=18095
K6_IMAGEM=grafana/k6:2.2.0
while [ $# -gt 0 ]; do
  case "$1" in
    --script) SCRIPT="$2"; shift 2 ;;
    --perfil) PERFIL="$2"; shift 2 ;;
    --base) BASE="$2"; shift 2 ;;
    --roteiro) ROTEIRO="$2"; shift 2 ;;
    --versao) VERSAO="$2"; shift 2 ;;
    --saida) SAIDA="$2"; shift 2 ;;
    --porta) PORTA="$2"; shift 2 ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
done
case "$SCRIPT" in eventos-feed|comentarios-write) ;; *) echo "--script: eventos-feed ou comentarios-write" >&2; exit 2 ;; esac
case "$BASE" in demonstracao|maior) ;; *) echo "--base: demonstracao ou maior" >&2; exit 2 ;; esac

RAIZ=$(git rev-parse --show-toplevel)
cd "$RAIZ"
SHA=$(git rev-parse --short=7 "$VERSAO^{commit}")
SAIDA=${SAIDA:-tests/load/resultados/$(date +%Y%m%d-%H%M%S)-$SCRIPT-$ROTEIRO-$BASE-$SHA}
mkdir -p "$SAIDA"
SAIDA=$(cd "$SAIDA" && pwd)
# Caminho do host para o Docker: no Git Bash, E:/... em vez de /e/...
caminho_host() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }

ID="chegaai-medicao-$$"
REDE="$ID"
IMAGEM_API="chegaai-medicao-api:$SHA"
limpar() {
  [ -n "${AMOSTRADOR:-}" ] && kill "$AMOSTRADOR" 2>/dev/null || true
  docker logs "$ID-api" > "$SAIDA/api.log" 2>&1 || true
  docker rm -f "$ID-api" "$ID-db" > /dev/null 2>&1 || true
  docker network rm "$REDE" > /dev/null 2>&1 || true
}
trap limpar EXIT

echo "Imagem da API em $SHA..."
if ! docker image inspect "$IMAGEM_API" > /dev/null 2>&1; then
  git archive "$SHA:backend" | docker build -q -t "$IMAGEM_API" - > /dev/null
fi
docker network create "$REDE" > /dev/null
docker run -d --name "$ID-db" --network "$REDE" --network-alias db \
  -e POSTGRES_DB=chegaai -e POSTGRES_USER=chegaai -e POSTGRES_PASSWORD=chegaai postgres:15-alpine > /dev/null
until docker exec "$ID-db" pg_isready -U chegaai -d chegaai > /dev/null 2>&1; do sleep 1; done

# Como no Cloud Run: 1 CPU, 512 MiB e pool de 5 (mínimo 1). Os limites de escrita sobem só aqui.
LIMITES=""
for nome in LOGIN_IP LOGIN_PAR CADASTRO_IP CRIAR_EVENTO CRIAR_EVENTO_DIA COMENTAR COMENTAR_HORA; do
  LIMITES="$LIMITES -e CHEGAAI_LIMITE_$nome=1000000"
done
# shellcheck disable=SC2086
docker run -d --name "$ID-api" --network "$REDE" --network-alias api --cpus=1 --memory=512m \
  -p "127.0.0.1:$PORTA:8080" -e PORT=8080 \
  -e CHEGAAI_DB_URL=jdbc:postgresql://db:5432/chegaai -e CHEGAAI_DB_USER=chegaai -e CHEGAAI_DB_PASSWORD=chegaai \
  -e CHEGAAI_DB_POOL_MAX=5 -e CHEGAAI_DB_POOL_MIN=1 \
  -e CHEGAAI_JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')" $LIMITES \
  "$IMAGEM_API" > /dev/null
echo "Esperando a API (com 1 CPU, a inicialização leva cerca de um minuto)..."
for i in $(seq 1 120); do
  curl -sf "http://127.0.0.1:$PORTA/api/actuator/health" > /dev/null && break
  [ "$i" = 120 ] && { echo "A API não ficou pronta; veja $SAIDA/api.log" >&2; exit 1; }
  sleep 2
done
if [ "$BASE" = maior ]; then
  docker exec -i "$ID-db" psql -q -v ON_ERROR_STOP=1 -U chegaai -d chegaai < tests/load/base-maior.sql
fi
CONTAGENS=$(docker exec "$ID-db" psql -tA -U chegaai -d chegaai \
  -c "select (select count(*) from evento) || ' ' || (select count(*) from inscricao) || ' ' || (select count(*) from comentario)")

# Aquecimento curto, fora da medição: a primeira chamada de cada rota carrega classes e consultas.
for _ in 1 2 3 4 5; do
  curl -s -o /dev/null "http://127.0.0.1:$PORTA/api/eventos/resumo" || true
  curl -s -o /dev/null "http://127.0.0.1:$PORTA/api/eventos/feed" || true
done

# Memória do contêiner da API a cada segundo, durante o k6.
( while true; do docker stats --no-stream --format '{{.MemUsage}}' "$ID-api" 2>/dev/null | cut -d/ -f1; sleep 1; done ) \
  > "$SAIDA/memoria.txt" &
AMOSTRADOR=$!

echo "Rodando $SCRIPT ($PERFIL, roteiro $ROTEIRO, base $BASE)..."
set +e
docker run --rm --network "$REDE" \
  -v "$(caminho_host tests/load):/scripts:ro" -v "$(caminho_host "$SAIDA"):/saida" \
  -e BASE_URL=http://api:8080/api -e LOAD_PROFILE="$PERFIL" -e ROTEIRO="$ROTEIRO" -e REPORT_DIR=/saida \
  "$K6_IMAGEM" run --quiet --out json=/saida/serie.jsonl "/scripts/$SCRIPT.js"
K6_SAIDA=$?
set -e
kill "$AMOSTRADOR" 2>/dev/null || true
AMOSTRADOR=""
OOM=$(docker inspect -f '{{.State.OOMKilled}}' "$ID-api")

K6_VERSAO=$(docker run --rm "$K6_IMAGEM" version | head -1)
DOCKER_INFO=$(docker info --format '{{.NCPU}} {{.MemTotal}} {{.OperatingSystem}}')
CPU_HOST=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Processor).Name" 2>/dev/null | tr -d '\r' || true)
[ -n "$CPU_HOST" ] || CPU_HOST=$(grep -m1 'model name' /proc/cpuinfo 2>/dev/null | cut -d: -f2- || echo "desconhecida")
CPU_HOST=$(echo "$CPU_HOST" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
DATA="$(date -u +%Y-%m-%dT%H:%M:%SZ)" SHA="$SHA" SCRIPTS_SHA="$(git rev-parse --short=7 HEAD)" SCRIPT="$SCRIPT" PERFIL="$PERFIL" \
BASE="$BASE" ROTEIRO="$ROTEIRO" CONTAGENS="$CONTAGENS" K6_VERSAO="$K6_VERSAO" K6_SAIDA="$K6_SAIDA" OOM="$OOM" \
DOCKER_INFO="$DOCKER_INFO" CPU_HOST="$CPU_HOST" SAIDA="$(caminho_host "$SAIDA")" node -e '
const fs = require("fs");
const e = process.env;
const [eventos, inscricoes, comentarios] = e.CONTAGENS.trim().split(" ").map(Number);
const [cpus, bytes, ...sistema] = e.DOCKER_INFO.trim().split(" ");
const docker = `${cpus} CPUs e ${(Number(bytes) / 1024 ** 3).toFixed(1).replace(".", ",")} GiB, ${sistema.join(" ")}`;
const memoria = fs.readFileSync(`${e.SAIDA}/memoria.txt`, "utf8").split(/\r?\n/).map((linha) => {
  const m = linha.trim().match(/^([\d.]+)\s*(KiB|MiB|GiB)$/);
  return m ? Number(m[1]) * { KiB: 1 / 1024, MiB: 1, GiB: 1024 }[m[2]] : null;
}).filter((valor) => valor !== null);
const metadados = {
  data: e.DATA, versaoApi: e.SHA, versaoScripts: e.SCRIPTS_SHA, script: e.SCRIPT, perfil: e.PERFIL,
  roteiro: e.ROTEIRO, base: { nome: e.BASE, eventos, inscricoes, comentarios },
  ambiente: { api: "backend/Dockerfile, 1 CPU, 512 MiB, pool de 5 (mínimo 1)", banco: "postgres:15-alpine",
    k6: e.K6_VERSAO, docker, cpuDoHost: e.CPU_HOST },
  memoriaMiB: { amostras: memoria.length, pico: memoria.length ? Math.max(...memoria) : null, oom: e.OOM === "true" },
  k6CodigoDeSaida: Number(e.K6_SAIDA),
};
fs.writeFileSync(`${e.SAIDA}/metadados.json`, JSON.stringify(metadados, null, 2) + "\n");
'
echo "Resultado em $SAIDA (k6 saiu com $K6_SAIDA; OOM: $OOM)."
[ "$OOM" = false ]
