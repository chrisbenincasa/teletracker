#!/usr/bin/env bash

NOW=$(date +%s)
STAMP=${1:-$NOW}

./build.sh || exit

aws s3 cp build/tmdb_pull.zip s3://us-west-2-teletracker-artifacts/lambdas/tmdb_pull.zip

echo "Pushed new version: ${STAMP}"