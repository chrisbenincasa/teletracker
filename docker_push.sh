#!/bin/bash

set -e

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
cd scala-server
travis_retry sbt ++$TRAVIS_SCALA_VERSION docker
docker push chrisbenincasa/teletracker:latest