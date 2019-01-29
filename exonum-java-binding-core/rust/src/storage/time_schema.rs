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

use exonum::storage::{Fork, Snapshot};
use exonum_time::schema::TimeSchema;
use jni::{objects::JClass, JNIEnv};
use std::panic;
use storage::db::{View, ViewRef};
use utils::{self, Handle};

enum SchemaType {
    SnapshotSchema(TimeSchema<&'static Snapshot>),
    ForkSchema(TimeSchema<&'static mut Fork>),
}

/// Returns pointer to created TimeSchemaProxy object
#[no_mangle]
// Review: Why is this needed at all if this object does not have _any_ operations?
pub extern "system" fn Java_com_exonum_binding_time_TimeSchemaProxy_nativeCreate(
    env: JNIEnv,
    _: JClass,
    view_handle: Handle,
) -> Handle {
    let res = panic::catch_unwind(|| {
        let schema_type = match *utils::cast_handle::<View>(view_handle).get() {
            ViewRef::Snapshot(snapshot) => SchemaType::SnapshotSchema(TimeSchema::new(snapshot)),
            ViewRef::Fork(ref mut fork) => SchemaType::ForkSchema(TimeSchema::new(fork)),
        };
        Ok(utils::to_handle(schema_type))
    });
    utils::unwrap_exc_or_default(&env, res)
}

/// Destroys the underlying `Schema` object and frees memory.
#[no_mangle]
pub extern "system" fn Java_com_exonum_binding_time_TimeSchemaProxy_nativeFree(
    env: JNIEnv,
    _: JClass,
    schema_handle: Handle,
) {
    utils::drop_handle::<SchemaType>(&env, schema_handle);
}
