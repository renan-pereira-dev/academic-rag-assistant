#!/usr/bin/env bash
# Roda o golden set contra POST /api/chat e grava o relatório em eval/results/.
#
# Uso: bash scripts/run-eval.sh [--label <configuração>]
#   Ex.: bash scripts/run-eval.sh --label "chunk800-limiar0.75"
#
# Variáveis opcionais:
#   API_URL         URL base da API           (padrão: http://localhost:8080)
#   GOLDEN_SET      caminho do golden set     (padrão: eval/golden-set.json)
#   RESULTS_DIR     pasta dos relatórios      (padrão: eval/results)
#   EVAL_DELAY_MS   pausa entre perguntas, útil para a cota gratuita do Gemini (padrão: 0)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

command -v node >/dev/null 2>&1 || { echo "Erro: Node.js 18+ é necessário." >&2; exit 1; }

exec node "$ROOT_DIR/scripts/eval/run-eval.mjs" \
  --api-url "${API_URL:-http://localhost:8080}" \
  --golden-set "${GOLDEN_SET:-$ROOT_DIR/eval/golden-set.json}" \
  --results-dir "${RESULTS_DIR:-$ROOT_DIR/eval/results}" \
  --delay-ms "${EVAL_DELAY_MS:-0}" \
  "$@"
