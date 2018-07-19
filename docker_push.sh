#!/bin/bash

set -e

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
cd scala-server
TAG="${TRAVIS_TAG:-SNAPSHOT}"
VERSION=`sbt -Drevision=$TAG --error 'set showSuccess := false' "showVersion"`
sbt ++$TRAVIS_SCALA_VERSION -Drevision=${TAG} docker
if [ "$TAG" = "SNAPSHOT"] 
then
    docker push chrisbenincasa/teletracker:latest
else
    docker push chrisbenincasa/teletracker:${TAG}
fi