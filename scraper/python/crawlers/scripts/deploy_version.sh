#!/usr/bin/env bash

VERSION="$1"

docker tag 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:"${VERSION}" 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:latest
docker push 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:"${VERSION}"