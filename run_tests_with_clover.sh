#!/usr/bin/env bash
# Runs all tests with OpenClover instrumentation:
# http://openclover.org/doc/manual/4.2.0/maven--quick-start-guide.html
#
# This script duplicates some of the operations
# of `run_all_tests.sh` because it needs to compile the instrumented sources
# first, then run native ITs, and only then collect the statistics.

# Fail immediately in case of any errors and/or unset variables
set -eu -o pipefail

# Import necessary environment variables (see the tests_profile header comment for details).
source tests_profile

# todo: skip ErrorProne checks?
mvn -Dcheckstyle.skip=true \
  -Dspotbugs.skip=true \
  -Drust.compiler.version="${RUST_COMPILER_VERSION:-1.26.2}" \
  clean \
  clover:setup \
  install

# Run native integration tests that require a JVM and module
# with fake service implementation.
./run_native_integration_tests.sh --skip-compile
./run_ejb_app_tests.sh

# Generate a coverage report
mvn clover:aggregate clover:clover
