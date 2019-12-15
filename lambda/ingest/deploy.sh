#!/usr/bin/env bash

NOW=$(date +%s)
STAMP=${1:-$NOW}

./build.sh

aws s3 cp out/package.zip s3://teletracker-artifacts/ingest-lambda/package.zip

echo "Pushed new version: ${STAMP}"