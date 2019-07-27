#!/usr/bin/env bash

set -u

KEY=$1
VALUE=$2

berglas create teletracker-secrets/"$KEY" "$VALUE" \
  --key projects/teletracker/locations/global/keyRings/berglas/cryptoKeys/berglas-key

berglas grant teletracker-secrets/"$KEY" --member user:chrisbenincasa@gmail.com