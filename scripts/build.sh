#!/bin/sh
set -eu

mkdir -p out
find src -name "*.java" > sources.txt
javac -d out @sources.txt
rm sources.txt
