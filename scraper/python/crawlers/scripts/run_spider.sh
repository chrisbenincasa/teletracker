#!/usr/bin/env bash

set -x

date_param="{date}"
time_param="{time}"

DATE=$(date "+%Y-%m-%d")
TS=$(date "+%s")
SPIDER_NAME=$1

shift

OUTPUTS_STR=()
REST=()

for output in "$@"; do
  if [[ $output == -s* ]];
  then
    REST+=($output )
  else
    formatted_name="${output//$date_param/$DATE}"
    OUTPUTS_STR+=(-o "${formatted_name//$time_param/$TS}")
  fi
done

DYNAMO_CMD=()
if [[ -n "${DYNAMO_DB_OUTPUT_TABLE}" ]]; then
  DYNAMO_CMD+=(--set 'DYNAMO_CRAWL_TRACK_ENABLED=True' --set "DYNAMO_CRAWL_TRACK_TABLE=$DYNAMO_DB_OUTPUT_TABLE")
fi

exec scrapy crawl "$SPIDER_NAME" \
  "${OUTPUTS_STR[@]}" \
  --set LOG_ENABLED=False \
  --set JSON_LOGGING=True \
  --set LOG_FORMATTER='crawlers.logformatter.LogFormatter' \
  --set LOG_LEVEL=INFO \
  "${DYNAMO_CMD[@]}" \
  "${REST[@]}"
