#!/usr/bin/env bash
# Compila o projeto + os testes e executa a bateria de testes.
set -e
cd "$(dirname "$0")"
./compilar.sh >/dev/null
mkdir -p out-test
javac -encoding UTF-8 -cp out -d out-test $(find src/test -name '*.java')
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out:out-test automato.TestesAutomato
