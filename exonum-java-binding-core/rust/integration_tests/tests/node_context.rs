extern crate failure;
extern crate futures;
extern crate integration_tests;
extern crate java_bindings;
#[macro_use]
extern crate lazy_static;

use std::sync::Arc;

use futures::{
    Stream,
    sync::mpsc::{self, Receiver},
};

use integration_tests::vm::create_vm_for_tests_with_fake_classes;
use java_bindings::{
    exonum::{
        blockchain::{Blockchain, Service, Transaction},
        crypto::{gen_keypair, Hash, PublicKey, SecretKey},
        messages::{RawTransaction, ServiceTransaction},
        node::{ApiSender, ExternalMessage},
        storage::{MemoryDB, Snapshot},
    },
    jni::JavaVM,
    MainExecutor, NodeContext,
};

lazy_static! {
    static ref VM: Arc<JavaVM> = create_vm_for_tests_with_fake_classes();
    pub static ref EXECUTOR: MainExecutor = MainExecutor::new(VM.clone());
}
/* Review: As this method now does a little more
(extracts some variables like service_id, signs the transaction) can we test that as well?

 Please push all the meaningful operations to `submit`, leaving only conversion in the glue code,
 and test what submit does.
 */

#[test]
fn submit_transaction() {
    let keypair = gen_keypair();
    let (node, app_rx) = create_node(keypair.0, keypair.1);
    let service_id = 0;
    let transaction_id = 0;
    let tx_payload = vec![1, 2, 3];
    let service_transaction = ServiceTransaction::from_raw_unchecked(transaction_id, tx_payload);
    let raw_transaction = RawTransaction::new(service_id, service_transaction);
    // Review: Please make this test clearer, currently the code under test is mixed with the setup code.
    node.submit(raw_transaction.clone()).unwrap();
    let sent_message = app_rx.wait().next().unwrap().unwrap();
    /*
    Review: The naming gets quite confusing: A ServiceTransaction has payload, and a SignedMessage
    has payload. Do you think anything can be clarified in the core? If not, at least please extract
    setn.payload in a variable.
    */
    match sent_message {
        ExternalMessage::Transaction(sent) => {
            /* Review: This naming is misleading: it is **not** a tx payload. */
            let tx_payload = sent.payload();
            let tx_author = sent.author();
            assert_eq!(&raw_transaction, tx_payload);
            assert_eq!(tx_author, keypair.0);
        }
        _ => panic!("Message is not Transaction"),
    }
}

#[test]
#[ignore]
fn submit_transaction_to_missing_service() {
    let keypair = gen_keypair();
    let (node, _) = create_node(keypair.0, keypair.1);
    // invalid service_id
    let service_id = 1;
    let transaction_id = 0;
    let tx_payload = vec![1, 2, 3];
    let service_transaction = ServiceTransaction::from_raw_unchecked(transaction_id, tx_payload);
    let raw_transaction = RawTransaction::new(service_id, service_transaction);
    let res = node.submit(raw_transaction.clone());
    assert!(res.is_err());
}

/* Review: Why don't we pass a keypair, releaving the callers from unpacking it? */
fn create_node(
    public_key: PublicKey,
    secret_key: SecretKey,
) -> (NodeContext, Receiver<ExternalMessage>) {
    let api_channel = mpsc::channel(128);
    let (app_tx, app_rx) = (ApiSender::new(api_channel.0), api_channel.1);

    struct EmptyService;

    impl Service for EmptyService {
        fn service_id(&self) -> u16 {
            0
        }

        fn service_name(&self) -> &str {
            "empty_service"
        }

        fn state_hash(&self, _: &Snapshot) -> Vec<Hash> {
            vec![]
        }

        fn tx_from_raw(&self, _: RawTransaction) -> Result<Box<dyn Transaction>, failure::Error> {
            unimplemented!()
        }
    }

    let storage = MemoryDB::new();
    let blockchain = Blockchain::new(
        storage,
        vec![Box::new(EmptyService)],
        public_key,
        secret_key,
        app_tx.clone(),
    );
    let node = NodeContext::new(EXECUTOR.clone(), blockchain, public_key, app_tx);
    (node, app_rx)
}
