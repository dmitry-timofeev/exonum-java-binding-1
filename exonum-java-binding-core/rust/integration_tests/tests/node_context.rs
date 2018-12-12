extern crate futures;
extern crate integration_tests;
extern crate java_bindings;
#[macro_use]
extern crate lazy_static;

use std::sync::Arc;

use futures::Stream;
use futures::sync::mpsc::{self, Receiver};

use integration_tests::vm::create_vm_for_tests_with_fake_classes;
use java_bindings::{
    Java_com_exonum_binding_service_NodeProxy_nativeSubmit, JniExecutor, JniResult, MainExecutor,
    NodeContext,
};
use java_bindings::exonum::blockchain::Blockchain;
use java_bindings::exonum::crypto::gen_keypair;
use java_bindings::exonum::messages::{BinaryForm, RawTransaction, ServiceTransaction};
use java_bindings::exonum::node::{ApiSender, ExternalMessage};
use java_bindings::exonum::storage::MemoryDB;
use java_bindings::jni::{JavaVM, JNIEnv};
use java_bindings::jni::objects::JObject;
use java_bindings::utils::{
    as_handle, get_and_clear_java_exception, get_class_name, unwrap_jni, unwrap_jni_verbose,
};

lazy_static! {
    static ref VM: Arc<JavaVM> = create_vm_for_tests_with_fake_classes();
    pub static ref EXECUTOR: MainExecutor = MainExecutor::new(VM.clone());
}
/* Review: As this method now does a little more
(extracts some variables like service_id, signs the transaction) can we test that as well? */

#[test]
fn submit_transaction() {
    let (mut node, app_rx) = create_node();
    let raw_transaction =
        RawTransaction::new(0, ServiceTransaction::from_raw_unchecked(0, vec![1, 2, 3]));
    node.submit(raw_transaction.clone()).unwrap();
    let sent_message = app_rx.wait().next().unwrap().unwrap();
    /*
    Review: The naming gets quite confusing: A ServiceTransaction has payload, and a SignedMessage
    has payload. Do you think anything can be clarified in the core? If not, at least please extract
    setn.payload in a variable.
    */
    match sent_message {
        ExternalMessage::Transaction(sent) => assert_eq!(&raw_transaction, sent.payload()),
        _ => panic!("Message is not Transaction"),
    }
}

fn create_node() -> (NodeContext, Receiver<ExternalMessage>) {
    let service_keypair = gen_keypair();
    let api_channel = mpsc::channel(128);
    let (app_tx, app_rx) = (ApiSender::new(api_channel.0), api_channel.1);

    let storage = MemoryDB::new();
    let blockchain = Blockchain::new(
        storage,
        vec![],
        service_keypair.0,
        service_keypair.1,
        app_tx.clone(),
    );
    let node = NodeContext::new(EXECUTOR.clone(), blockchain, service_keypair.0, app_tx);
    (node, app_rx)
}

fn message_from_raw<'e>(env: &'e JNIEnv<'e>, buffer: &[u8]) -> JniResult<JObject<'e>> {
    env.byte_array_from_slice(buffer).map(JObject::from)
}
