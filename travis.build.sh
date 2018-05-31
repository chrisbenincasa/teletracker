#!/bin/sh
cd $TRAVIS_BUILD_DIR/server
yarn install -g mocha
yarn install
yarn test
