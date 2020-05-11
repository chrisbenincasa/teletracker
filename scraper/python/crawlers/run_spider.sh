#!/usr/bin/env bash

DATE=$(date "+%Y-%m-%d")
scrapy crawl "$1" \
  -o "s3://teletracker-data-us-west-2/scrape-results/$2/$DATE/$3" \
  --set LOG_ENABLED=False \
  --set LOG_FORMATTER='crawlers.logformatter.LogFormatter'