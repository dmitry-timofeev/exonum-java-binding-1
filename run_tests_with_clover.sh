#!/usr/bin/env bash

# todo: document better
mvn -Dcheckstyle.skip=true clean clover:setup verify

./run_native_integration_tests.sh --skip-compile

mvn clover:aggregate clover:clover
