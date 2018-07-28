#!/bin/sh
#cd $TRAVIS_BUILD_DIR/server
#yarn global add mocha
#yarn install
#yarn test

#retVal=$?
#if [ $retVal -ne 0 ]; then
#    exit 1;
#fi

#cd ..

cd $TRAVIS_BUILD_DIR/frontend
yarn install
yarn test

retVal=$?
if [ $retVal -ne 0 ]; then
    exit 1;
fi

cd ..
