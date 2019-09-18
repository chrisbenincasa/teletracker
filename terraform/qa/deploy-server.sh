#!/usr/bin/env bash

set -e

VERSION=$1

./replace_version $VERSION

terraform apply -var-file=current.json