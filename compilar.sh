#!/usr/bin/env bash
# Compila o projeto para a pasta out/ (requer JDK 17 ou superior).
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main -name '*.java')
echo "Compilado em out/"
