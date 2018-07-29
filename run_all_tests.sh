#!/usr/bin/env bash
# Runs all tests.
#
# A JVM will be selected by JAVA_HOME environment variable, or, if it is not set,
# inferred from the java executable available on the path.

# Fail immediately in case of any errors and/or unset variables
set -eu -o pipefail

# Run unit and integration tests in ci-build profile. This profile includes:
#  - Java unit & integration tests, including ci-only & slow non-critical tests,
#    which are excluded in the default profile.
#  - Checkstyle checks as errors.
#  - Native unit & integration tests that do not require a JVM.
# See build definitions of the modules for more.
# TODO: stable does not work well until ECR-1839 is resolved
mvn install \
  --activate-profiles ci-build \
  -Drust.compiler.version="${RUST_VERSION:-1.26.2}"

# Run native integration tests that require a JVM.
./run_native_integration_tests.sh --skip-compile
./run_ejb_app_tests.sh
