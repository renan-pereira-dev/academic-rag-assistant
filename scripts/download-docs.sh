#!/usr/bin/env bash
# Baixa os PDFs públicos listados em scripts/docs-sources.txt para docs/.
#
# Uso: bash scripts/download-docs.sh [--force]
#   --force  baixa de novo mesmo se o arquivo já existir
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCES_FILE="${DOCS_SOURCES_FILE:-$ROOT_DIR/scripts/docs-sources.txt}"
DOCS_DIR="${DOCS_DIR:-$ROOT_DIR/docs}"
FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *) echo "Argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

command -v curl >/dev/null 2>&1 || { echo "Erro: curl não encontrado." >&2; exit 1; }
[[ -f "$SOURCES_FILE" ]] || { echo "Erro: arquivo de fontes não encontrado: $SOURCES_FILE" >&2; exit 1; }

mkdir -p "$DOCS_DIR"
count=0

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue

  name="${line%%|*}"
  url="${line#*|}"
  if [[ "$name" == "$line" || -z "$name" || -z "$url" || "$name" != *.pdf || "$name" == */* ]]; then
    echo "Erro: linha inválida (esperado <arquivo.pdf>|<url>): $line" >&2
    exit 1
  fi

  target="$DOCS_DIR/$name"
  count=$((count + 1))

  if [[ -f "$target" && "$FORCE" == false ]]; then
    echo "Já existe, pulando: $name (use --force para baixar de novo)"
    continue
  fi

  echo "Baixando $name ..."
  tmp="$target.part"
  if ! curl -fSL --retry 3 --connect-timeout 20 -o "$tmp" "$url"; then
    rm -f "$tmp"
    echo "Erro: falha ao baixar $url" >&2
    exit 1
  fi
  if [[ "$(head -c 4 "$tmp")" != "%PDF" ]]; then
    rm -f "$tmp"
    echo "Erro: o conteúdo baixado de $url não é um PDF." >&2
    exit 1
  fi
  mv "$tmp" "$target"
  echo "OK: $target"
done < "$SOURCES_FILE"

if [[ "$count" -eq 0 ]]; then
  echo "Nenhuma fonte configurada. Preencha $SOURCES_FILE com <arquivo.pdf>|<url>." >&2
  exit 1
fi
