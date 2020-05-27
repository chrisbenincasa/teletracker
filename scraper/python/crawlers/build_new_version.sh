#!/usr/bin/env bash

./gen_version.sh

VERSION=$(cat FULL_VERSION)

docker build -t 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/crawlers:"${VERSION}" .