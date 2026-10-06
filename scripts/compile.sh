#!/bin/sh
set -e
mkdir -p out
javac -encoding UTF-8 -d out src/p2p/*.java
echo "Compilation successful."
