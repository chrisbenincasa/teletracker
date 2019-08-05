#!/bin/bash

cp ../scala-server/build.sbt build.sbt
rsync -av --progress ../scala-server/project . --exclude=target/ --exclude=.bloop

gcloud builds submit . --config=sbt-cloudbuild.yaml

rm build.sbt
rm -rf project/