#!/usr/bin/env bash

set -ex

ENV=${1:-qa}

gcloud iam service-accounts keys create server/gcp-keys/teletracker-"${ENV}".json --iam-account 558300338939-compute@developer.gserviceaccount.com