#!/bin/sh
cd $TRAVIS_BUILD_DIR/server
yarn global add mocha
yarn install
yarn test
cd ..

cd $TRAVIS_BUILD_DIR/frontend
yarn install
yarn test
cd ..