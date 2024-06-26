#!/bin/bash

ENV=${ENV:-qa}
TAG=${TAG:-latest}

docker run -it \
  --rm \
  --env-file=.env \
  -p 3001:3001 \
  302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker/server:"${TAG}"
#  --env=GOOGLE_APPLICATION_CREDENTIALS="/gcp-keys/teletracker-${ENV}.json" \
#  --env=DB_PASSWORD="berglas://teletracker-secrets/db-password-${ENV}" \
#  --env=JWT_SECRET="berglas://teletracker-secrets/jwt-secret-key-${ENV}" \
#  --env=TMDB_API_KEY="berglas://teletracker-secrets/tmdb-api-key-${ENV}" \
#  --env=ADMINISTRATOR_KEY="berglas://teletracker-secrets/administrator-key-${ENV}" \
#  -v $(pwd)/server/gcp-keys:/gcp-keys \
