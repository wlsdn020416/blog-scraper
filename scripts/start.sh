#!/bin/sh
set -eu

if [ ! -d out ]; then
  ./scripts/build.sh
fi

java -cp out oop.blog.presentation.BlogApiServer
