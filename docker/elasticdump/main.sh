#!/usr/bin/env bash

TODAY=$(date "+%Y-%m-%d")

elasticdump \
  --output="s3://teletracker-data-us-west-2/elasticsearch/items/${TODAY}/${ES_INDEX}.json" \
  --input="${ES_HOST}/${ES_INDEX}" \
  --fileSize=10mb \
  --limit=1000 \
  --size=10 \
  --awsChain=true