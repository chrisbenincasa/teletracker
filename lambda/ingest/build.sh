#!/usr/bin/env bash

echo "Cleaning staging area"

OUTDIR=out

if [ -d "$OUTDIR/" ]; then rm -rf "${OUTDIR:?}/"; fi

mkdir $OUTDIR

rsync -avq src/* package.json yarn.lock $OUTDIR \
  --exclude=$OUTDIR/ \
  --exclude=node_modules/ \
  --exclude=.idea \
  --exclude=.vscode \
  --exclude=*.zip

pushd $OUTDIR || exit
yarn --prod

zip -q package.zip index.js main.js package.json yarn.lock
zip -urq package.zip node_modules -x "*.DS_Store"

popd || exit