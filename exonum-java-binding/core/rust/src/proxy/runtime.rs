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

use exonum::{
    api::ApiContext,
    blockchain::Schema as CoreSchema,
    crypto::{Hash, PublicKey, SecretKey},
    helpers::ValidatorId,
    messages::BinaryValue,
    exonum_merkledb::{self, Fork, Snapshot},
    node::ApiSender,
    runtime::{
        dispatcher::{DispatcherRef, DispatcherSender},
        ApiChange, ArtifactId, ArtifactProtobufSpec, CallInfo, ErrorKind, ExecutionContext,
        ExecutionError, InstanceDescriptor, InstanceId, InstanceSpec, Runtime, RuntimeIdentifier,
        StateHashAggregator,
    },
};
use futures::{Future, IntoFuture};
use jni::{
    objects::{GlobalRef, JObject, JValue},
    signature::{JavaType, Primitive},
    Executor, JNIEnv
};
use proto;
use proxy::node::NodeContext;
use runtime::Error;
use std::fmt;
use storage::View;
use to_handle;
use utils::{
    jni_cache::{classes_refs, runtime_adapter, tx_execution_exception},
    panic_on_exception, unwrap_jni, get_and_clear_java_exception,
    describe_java_exception, get_exception_message,
};
use JniErrorKind;
use JniResult;

/// A proxy for `ServiceRuntimeAdapter`s.
#[derive(Clone)]
pub struct JavaRuntimeProxy {
    exec: Executor,
    runtime_adapter: GlobalRef,
}

impl JavaRuntimeProxy {
    /// Runtime Identifier
    pub const RUNTIME_ID: RuntimeIdentifier = RuntimeIdentifier::Java;

    /// Creates new `JavaRuntimeProxy` for given `ServiceRuntimeAdapter` object
    pub fn new(executor: Executor, adapter: GlobalRef) -> Self {
        JavaRuntimeProxy {
            exec: executor,
            runtime_adapter: adapter,
        }
    }

    fn parse_artifact(&self, artifact: &ArtifactId) -> Result<JavaArtifactId, ExecutionError> {
        if artifact.runtime_id != Self::RUNTIME_ID as u32 {
            Err(Error::IncorrectArtifactId.into())
        } else {
            Ok(JavaArtifactId(artifact.name.to_string()))
        }
    }


    /*
Review: @sidorov ValidatorId is (still) too complex. Why has it been removed? Shall we remove it as well?
*/
    /// If the current node is a validator, returns `Some(validator_id)`, for other nodes return `None`.
    fn validator_id(snapshot: &dyn Snapshot, pub_key: &PublicKey) -> Option<ValidatorId> {
        CoreSchema::new(snapshot)
            .consensus_config()
            .find_validator(|validator_keys| *pub_key == validator_keys.service_key)
    }

    /// If the current node is a validator, return its identifier, for other nodes return `default`.
    fn validator_id_or(
        snapshot: &dyn Snapshot,
        pub_key: &PublicKey,
        default: i32
    ) -> i32 {
        Self::validator_id(snapshot, pub_key)
            .map_or(default, |id| i32::from(id.0))
    }

    /// Converts `JniResult<R>` into `Result<R, ExecutionError>`.
    fn convert_jni<R>(res: JniResult<R>) -> Result<R, ExecutionError> {
        res.map_err(|err| {
            let kind: ErrorKind = match err.kind() {
                JniErrorKind::JavaException => Error::JavaException.into(),
                _ => Error::OtherJniError.into(),
            };
            (kind, err).into()
        })
    }

    /// Handles and clears any Java exceptions.
    ///
    /// Any JNI errors are converted into `ExecutionError` with their descriptions, for JNI errors
    /// like `JniErrorKind::JavaException` it gets (and clears) any exception that is currently
    /// being thrown, then exception is passed to corresponding `ExceptionHandler` according their
    /// type and `exception_handlers` mapping.
    /// `ExceptionHandlers::DEFAULT` is called in case of there is no any handlers or handlers are
    /// not matched to exception type.
    fn handle_exception<H, R>(
        env: &JNIEnv,
        result: JniResult<R>,
        exception_handlers: &[(&GlobalRef, H)],
    ) -> Result<R, ExecutionError>
        where
            H: Fn(&JNIEnv, JObject) -> ExecutionError,
    {
        result.map_err(|err| {
            match err.kind() {
                JniErrorKind::JavaException => {
                    let exception = get_and_clear_java_exception(env);
                    for (class, handler) in exception_handlers {
                        if unwrap_jni(env.is_instance_of(exception, *class)) {
                            return handler(env, exception);
                        }
                    }

                    ExceptionHandlers::DEFAULT(env, exception)
                },
                _ => (Error::OtherJniError, err).into(),
            }
        })
    }
}

impl Runtime for JavaRuntimeProxy {
    fn deploy_artifact(
        &mut self,
        artifact: ArtifactId,
        deploy_spec: Vec<u8>,
    ) -> Box<dyn Future<Item = (), Error = ExecutionError>> {

        let id = match self.parse_artifact(&artifact) {
            Ok(id) => id.to_string(),
            Err(err) => return Box::new(Err(err).into_future()),
        };

        let result = self.exec.with_attached(|env| {
            let artifact_id = JObject::from(env.new_string(id)?);
            let spec = JObject::from(env.byte_array_from_slice(&deploy_spec)?);

            let jni_res = env.call_method_unchecked(
                self.runtime_adapter.as_obj(),
                runtime_adapter::deploy_artifact_id(),
                JavaType::Primitive(Primitive::Void),
                &[
                    JValue::from(artifact_id),
                    JValue::from(spec),
                ],
            ).and_then(JValue::v);

            Ok(Self::handle_exception(
                env,
                jni_res,
                &[(&classes_refs::service_loading_exception(), ExceptionHandlers::SERVICE_LOADING)]
            ))
        });

        Box::new(
            Self::convert_jni(result)
                .unwrap_or_else(|err| Err(err))
                .into_future()
        )
    }

    fn is_artifact_deployed(&self, id: &ArtifactId) -> bool {
        let artifact = match self.parse_artifact(id) {
            Ok(id) => id.to_string(),
            Err(_) => {
                return false;
            },
        };

        unwrap_jni(self.exec.with_attached(|env| {
            let artifact_id = JObject::from(env.new_string(artifact)?);

            panic_on_exception(
                env,
                env.call_method_unchecked(
                    self.runtime_adapter.as_obj(),
                    runtime_adapter::is_artifact_deployed_id(),
                    JavaType::Primitive(Primitive::Boolean),
                    &[JValue::from(artifact_id)],
                ),
            ).z()
        }))
    }

    fn artifact_protobuf_spec(&self, _id: &ArtifactId) -> Option<ArtifactProtobufSpec> {
        Some(ArtifactProtobufSpec::default())
    }

    fn start_service(&mut self, spec: &InstanceSpec) -> Result<(), ExecutionError> {
        let adapter = self.runtime_adapter.as_obj().clone();
        let service_name = spec.name.clone();
        let id = spec.id;
        let artifact = self.parse_artifact(&spec.artifact)?;

        let result = self.exec.with_attached(|env| {
            let name = JObject::from(env.new_string(service_name)?);
            let artifact_id = JObject::from(env.new_string(artifact.to_string())?);

            let jni_res = env.call_method_unchecked(
                adapter,
                runtime_adapter::create_service_id(),
                JavaType::Primitive(Primitive::Void),
                &[
                    JValue::from(name),
                    JValue::from(id as i32),
                    JValue::from(artifact_id),
                ],
            ).and_then(JValue::v);

            Ok(Self::handle_exception::<&ExceptionHandler, ()>(env, jni_res, &[]))
        });
        Self::convert_jni(result)?
    }

    fn initialize_service(
        &self,
        fork: &Fork,
        descriptor: InstanceDescriptor,
        parameters: Vec<u8>,
    ) -> Result<(), ExecutionError> {

        let result = self.exec.with_attached(|env| {
            let id = descriptor.id as i32;
            let view_handle = to_handle(View::from_ref_fork(fork));
            let params = JObject::from(env.byte_array_from_slice(&parameters)?);

            let jni_res = env.call_method_unchecked(
                self.runtime_adapter.as_obj(),
                runtime_adapter::initialize_service_id(),
                JavaType::Primitive(Primitive::Void),
                &[
                    JValue::from(id),
                    JValue::from(view_handle),
                    JValue::from(params),
                ],
            ).and_then(JValue::v);
            Ok(Self::handle_exception::<&ExceptionHandler, ()>(env, jni_res, &[]))
        });
        Self::convert_jni(result)?
    }

    fn stop_service(&mut self, descriptor: InstanceDescriptor) -> Result<(), ExecutionError> {
        let adapter = self.runtime_adapter.as_obj();

        let result = self.exec.with_attached(|env| {
            let id = descriptor.id as i32;

            let jni_res = env.call_method_unchecked(
                adapter,
                runtime_adapter::stop_service_id(),
                JavaType::Primitive(Primitive::Void),
                &[
                    JValue::from(id),
                ],
            ).and_then(JValue::v);
            Ok(Self::handle_exception::<&ExceptionHandler, ()>(env, jni_res, &[]))
        });
        Self::convert_jni(result)?
    }

    fn execute(
        &self,
        context: &ExecutionContext,
        call_info: &CallInfo,
        arguments: &[u8],
    ) -> Result<(), ExecutionError> {
        let tx = if let Some((hash, pub_key)) = context.caller.as_transaction()
        {
            (hash.to_bytes(), pub_key.to_bytes())
        } else {
            /* Review: Is there an easier way to check that? If not, I'd request one from core,
because this way is not intuitive.
*/
            // TODO: caller is Blockchain (not Transaction) is not supported  yet
            return Err(Error::NotSupportedOperation.into());
        };
        let res = self.exec.with_attached(|env| {
            let service_id = call_info.instance_id as i32;
            let tx_id = call_info.method_id as i32;
            let args = JObject::from(env.byte_array_from_slice(arguments)?);
            let view_handle = to_handle(View::from_ref_fork(context.fork));
            let pub_key = JObject::from(env.byte_array_from_slice(&tx.0)?);
            let hash = JObject::from(env.byte_array_from_slice(&tx.1)?);

            let result = env.call_method_unchecked(
                self.runtime_adapter.as_obj(),
                runtime_adapter::execute_tx_id(),
                JavaType::Primitive(Primitive::Void),
                &[
                    JValue::from(service_id),
                    JValue::from(tx_id),
                    JValue::from(args),
                    JValue::from(view_handle),
                    JValue::from(pub_key),
                    JValue::from(hash),
                ],
            ).and_then(JValue::v);

            let checked_result = Self::handle_exception(
                env,
                result,
                &[(&classes_refs::transaction_execution_exception(), ExceptionHandlers::TX_EXECUTION)]
            );

            Ok(checked_result)
        });
        Self::convert_jni(res)?
    }

    fn state_hashes(&self, snapshot: &dyn Snapshot) -> StateHashAggregator {
        let bytes = unwrap_jni(self.exec.with_attached(|env| {
            let view_handle = to_handle(View::from_ref_snapshot(snapshot));
            let java_runtime_hashes = panic_on_exception(
                env,
                env.call_method_unchecked(
                    self.runtime_adapter.as_obj(),
                    runtime_adapter::state_hashes_id(),
                    JavaType::Array(Box::new(JavaType::Primitive(Primitive::Byte))),
                    &[JValue::from(view_handle)],
                ),
            );
            let byte_array = java_runtime_hashes.l()?.into_inner();
            let data = env.convert_byte_array(byte_array)?;

            Ok(data)
        }));

        ServiceRuntimeStateHashes::from_bytes(bytes.into())
            .unwrap()
            .into()
    }

    fn before_commit(&self, _dispatcher: &DispatcherRef, fork: &mut Fork) {
        unwrap_jni(self.exec.with_attached(|env| {
            /*
            Review: !from_ref_mut_fork , according to the spec.
            */
            let view_handle = to_handle(View::from_ref_fork(fork));

            panic_on_exception(
                env,
                env.call_method_unchecked(
                    self.runtime_adapter.as_obj(),
                    runtime_adapter::before_commit_id(),
                    JavaType::Primitive(Primitive::Void),
                    &[
                        JValue::from(view_handle),
                    ],
                ),
            );
            Ok(())
        }));
    }

    fn after_commit(
        &self,
        _dispatcher: &DispatcherSender,
        snapshot: &Snapshot,
        service_keypair: &(PublicKey, SecretKey),
        _tx_sender: &ApiSender,
    ) {
        unwrap_jni(self.exec.with_attached(|env| {
            let view_handle = to_handle(View::from_ref_snapshot(snapshot));
            let validator_id = Self::validator_id_or(snapshot, &service_keypair.0, -1);
            let height:u64 = CoreSchema::new(snapshot).height().into();

            panic_on_exception(
                env,
                env.call_method_unchecked(
                    self.runtime_adapter.as_obj(),
                    runtime_adapter::after_commit_id(),
                    JavaType::Primitive(Primitive::Void),
                    &[
                        JValue::from(view_handle),
                        JValue::from(validator_id),
                        JValue::from(height as i64),
                    ],
                ),
            );
            Ok(())
        }));
    }

    fn notify_api_changes(&self, context: &ApiContext, changes: &[ApiChange]) {
        let added_instances_ids: Vec<i32> = changes.iter()
            .filter_map(|change| {
                if let ApiChange::InstanceAdded(instance_id) = change {
                    Some(*instance_id as i32)
                } else {
                    None
                }
            })
            .collect();

        if added_instances_ids.is_empty() {
            return;
        }

        let node = NodeContext::new(self.exec.clone(), context.clone());

        unwrap_jni(self.exec.with_attached(|env| {
            let node_handle = to_handle(node);
            let ids_array = env.new_int_array(added_instances_ids.len() as i32)?;
            env.set_int_array_region(ids_array, 0, &added_instances_ids)?;
            let service_ids = JObject::from(ids_array);

            panic_on_exception(
                env,
                env.call_method_unchecked(
                    self.runtime_adapter.as_obj(),
                    runtime_adapter::connect_apis_id(),
                    JavaType::Primitive(Primitive::Void),
                    &[
                        JValue::from(service_ids),
                        JValue::from(node_handle),
                    ],
                ),
            );
            Ok(())
        }));
    }
}

impl fmt::Debug for JavaRuntimeProxy {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "JavaRuntimeProxy()")
    }
}

/// Artifact identification properties within `JavaRuntimeProxy`
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct JavaArtifactId(String);

impl fmt::Display for JavaArtifactId {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Clone, ProtobufConvert, PartialEq)]
#[exonum(pb = "proto::ServiceStateHashes")]
struct ServiceStateHashes {
    instance_id: u32,
    state_hashes: Vec<Vec<u8>>,
}

impl From<&ServiceStateHashes> for (InstanceId, Vec<Hash>) {
    fn from(value: &ServiceStateHashes) -> Self {
        let hashes: Vec<Hash> = value
            .state_hashes
            .iter()
            .map(|bytes| to_hash(bytes))
            .collect();
        (value.instance_id, hashes)
    }
}

#[derive(Serialize, Deserialize, Clone, ProtobufConvert, PartialEq)]
#[exonum(pb = "proto::ServiceRuntimeStateHashes")]
struct ServiceRuntimeStateHashes {
    runtime_state_hashes: Vec<Vec<u8>>,
    service_state_hashes: Vec<ServiceStateHashes>,
}

impl ServiceRuntimeStateHashes {
    fn runtime(&self) -> Vec<Hash> {
        // ! Review: Shan't this code be extracted: to_hashes(hashes: Vec<Vec<u8>>) -> Vec<Hash>?
        self.runtime_state_hashes
            .iter()
            .map(|bytes| to_hash(bytes))
            .collect()
    }

    fn instances(&self) -> Vec<(InstanceId, Vec<Hash>)> {
        self.service_state_hashes
            .iter()
            .map(|service| service.into())
            .collect()
    }
}

impl From<ServiceRuntimeStateHashes> for StateHashAggregator {
    fn from(value: ServiceRuntimeStateHashes) -> Self {
        StateHashAggregator {
            runtime: value.runtime(),
            instances: value.instances(),
        }
    }
}

fn to_hash(bytes: &Vec<u8>) -> Hash {
    Hash::from_bytes(bytes.into()).unwrap()
}

type ExceptionHandler = Fn(&JNIEnv, JObject) -> ExecutionError;
struct ExceptionHandlers;

impl ExceptionHandlers {
    const DEFAULT: &'static ExceptionHandler = &|env, exception| {
        assert!(!exception.is_null(), "No exception thrown.");
        let message = describe_java_exception(env, exception);
        (Error::JavaException, message).into()
    };

    const TX_EXECUTION: &'static ExceptionHandler = &|env, exception| {
        assert!(!exception.is_null(), "No exception thrown.");
        let code = unwrap_jni(Self::get_tx_error_code(env, exception)) as u8;
        let msg = unwrap_jni(get_exception_message(env, exception))
            .unwrap_or(String::new());
        ExecutionError::new(ErrorKind::service(code), msg)
    };

    const SERVICE_LOADING: &'static ExceptionHandler = &|env, exception| {
        assert!(!exception.is_null(), "No exception thrown.");
        /*
        Review: Do we need to handle service loading exception specially
        (it is converted to JavaException)?
        */
        let msg = unwrap_jni(get_exception_message(env, exception));
        let desc = format!("ServiceLoadingException(message: {:?})", msg);
        /*
        Review: Is there any benefit in defining more specific runtime errors?
        */
        (Error::JavaException, desc).into()
    };

    fn get_tx_error_code(env: &JNIEnv, exception: JObject) -> JniResult<i8> {
        let err_code = env.call_method_unchecked(
            exception,
            tx_execution_exception::get_error_code_id(),
            JavaType::Primitive(Primitive::Byte),
            &[],
        )?;
        err_code.b()
    }
}
