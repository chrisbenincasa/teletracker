#!/usr/bin/env bash

NOW=$(date +%s)
STAMP=${1:-$NOW}

./build.sh || exit

aws s3 cp build/imdb_crawl.zip s3://us-west-2-teletracker-artifacts/lambdas/imdb_crawl.zip

echo "Pushed new version: ${STAMP}"