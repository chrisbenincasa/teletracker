#!/usr/bin/env bash

NOW=$(date +%s)
STAMP=${1:-$NOW}

./build.sh || exit

aws s3 cp build/redis_crawler_scaling.zip s3://us-west-2-teletracker-artifacts/lambdas/redis_crawler_scaling.zip

echo "Pushed new version: ${STAMP}"