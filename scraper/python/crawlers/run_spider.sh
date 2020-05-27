#!/usr/bin/env bash

date_param="{date}"
time_param="{time}"

DATE=$(date "+%Y-%m-%d")
TS=$(date "+%s")

formatted_name=$2
formatted_name="${formatted_name//$date_param/$DATE}"
formatted_name="${formatted_name//$time_param/$TS}"

echo "Running spider $1 and outputting to $formatted_name"

scrapy crawl "$1" \
  -o "$formatted_name" \
  --set LOG_ENABLED=False \
  --set JSON_LOGGING=True \
  --set LOG_FORMATTER='crawlers.logformatter.LogFormatter'