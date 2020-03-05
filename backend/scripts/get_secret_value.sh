#!/usr/bin/env bash

set -u

KEY=$1

TMP=$(mktemp)

aws s3 cp --quiet s3://teletracker-secrets/${KEY} ${TMP}
aws kms decrypt --ciphertext-blob fileb://${TMP} --output text --query Plaintext | base64 --decode