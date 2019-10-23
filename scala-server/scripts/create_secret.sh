#!/usr/bin/env bash

set -u

KEY=$1
VALUE=$2
ENC_KEY=${3:-teletracker-qa}

#berglas create teletracker-secrets/"$KEY" "$VALUE" \
#  --key projects/teletracker/locations/global/keyRings/berglas/cryptoKeys/berglas-key
#
#berglas grant teletracker-secrets/"$KEY" --member user:chrisbenincasa@gmail.com

TMP=$(mktemp)

aws kms encrypt --key-id alias/${ENC_KEY} --plaintext ${VALUE} --output text --query CiphertextBlob | base64 --decode > ${TMP}
aws s3 cp ${TMP} s3://teletracker-secrets/${KEY}