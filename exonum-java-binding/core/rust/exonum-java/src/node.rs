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

use std::sync::Arc;

use java_bindings::{Command, Config, EjbCommand, EjbCommandResult};
use java_bindings::exonum::blockchain::{Blockchain, BlockchainBuilder, InstanceCollection};
use java_bindings::exonum::exonum_merkledb::{Database, RocksDB};
use java_bindings::exonum::node::{ApiSender, Node, NodeChannel};
use java_bindings::exonum::runtime::rust::ServiceFactory;

pub fn run_node(command: Command) -> Result<(), failure::Error> {
    if let EjbCommandResult::EjbRun(config) = command.execute()? {
        let node = create_node(config)?;
        node.run()
    } else {
        Ok(())
    }
}

fn create_node(config: Config) -> Result<Node, failure::Error> {
    /*
    Review: That's not readable. Please extract in a variable (events_pool_capacity?). Also,
    if you move `node_config` definition above, you can reuse it here to get a shorter chain.
    */
    let channel = NodeChannel::new(&config.run_config.node_config.mempool.events_pool_capacity);
    let blockchain = create_blockchain(&config, &channel)?;

    let node_config = config.run_config.node_config;
    let node_config_path = config
        .run_config
        .node_config_path
        .to_str()
        .expect("Cannot convert node_config_path to String")
        .to_owned();

    Ok(Node::with_blockchain(
        blockchain,
        channel,
        /*
        Review: That's funny that it accepts the whole thing yet requires node_config_path
        to be passed separately.
        */
        node_config,
        Some(node_config_path),
    ))
}

fn create_blockchain(config: &Config, channel: &NodeChannel) -> Result<Blockchain, failure::Error> {
    let node_config = &config.run_config.node_config;
    let service_factories = standard_exonum_service_factories();
    let database = create_database(config)?;
    let keypair = node_config.service_keypair();
    let api_sender = ApiSender::new(channel.api_requests.0.clone());
    let internal_requests = channel.internal_requests.0.clone();

    BlockchainBuilder::new(database, node_config.consensus.clone(), keypair)
        // TODO: add Java runtime
        // Review: InstanceCollection? We don't seem to have instances unless started, do we?
        .with_rust_runtime(service_factories.into_iter().map(InstanceCollection::new))
        .finalize(api_sender, internal_requests)
}

fn create_database(config: &Config) -> Result<Arc<dyn Database>, failure::Error> {
    let database = Arc::new(RocksDB::open(
        &config.run_config.db_path,
        &config.run_config.node_config.database,
    )?) as Arc<dyn Database>;
    Ok(database)
}

fn standard_exonum_service_factories() -> Vec<Box<dyn ServiceFactory>> {
    // TODO: add anchoring & time services
    vec![]
}
