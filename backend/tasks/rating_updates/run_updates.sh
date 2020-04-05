#!/usr/bin/env bash

set -uex

for i in $(seq 0 41);
    do curl -XPOST -s -H "Content-Type: application/x-ndjson" https://search-teletracker-qa-igmliq26kf3wlgxlrihy2bqfuu.us-west-2.es.amazonaws.com/_bulk --data-binary "@rating_updates.$(printf '%03d' $i).txt" && sleep 120;
done;
