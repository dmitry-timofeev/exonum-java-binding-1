// Copyright 2019 The Exonum Team
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::{collections::HashSet, fs::File, io::Read, path::Path};

use std::collections::HashMap;
use toml;

pub const PATH_TO_SERVICES_DEFINITION: &str = "ejb_app_services.toml";
pub const CONFIGURATION_SERVICE: &str = "configuration";
pub const BTC_ANCHORING_SERVICE: &str = "btc-anchoring";
pub const TIME_SERVICE: &str = "time";

#[derive(Serialize, Deserialize)]
pub struct EjbAppServices {
    pub enabled_services: Option<HashSet<String>>,
    pub user_services: HashMap<String, String>,
}

type Result<T> = std::result::Result<T, Box<std::error::Error>>;

/// Loads service names from a specific TOML configuration file
pub fn load_services_definition<P: AsRef<Path>>(path: P) -> Result<EjbAppServices> /*Result<(Option<HashSet<String>>, HashMap<String, String>)>*/
{
    let mut file = File::open(path)?;
    let mut toml = String::new();
    file.read_to_string(&mut toml)?;
    let services: EjbAppServices =
        toml::from_str(&toml).expect("Invalid format of the file with EJB services definition");
    Ok(services)
}

/// Determines whether particular service name is defined in the specific TOML configuration file.
pub fn is_service_enabled_in_config_file<P: AsRef<Path>>(service_name: &str, path: P) -> bool {
    match load_services_definition(path) {
        Ok(services) => match services.enabled_services {
            Some(services_to_enable) => services_to_enable.contains(service_name),
            None => false,
        },
        Err(_) => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::{Builder, TempPath};

    #[test]
    fn no_config() {
        let res = load_services_definition("nonexistent");
        assert!(res.is_err());
    }

    #[test]
    fn missed_enabled_services_section() {
        let cfg = create_config(
            "missed_enabled_services_test.toml",
            "[user_services]\nservice_name1 = '/path/to/artifact1'\nservice_name2 = '/path/to/artifact2'",
        );

        let res = load_services_definition(cfg);
        assert!(res.is_ok());
    }

    #[test]
    #[should_panic(expected = "Invalid format of the file with EJB services definition")]
    fn missed_user_services_section() {
        let cfg = create_config(
            "missed_user_services_test.toml",
            "enabled_services = [\"configuration\", \"btc-anchoring\", \"time\"]",
        );

        let _result = load_services_definition(cfg);
    }

    #[test]
    fn all_services() {
        let cfg = create_config(
            "all_services_test.toml",
            "enabled_services = [\"configuration\", \"btc-anchoring\", \"time\"]\n\
            [user_services]\nservice_name1 = '/path/to/artifact1'\nservice_name2 = '/path/to/artifact2'",
        );

        let res = load_services_definition(cfg);
        assert!(res.is_ok());

        let services = res.unwrap();
        let services_to_enable = services.enabled_services.unwrap();
        let user_services = services.user_services;

        assert_eq!(services_to_enable.len(), 3);
        assert!(services_to_enable.contains(CONFIGURATION_SERVICE));
        assert!(services_to_enable.contains(BTC_ANCHORING_SERVICE));
        assert!(services_to_enable.contains(TIME_SERVICE));

        assert_eq!(user_services.len(), 2);
        assert_eq!(
            user_services.get("service_name1").unwrap(),
            "/path/to/artifact1"
        );
        assert_eq!(
            user_services.get("service_name2").unwrap(),
            "/path/to/artifact2"
        );
    }

    #[test]
    fn empty_list() {
        let cfg = create_config("empty_list.toml", "enabled_services = []\n[user_services]");
        let res = load_services_definition(cfg);
        assert!(res.is_ok());
        let services = res.unwrap();
        assert_eq!(services.enabled_services.unwrap().len(), 0);
        assert_eq!(services.user_services.len(), 0);
    }

    #[test]
    fn check_service_enabled() {
        let cfg = create_config(
            "service_enabled_test.toml",
            "enabled_services = [\"time\"]\n[user_services]\nservice_name1 = '/path/to/artifact1.jar'",
        );

        assert!(!is_service_enabled_in_config_file(
            CONFIGURATION_SERVICE,
            &cfg
        ));
        assert!(!is_service_enabled_in_config_file(
            BTC_ANCHORING_SERVICE,
            &cfg
        ));
        assert!(is_service_enabled_in_config_file(TIME_SERVICE, &cfg));
    }

    fn create_config(filename: &str, cfg: &str) -> TempPath {
        let mut cfg_file = Builder::new().prefix(filename).tempfile().unwrap();
        writeln!(cfg_file, "{}", cfg).unwrap();
        cfg_file.into_temp_path()
    }
}
