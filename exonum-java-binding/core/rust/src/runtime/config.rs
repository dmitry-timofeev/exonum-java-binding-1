/*
 * Copyright 2019 The Exonum Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use exonum_cli::command::run::NodeRunConfig;

use std::{fmt, path::PathBuf};

use {absolute_library_path, system_classpath};

/// Full configuration of the EJB runtime and JVM.
pub struct Config {
    /// Node configuration parameters used in Exonum Core.
    pub run_config: NodeRunConfig,
    /// JVM configuration parameters.
    pub jvm_config: JvmConfig,
    /// Java runtime configuration parameters.
    pub runtime_config: RuntimeConfig,
}

/// JVM-specific configuration parameters.
///
/// These parameters are provided by the user on `run` configuration step.
/// These parameters are private and can be unique for every node.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct JvmConfig {
    /// Additional parameters for JVM.
    ///
    /// Passed directly to JVM while initializing EJB runtime.
    /// Parameters must not have dash at the beginning.
    /// Some parameters are forbidden for setting up by user.
    /// Parameters that are prepended to the rest.
    pub args_prepend: Vec<String>,
    /// Parameters that get appended to the rest.
    pub args_append: Vec<String>,
    /// Socket address for JVM debugging.
    pub jvm_debug_socket: Option<String>,
}

/// Runtime-specific configuration parameters.
///
/// These parameters are provided by the user on `run` configuration step.
/// These parameters are private and can be unique for every node.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct RuntimeConfig {
    /// Path to the directory containing service artifacts.
    pub artifacts_path: PathBuf,
    /// Path to `log4j` configuration file.
    pub log_config_path: PathBuf,
    /// A port of the HTTP server for Java services.
    /// Must be distinct from the ports used by Exonum.
    pub port: i32,
    /// Overridden path to native library if specified.
    pub override_system_lib_path: Option<String>,
}

/// Internal EJB configuration.
///
/// Not visible by user, used internally while initializing runtime.
pub struct InternalConfig {
    /// EJB system classpath.
    pub system_class_path: String,
    /// EJB library path.
    pub system_lib_path: String,
}

impl InternalConfig {
    /// Returns InternalConfig with system paths intended for Exonum Java App.
    pub fn app_config() -> Self {
        Self {
            system_class_path: system_classpath(),
            system_lib_path: absolute_library_path(),
        }
    }
}

/// Error returned while validating user-specified additional parameters for JVM.
/// Trying to specify a parameter that is set by EJB internally.
#[derive(PartialEq)]
pub struct ForbiddenParameterError(String);

impl fmt::Debug for ForbiddenParameterError {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(
            f,
            "Trying to specify JVM parameter [{}] that is set by EJB internally. \
             Use EJB parameters instead.",
            self.0
        )
    }
}

/// Checks if parameter is not in list of forbidden parameters and adds a dash at the beginning.
pub(crate) fn validate_and_convert(
    user_parameter: &str,
) -> Result<String, ForbiddenParameterError> {
    check_not_forbidden(user_parameter)?;
    // adding dash at the beginning
    let mut user_parameter = user_parameter.to_owned();
    user_parameter.insert(0, '-');

    Ok(user_parameter)
}

fn check_not_forbidden(user_parameter: &str) -> Result<(), ForbiddenParameterError> {
    if user_parameter.starts_with("Djava.class.path")
        || user_parameter.starts_with("Djava.library.path")
        || user_parameter.starts_with("Dlog4j.configurationFile")
    {
        Err(ForbiddenParameterError(user_parameter.to_string()))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn not_forbidden_debug() {
        let validation_result = validate_and_convert("Xdebug");
        assert_eq!(validation_result, Ok("-Xdebug".to_string()));
    }

    #[test]
    fn not_forbidden_user_parameter() {
        let validation_result = validate_and_convert("Duser.parameter=Djava.library.path");
        assert_eq!(
            validation_result,
            Ok("-Duser.parameter=Djava.library.path".to_string())
        );
    }

    #[test]
    #[should_panic(expected = "Trying to specify JVM parameter")]
    fn library_path() {
        validate_and_convert("Djava.library.path=.").unwrap();
    }

    #[test]
    #[should_panic(expected = "Trying to specify JVM parameter")]
    fn class_path() {
        validate_and_convert("Djava.class.path=target").unwrap();
    }

    #[test]
    #[should_panic(expected = "Trying to specify JVM parameter")]
    fn log_config() {
        validate_and_convert("Dlog4j.configurationFile=logfile").unwrap();
    }
}
