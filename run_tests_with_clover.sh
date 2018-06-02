#!/usr/bin/env bash
# Runs all tests with OpenClover instrumentation:
# http://openclover.org/doc/manual/4.2.0/maven--quick-start-guide.html
#
# This script duplicates some of the operations
# of `run_all_tests.sh` because it needs to compile the instrumented sources
# first, then run native ITs, and only then collect the statistics.

# Fail immediately in case of any errors and/or unset variables
set -eu -o pipefail

# todo: skip ErrorProne checks?
mvn -Dcheckstyle.skip=true \
  clean \
  clover:setup \
  install

# Run native integration tests that require a JVM.
./run_native_integration_tests.sh --skip-compile

# Generate a coverage report
mvn clover:aggregate clover:clover
