#!/usr/bin/env bash
# Kompilasi dan jalankan demo Tixcorner DSS (butuh JDK 17+).
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out com.tixcorner.dss.Main data
