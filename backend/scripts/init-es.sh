#!/usr/bin/env bash

set -uex

HOST=${1:-search-teletracker-qa-igmliq26kf3wlgxlrihy2bqfuu.us-west-2.es.amazonaws.com}

item_mapping=$(jq -r '.items' ../tasks/src/main/resources/elasticsearch/migration/item_mapping.json)
people_mapping=$(jq -r '.people' ../tasks/src/main/resources/elasticsearch/migration/people_mapping.json)
user_item_mapping=$(jq -r '.user_item' ../tasks/src/main/resources/elasticsearch/migration/user_item_mapping.json)

curl -X PUT "https://$HOST/items" -H 'Content-Type: application/json' -d"$item_mapping}" | jq
curl -X PUT "https://$HOST/people" -H 'Content-Type: application/json' -d"$people_mapping}" | jq
curl -X PUT "https://$HOST/user_items" -H 'Content-Type: application/json' -d"$user_item_mapping}" | jq