#!/bin/sh
set -eu
out="build/classes"
rm -rf "$out"
mkdir -p "$out"
find src/main/java -name '*.java' -print0 | xargs -0 javac -d "$out"
if [ "${1:-live}" = "test" ]; then
  find src/test/java -name '*.java' -print0 | xargs -0 javac -cp "$out" -d "$out"
  java -cp "$out" example.fieldservice.WorkOrderServiceTest
else
  java -cp "$out" example.fieldservice.FieldServiceExample
fi
