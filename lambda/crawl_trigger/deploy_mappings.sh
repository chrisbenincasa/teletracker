#!/usr/bin/env bash

echo "Pushing configuration"

jq '.' mappings.json >&1

aws s3 cp mappings.json s3://teletracker-config/crawl_triggers/mappings.json