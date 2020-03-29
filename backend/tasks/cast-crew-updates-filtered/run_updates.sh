#!/usr/bin/env bash

set -uex

for i in $(seq 6 15);
    do curl -XPOST -s -H "Content-Type: application/x-ndjson" https://search-teletracker-qa-igmliq26kf3wlgxlrihy2bqfuu.us-west-2.es.amazonaws.com/_bulk --data-binary "@cast-crew-updates-filtered.$(printf '%03d' $i).txt" && sleep 120;
done;
