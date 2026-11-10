#!/usr/bin/env bash
# Testes de integração da API do dashboard contra a aplicação rodando (com MongoDB).
# Gera evidências em docs/testes/resultado-api.txt
#
# Uso:  ./docs/testes/testar-api.sh [URL_BASE]
#   Variáveis: GERENTE_USER/GERENTE_PASS (padrão gerente/demo123), ATENDENTE_USER/ATENDENTE_PASS (atendente/demo123)
#   Para criar esses usuários automaticamente, suba a aplicação com PEDIUPATRAO_DEMO_DATA=true.
set -u
BASE="${1:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/resultado-api.txt"
TMP="$(mktemp -d)"
OK=0; FALHA=0

login() { # $1 usuario $2 senha $3 arquivo-de-cookies
  curl -s -c "$3" -b "$3" "$BASE/login" -o "$TMP/login.html"
  local csrf
  csrf=$(grep -o 'name="_csrf"[^>]*value="[^"]*"' "$TMP/login.html" | sed 's/.*value="\([^"]*\)"/\1/' | head -1)
  curl -s -c "$3" -b "$3" -o /dev/null -X POST "$BASE/login" \
       --data-urlencode "username=$1" --data-urlencode "password=$2" --data-urlencode "_csrf=$csrf"
}

caso() { # $1 descrição  $2 status esperado  $3 cookies (ou -)  $4 caminho  [$5 método]
  local metodo="${5:-GET}" args=(-s -o "$TMP/corpo.json" -w '%{http_code}' -X "$metodo" -H 'Accept: application/json')
  [ "$3" != "-" ] && args+=(-b "$3")
  local status
  status=$(curl "${args[@]}" "$BASE$4")
  local res="FALHOU"; if [ "$status" = "$2" ]; then res="OK"; OK=$((OK+1)); else FALHA=$((FALHA+1)); fi
  {
    echo "[$res] $1"
    echo "      $metodo $4  ->  HTTP $status (esperado $2)"
    echo "      $(head -c 400 "$TMP/corpo.json" | tr '\n' ' ')"
    echo
  } | tee -a "$OUT"
}

: > "$OUT"
echo "Testes da API do Dashboard — $(date '+%d/%m/%Y %H:%M:%S') — $BASE" | tee -a "$OUT"
echo | tee -a "$OUT"

G="$TMP/gerente.txt"; A="$TMP/atendente.txt"
login "${GERENTE_USER:-gerente}" "${GERENTE_PASS:-demo123}" "$G"
login "${ATENDENTE_USER:-atendente}" "${ATENDENTE_PASS:-demo123}" "$A"
HOJE=$(date +%F)

caso "Sem login retorna 401 em JSON"                      401 -  "/api/dashboard/resumo"
caso "ATENDENTE não acessa o dashboard (403)"             403 "$A" "/api/dashboard/resumo"
caso "GERENTE: resumo padrão (últimos 30 dias)"           200 "$G" "/api/dashboard/resumo"
caso "GERENTE: resumo de hoje"                            200 "$G" "/api/dashboard/resumo?inicio=$HOJE&fim=$HOJE"
caso "Série semanal"                                      200 "$G" "/api/dashboard/vendas/serie?agrupamento=SEMANA"
caso "Série mensal dos últimos 6 meses (auto)"            200 "$G" "/api/dashboard/vendas/serie?inicio=$(date -d '-180 days' +%F 2>/dev/null || date -v-180d +%F)&fim=$HOJE"
caso "Vendas por categoria"                               200 "$G" "/api/dashboard/vendas/categorias"
caso "Horários de pico"                                   200 "$G" "/api/dashboard/vendas/horarios"
caso "Situação dos pedidos (limite de atraso 30 min)"     200 "$G" "/api/dashboard/pedidos/situacao?limiteAtrasoMin=30"
caso "Últimos 5 pedidos"                                  200 "$G" "/api/dashboard/pedidos/recentes?limite=5"
caso "Ranking por faturamento (top 5)"                    200 "$G" "/api/dashboard/produtos/ranking?limite=5&ordenarPor=FATURAMENTO"
caso "Filtro por categoria e status"                      200 "$G" "/api/dashboard/resumo?categoria=Pizza&status=FINALIZADO,RETIRADO"
caso "Opções de filtro"                                   200 "$G" "/api/dashboard/filtros"
caso "Data em formato inválido (400)"                     400 "$G" "/api/dashboard/resumo?inicio=10/11/2026"
caso "Período invertido (400)"                            400 "$G" "/api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01"
caso "Período maior que 366 dias (400)"                   400 "$G" "/api/dashboard/resumo?inicio=2024-01-01&fim=2026-11-10"
caso "Status inexistente (400)"                           400 "$G" "/api/dashboard/pedidos/situacao?status=PERDIDO"
caso "Agrupamento inválido (400)"                         400 "$G" "/api/dashboard/vendas/serie?agrupamento=ANO"
caso "Limite fora da faixa (400)"                         400 "$G" "/api/dashboard/produtos/ranking?limite=100"
caso "Limite não numérico (400)"                          400 "$G" "/api/dashboard/produtos/ranking?limite=abc"
caso "API é somente leitura: POST bloqueado (403)"        403 "$G" "/api/dashboard/resumo" POST

echo "Resumo: $OK OK, $FALHA falha(s)." | tee -a "$OUT"
rm -rf "$TMP"
[ "$FALHA" -eq 0 ]
