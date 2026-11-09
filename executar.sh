#!/usr/bin/env bash
# Atalho: ./executar.sh <automato> [opções] [palavras...]
cd "$(dirname "$0")"
[ -d out ] || ./compilar.sh >/dev/null
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out automato.cli.Main "$@"
