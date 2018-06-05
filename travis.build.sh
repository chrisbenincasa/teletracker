#!/bin/sh
pushd $TRAVIS_BUILD_DIR/server
yarn global add mocha
yarn install
yarn test
popd

pushd $TRAVIS_BUILD_DIR/frontend
yarn install
yarn test
popd