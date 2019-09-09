#!/usr/bin/env bash

set -e

VERSION=$1

TEMP=$(mktemp)
jq ".image = \"gcr.io/teletracker/server:$VERSION\"" current.json > $TEMP && mv $TEMP current.json
terraform apply -var-file=current.json