#!/usr/bin/env bash

echo "$(cat VERSION)-$(date +%s).${SHORT_SHA:-$(git rev-parse --short HEAD)}" > FULL_VERSION && echo "Deploying version $(cat FULL_VERSION)"