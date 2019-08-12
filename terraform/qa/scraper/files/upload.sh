#!/usr/bin/env bash

# Exit if any of the intermediate steps fail
set -e

eval "$(jq -r '@sh "INPUT=\(.input) OUTPUT=\(.output) BUCKET=\(.bucket)"')"

gsutil cp $INPUT gs://$BUCKET/$OUTPUT

jq -n --arg output "$OUTPUT" '{"output":$output}'