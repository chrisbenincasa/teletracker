#!/bin/bash

set -e

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

cd scala-server

TAG="${TRAVIS_TAG:-SNAPSHOT}"
VERSION=`sbt -Drevision=$TAG --error 'set showSuccess := false' "showVersion"`

echo "Pushing version v${VERSION}"

SCALA_V=${TRAVIS_SCALA_VERSION:-2.12.6}

if [ -z "$SKIP_DOCKER_BUILD" ] then
    sbt ++$SCALA_V -Drevision=${TAG} docker
fi

if [ "$TAG" = "SNAPSHOT" ]; then 
    docker push chrisbenincasa/teletracker:latest
else 
    docker push chrisbenincasa/teletracker:v${VERSION}
fi