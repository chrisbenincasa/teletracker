#!/usr/bin/env bash

echo "Cleaning staging area"

if [ -d "build/" ]; then rm -rf "build/"; fi

mkdir build

yarn run build || exit

cp package.json yarn.lock build

pushd build || exit
yarn --prod

zip -qr tmdb_pull.zip .