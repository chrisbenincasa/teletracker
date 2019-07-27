#!/bin/bash

ENV=${ENV:-qa}
TAG=${TAG:-latest}

docker run -it \
  --rm \
  --env-file=.env \
  --env=GOOGLE_APPLICATION_CREDENTIALS="/gcp-keys/teletracker-${ENV}.json" \
  --env=DB_PASSWORD="berglas://teletracker-secrets/db-password-${ENV}" \
  -v $(pwd)/server/gcp-keys:/gcp-keys \
  gcr.io/teletracker/server:"${TAG}"