#!/usr/bin/env bash

NOW=$(date +%s)
STAMP=${1:-$NOW}

./build.sh || exit

aws s3 cp build/crawl_trigger.zip s3://us-west-2-teletracker-artifacts/lambdas/crawl_trigger.zip

echo "Pushed new version: ${STAMP}"