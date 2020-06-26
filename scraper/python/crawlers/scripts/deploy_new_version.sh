#!/usr/bin/env bash

./scripts/build_new_version.sh

VERSION=$(cat FULL_VERSION)

docker tag 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:"${VERSION}" 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:latest
docker push 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:"${VERSION}"