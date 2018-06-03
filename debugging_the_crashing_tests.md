# Test
I first launched a definitely crashing test:
  - `$ git checkout native-proxies`
  - Found a definitely crashing test: `com.exonum.binding.storage.indices.ProofMapIndexProxyIntegrationTest#getProof_MultiEntryMapDoesNotContain`
  - Set up in IDEA `LD_LIBRARY_PATH=/home/dmitry/.rustup/toolchains/stable-x86_64-unknown-linux-gnu/lib` for this test run.
    Set a proper value with `$(rustup run "$RUST_VERSION" rustc --print sysroot)/lib`.
  - Added a sleep in `@BeforeClass` (couldn't read from `stdin`, probably because of the test runner)

```
Index: exonum-java-binding-core/src/test/java/com/exonum/binding/storage/indices/ProofMapIndexProxyIntegrationTest.java
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
--- exonum-java-binding-core/src/test/java/com/exonum/binding/storage/indices/ProofMapIndexProxyIntegrationTest.java	(date 1527873656000)
+++ exonum-java-binding-core/src/test/java/com/exonum/binding/storage/indices/ProofMapIndexProxyIntegrationTest.java	(date 1528038617000)
@@ -30,6 +30,7 @@
 import com.google.common.collect.ImmutableList;
 import com.google.common.collect.Streams;
 import com.google.common.primitives.UnsignedBytes;
+import java.time.Duration;
 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.BitSet;
@@ -40,6 +41,7 @@
 import java.util.stream.Collectors;
 import java.util.stream.IntStream;
 import java.util.stream.Stream;
+import org.junit.BeforeClass;
 import org.junit.Rule;
 import org.junit.Test;
 import org.junit.rules.ExpectedException;
@@ -80,6 +82,11 @@
   private static final HashCode EMPTY_MAP_ROOT_HASH = HashCode.fromBytes(
       new byte[DEFAULT_HASH_SIZE_BYTES]);
 
+  @BeforeClass
+  public static void waitBeforeTest() throws Exception {
+    Thread.sleep(Duration.ofSeconds(16).toMillis());
+  }
+
   @Test
   public void containsKey() throws Exception {
     runTestWithView(database::createFork, (map) -> {
```

  - Launched the test from IDEA, verified the crash.
  - Took the command line.
  - Put in a script below.
```
#!/usr/bin/env bash
LD_LIBRARY_PATH=/home/dmitry/.rustup/toolchains/stable-x86_64-unknown-linux-gnu/lib \
  /home/dmitry/bin/jdk1.8.0_162/bin/java -ea -ea:com.exonum.binding... -Djava.library.path=/home/dmitry/Documents/exonum-java-binding/exonum-java-binding-core/rust/target/debug -Xcheck:jni -ea:com.exonum.binding... -Xss1500k -Didea.test.cyclic.buffer.size=1048576 -javaagent:/home/dmitry/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0/181.5087.20/lib/idea_rt.jar=42333:/home/dmitry/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0/181.5087.20/bin -Dfile.encoding=UTF-8 -classpath /home/dmitry/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0/181.5087.20/lib/idea_rt.jar:/home/dmitry/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0/181.5087.20/plugins/junit/lib/junit-rt.jar:/home/dmitry/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0/181.5087.20/plugins/junit/lib/junit5-rt.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/charsets.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/deploy.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/cldrdata.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/dnsns.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/jaccess.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/jfxrt.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/localedata.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/nashorn.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/sunec.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/sunjce_provider.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/sunpkcs11.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/ext/zipfs.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/javaws.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/jce.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/jfr.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/jfxswt.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/jsse.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/management-agent.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/plugin.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/resources.jar:/home/dmitry/bin/jdk1.8.0_162/jre/lib/rt.jar:/home/dmitry/Documents/exonum-java-binding/exonum-java-binding-core/target/test-classes:/home/dmitry/Documents/exonum-java-binding/exonum-java-binding-core/target/classes:/home/dmitry/Documents/exonum-java-binding/exonum-java-proofs/target/classes:/home/dmitry/.m2/repository/com/google/guava/guava/24.0-jre/guava-24.0-jre.jar:/home/dmitry/.m2/repository/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar:/home/dmitry/.m2/repository/org/checkerframework/checker-compat-qual/2.0.0/checker-compat-qual-2.0.0.jar:/home/dmitry/.m2/repository/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar:/home/dmitry/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar:/home/dmitry/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar:/home/dmitry/.m2/repository/org/apache/logging/log4j/log4j-api/2.10.0/log4j-api-2.10.0.jar:/home/dmitry/.m2/repository/org/apache/logging/log4j/log4j-core/2.10.0/log4j-core-2.10.0.jar:/home/dmitry/.m2/repository/io/vertx/vertx-web/3.5.1/vertx-web-3.5.1.jar:/home/dmitry/.m2/repository/io/vertx/vertx-auth-common/3.5.1/vertx-auth-common-3.5.1.jar:/home/dmitry/.m2/repository/io/vertx/vertx-bridge-common/3.5.1/vertx-bridge-common-3.5.1.jar:/home/dmitry/.m2/repository/io/vertx/vertx-core/3.5.1/vertx-core-3.5.1.jar:/home/dmitry/.m2/repository/io/netty/netty-common/4.1.19.Final/netty-common-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-buffer/4.1.19.Final/netty-buffer-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-transport/4.1.19.Final/netty-transport-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-handler/4.1.19.Final/netty-handler-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-codec/4.1.19.Final/netty-codec-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-handler-proxy/4.1.19.Final/netty-handler-proxy-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-codec-socks/4.1.19.Final/netty-codec-socks-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-codec-http/4.1.19.Final/netty-codec-http-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-codec-http2/4.1.19.Final/netty-codec-http2-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-resolver/4.1.19.Final/netty-resolver-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-resolver-dns/4.1.19.Final/netty-resolver-dns-4.1.19.Final.jar:/home/dmitry/.m2/repository/io/netty/netty-codec-dns/4.1.19.Final/netty-codec-dns-4.1.19.Final.jar:/home/dmitry/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.9.3/jackson-core-2.9.3.jar:/home/dmitry/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.9.3/jackson-databind-2.9.3.jar:/home/dmitry/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar:/home/dmitry/.m2/repository/com/google/inject/guice/4.1.0/guice-4.1.0.jar:/home/dmitry/.m2/repository/javax/inject/javax.inject/1/javax.inject-1.jar:/home/dmitry/.m2/repository/aopalliance/aopalliance/1.0/aopalliance-1.0.jar:/home/dmitry/.m2/repository/com/google/auto/value/auto-value/1.5.3/auto-value-1.5.3.jar:/home/dmitry/.m2/repository/org/mockito/mockito-core/2.8.47/mockito-core-2.8.47.jar:/home/dmitry/.m2/repository/net/bytebuddy/byte-buddy/1.6.14/byte-buddy-1.6.14.jar:/home/dmitry/.m2/repository/net/bytebuddy/byte-buddy-agent/1.6.14/byte-buddy-agent-1.6.14.jar:/home/dmitry/.m2/repository/org/objenesis/objenesis/2.5/objenesis-2.5.jar:/home/dmitry/.m2/repository/org/powermock/powermock-module-junit4/1.7.1/powermock-module-junit4-1.7.1.jar:/home/dmitry/.m2/repository/org/powermock/powermock-module-junit4-common/1.7.1/powermock-module-junit4-common-1.7.1.jar:/home/dmitry/.m2/repository/org/powermock/powermock-reflect/1.7.1/powermock-reflect-1.7.1.jar:/home/dmitry/.m2/repository/org/powermock/powermock-core/1.7.1/powermock-core-1.7.1.jar:/home/dmitry/.m2/repository/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar:/home/dmitry/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/home/dmitry/.m2/repository/org/powermock/powermock-api-mockito2/1.7.1/powermock-api-mockito2-1.7.1.jar:/home/dmitry/.m2/repository/org/powermock/powermock-api-mockito-common/1.7.1/powermock-api-mockito-common-1.7.1.jar:/home/dmitry/.m2/repository/org/powermock/powermock-api-support/1.7.1/powermock-api-support-1.7.1.jar:/home/dmitry/.m2/repository/org/apache/logging/log4j/log4j-core/2.10.0/log4j-core-2.10.0-tests.jar:/home/dmitry/.m2/repository/io/vertx/vertx-web-client/3.5.1/vertx-web-client-3.5.1.jar:/home/dmitry/.m2/repository/io/vertx/vertx-web-common/3.5.1/vertx-web-common-3.5.1.jar:/home/dmitry/Documents/exonum-java-binding/exonum-java-testing/target/classes:/home/dmitry/.m2/repository/junit/junit/4.12/junit-4.12.jar:/home/dmitry/.m2/repository/org/hamcrest/java-hamcrest/2.0.0.0/java-hamcrest-2.0.0.0.jar com.intellij.rt.execution.junit.JUnitStarter -ideVersion5 -junit4 com.exonum.binding.storage.indices.ProofMapIndexProxyIntegrationTest,getProof_MultiEntryMapDoesNotContain
```

2. Launched the script.

3. Took the pid of the JVM process

4. Attached the gdb: 
```
gdb-rust -p <put-the-pid-of-the-JVM-running-test>
```

5. Observed the stack trace:
```
(gdb) bt
#0  je_arena_decay_ticks (nticks=1, arena=0x17203ac2c17, tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/include/jemalloc/internal/arena.h:1322
#1  je_arena_decay_tick (arena=0x17203ac2c17, tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/include/jemalloc/internal/arena.h:1333
#2  je_arena_ralloc_no_move (zero=<optimized out>, extra=0, 
    size=<optimized out>, oldsize=139741956580456, ptr=0x7f184c4bee20, 
    tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/arena.c:3317
#3  je_arena_ralloc (tsd=0x7f1854b31590, arena=arena@entry=0x0, 
    ptr=ptr@entry=0x7f184c4bee20, oldsize=oldsize@entry=8, size=size@entry=7, 
    alignment=alignment@entry=0, zero=zero@entry=false, 
    tcache=tcache@entry=0x7f1834a0d000)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/arena.c:3354
#4  0x00007f183582229b in je_iralloct (arena=0x0, tcache=0x7f1834a0d000, 
    zero=false, alignment=0, size=7, oldsize=8, ptr=0x7f184c4bee20, 
    tsd=<optimized out>) at include/jemalloc/internal/jemalloc_internal.h:1259
#5  rallocx (ptr=0x7f184c4bee20, size=7, flags=<optimized out>)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/jemalloc.c:2414
#6  0x00007f183581b3a6 in __rde_realloc () at liballoc_jemalloc/lib.rs:167
#7  0x00007f1835813ebf in _$LT$alloc..heap..Heap$u20$as$u20$core..heap..Alloc$GT$::realloc::hbfc14832c81ab56b () at /checkout/src/liballoc/heap.rs:127
#8  _$LT$alloc..raw_vec..RawVec$LT$T$C$$u20$A$GT$$GT$::try_reserve_exact::h189d9332178abea1 () at /checkout/src/liballoc/raw_vec.rs:433
#9  _$LT$alloc..raw_vec..RawVec$LT$T$C$$u20$A$GT$$GT$::reserve_exact::h6136a190042d8782 () at /checkout/src/liballoc/raw_vec.rs:446
#10 0x00007f183580ad5c in _$LT$alloc..vec..Vec$LT$T$GT$$GT$::reserve_exact::hadc338bd943a6253 () at /checkout/src/liballoc/vec.rs:490
#11 std::ffi::c_str::CString::from_vec_unchecked::hb78a5c1ba968117a ()
    at libstd/ffi/c_str.rs:362
#12 0x00007f17f1bed9a0 in _$LT$jni..wrapper..strings..ffi_str..JNIString$u20$as$u20$core..convert..From$LT$T$GT$$GT$::from::hadfc4e965dfc24ca (other="<init>")
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/strings/ffi_str.rs:50
#13 0x00007f17f1bb7a1d in _$LT$T$u20$as$u20$core..convert..Into$LT$U$GT$$GT$::into::h94772d592eee6108 (self="<init>") at /checkout/src/libcore/convert.rs:396
#14 0x00007f17f1c2f1d6 in jni::wrapper::jnienv::JNIEnv::get_method_id_base::h0a163111551437d0 (self=0x7f1854b2eee8, class=JClass = {...}, name="<init>", 
    sig="([B[B[B[B)V", get_method=closure = {...})
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/jnienv.rs:437
---Type <return> to continue, or q <return> to quit---
920a44d (self=0x7f1854b2eee8, class=JClass = {...}, name="<init>", sig="([B[B[B[B)V")
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/jnienv.rs:469
#16 0x00007f17f1b9f739 in jni::wrapper::descriptors::method_desc::_$LT$impl$u20$jni..wrapper..descriptors..desc..Desc$LT$$u27$a$C$$u20$jni..wrapper..objects..jmethodid..JMethodID$LT$$u27$a$GT$$GT$$u20$for$u20$$LP$T$C$$u20$U$C$$u20$V$RP$$GT$::lookup::h476923bcf5555a6d (self=..., env=0x7f1854b2eee8)
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/descriptors/method_desc.rs:20
#17 0x00007f17f1b9f6e5 in jni::wrapper::descriptors::method_desc::_$LT$impl$u20$jni..wrapper..descriptors..desc..Desc$LT$$u27$a$C$$u20$jni..wrapper..objects..jmethodid..JMethodID$LT$$u27$a$GT$$GT$$u20$for$u20$$LP$T$C$$u20$Signature$RP$$GT$::lookup::h3879e6aead78b5d7 (self=..., env=0x7f1854b2eee8)
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/descriptors/method_desc.rs:30
#18 0x00007f17f1c0dd1f in jni::wrapper::jnienv::JNIEnv::new_object::h930e2bb5a718ae4d (self=0x7f1854b2eee8, 
    class="com/exonum/binding/storage/proofs/map/MappingNotFoundProofBranch", ctor_sig="([B[B[B[B)V", 
    ctor_args=&[jni::wrapper::objects::jvalue::JValue](len: 4) = {...}) at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/jnienv.rs:900
#19 0x00007f17f1bd6cfd in java_bindings::storage::proof_map_index::make_java_mapping_not_found_branch::hd0f52b4d93478f98 (env=0x7f1854b2eee8, left_hash=0x7f1854b2eb99, 
    right_hash=0x7f1854b2ebb9, left_key=0x7f1854b2ebda, right_key=0x7f1854b2ebfe) at src/storage/proof_map_index.rs:524
#20 0x00007f17f1bd6474 in java_bindings::storage::proof_map_index::make_java_brach_proof::h7c466a2e9f9869cc (env=0x7f1854b2eee8, branch=0x7f1854b2eb98)
    at src/storage/proof_map_index.rs:496
#21 0x00007f17f1b33436 in java_bindings::storage::proof_map_index::Java_com_exonum_binding_storage_indices_ProofMapIndexProxy_nativeGetProof::_$u7b$$u7b$closure$u7d$$u7d$::h0ea15af1e5be6fc6 () at src/storage/proof_map_index.rs:140
#22 0x00007f17f1b9b9ea in std::panicking::try::do_call::hbc669e06f3de4a32 (data=0x7f1854b2eda0 "\350\356\262T\030\177") at /checkout/src/libstd/panicking.rs:306
#23 0x00007f183581accf in __rust_maybe_catch_panic () at libpanic_unwind/lib.rs:102
#24 0x00007f17f1b98f86 in std::panicking::try::hfba861b66dd45dae (f=...) at /checkout/src/libstd/panicking.rs:285
#25 0x00007f17f1bb8752 in std::panic::catch_unwind::h0e8e1709c85560e0 (f=...) at /checkout/src/libstd/panic.rs:361
#26 0x00007f17f1bd38ba in Java_com_exonum_binding_storage_indices_ProofMapIndexProxy_nativeGetProof (env=JNIEnv = {...}, map_handle=139741938934000, key=0x7f1854b2f0a8)
    at src/storage/proof_map_index.rs:125
#27 0x00007f183d016347 in ?? ()
#28 0x00007f183cc58a38 in ?? ()
#29 0x0000000000000000 in ?? ()
```

6. Let's look at some frames, going up the stack:
```
(gdb) frame 18
#18 0x00007f17f1c0dd1f in jni::wrapper::jnienv::JNIEnv::new_object::h930e2bb5a718ae4d (self=0x7f1854b2eee8, 
    class="com/exonum/binding/storage/proofs/map/MappingNotFoundProofBranch", ctor_sig="([B[B[B[B)V", 
    ctor_args=&[jni::wrapper::objects::jvalue::JValue](len: 4) = {...}) at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/jnienv.rs:900
900	        let method_id: JMethodID = (class, ctor_sig).lookup(self)?;
```

Here we create a new object: MappingNotFoundProofBranch. The lib performs a lookup of the method id (frame 17), which turns into a `get_method_id` call (frame 16). In frame 14, we convert the passed name into `ffi_name`:
```
(gdb) frame 14
#14 0x00007f17f1c2f1d6 in jni::wrapper::jnienv::JNIEnv::get_method_id_base::h0a163111551437d0 (self=0x7f1854b2eee8, class=JClass = {...}, name="<init>", 
    sig="([B[B[B[B)V", get_method=closure = {...}) at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/jnienv.rs:437
437	        let ffi_name = name.into();
```

The next frame isn't resolved properly:
```
(gdb) frame 13
#13 0x00007f17f1bb7a1d in _$LT$T$u20$as$u20$core..convert..Into$LT$U$GT$$GT$::into::h94772d592eee6108 (self="<init>") at /checkout/src/libcore/convert.rs:396
396	/checkout/src/libcore/convert.rs: No such file or directory.
```

The 12th:
```
(gdb) frame 12
#12 0x00007f17f1bed9a0 in _$LT$jni..wrapper..strings..ffi_str..JNIString$u20$as$u20$core..convert..From$LT$T$GT$$GT$::from::hadfc4e965dfc24ca (other="<init>")
    at /home/dmitry/.cargo/git/checkouts/jni-rs-75341f86635fc618/ccd041d/src/wrapper/strings/ffi_str.rs:50
50	            internal: unsafe { ffi::CString::from_vec_unchecked(enc) },

```

Here we perform `ffi::CString::from_vec_unchecked(enc)`, passing an enc. Now we have to go not up the stack, but back a couple of expressions in the current stack. Let's see how this function looks like:
```
impl<T> From<T> for JNIString
where
    T: AsRef<str>,
{
    fn from(other: T) -> Self {
        let enc = to_java_cesu8(other.as_ref()).into_owned();
        JNIString {
            internal: unsafe { ffi::CString::from_vec_unchecked(enc) },
        }
    }
}
```

`java_cesu8` signature: `pub fn to_java_cesu8(text: &str) -> Cow<[u8]>`. Its doc says: "[If] This string is valid as UTF-8 or CESU-8, so it doesn't change, and we can convert it without allocating memory."
`&str -> Cow<[u8]>/borrowed -> Cow<[u8]>/owned -> Vec<u8>`

`Cow<[u8]>` gets converted into a `Vec<u8>`, which is then used to create a CString:

`pub unsafe fn from_vec_unchecked(mut v: Vec<u8>) -> CString`.

What happens next — who knows (had no chance to carefully examine):
```
#0  je_arena_decay_ticks (nticks=1, arena=0x17203ac2c17, tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/include/jemalloc/internal/arena.h:1322
#1  je_arena_decay_tick (arena=0x17203ac2c17, tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/include/jemalloc/internal/arena.h:1333
#2  je_arena_ralloc_no_move (zero=<optimized out>, extra=0, 
    size=<optimized out>, oldsize=139741956580456, ptr=0x7f184c4bee20, 
    tsdn=0x7f1854b31590)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/arena.c:3317
#3  je_arena_ralloc (tsd=0x7f1854b31590, arena=arena@entry=0x0, 
    ptr=ptr@entry=0x7f184c4bee20, oldsize=oldsize@entry=8, size=size@entry=7, 
    alignment=alignment@entry=0, zero=zero@entry=false, 
    tcache=tcache@entry=0x7f1834a0d000)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/arena.c:3354
#4  0x00007f183582229b in je_iralloct (arena=0x0, tcache=0x7f1834a0d000, 
    zero=false, alignment=0, size=7, oldsize=8, ptr=0x7f184c4bee20, 
    tsd=<optimized out>) at include/jemalloc/internal/jemalloc_internal.h:1259
#5  rallocx (ptr=0x7f184c4bee20, size=7, flags=<optimized out>)
    at /checkout/src/liballoc_jemalloc/../jemalloc/src/jemalloc.c:2414
#6  0x00007f183581b3a6 in __rde_realloc () at liballoc_jemalloc/lib.rs:167
#7  0x00007f1835813ebf in _$LT$alloc..heap..Heap$u20$as$u20$core..heap..Alloc$GT$::realloc::hbfc14832c81ab56b () at /checkout/src/liballoc/heap.rs:127
#8  _$LT$alloc..raw_vec..RawVec$LT$T$C$$u20$A$GT$$GT$::try_reserve_exact::h189d9332178abea1 () at /checkout/src/liballoc/raw_vec.rs:433
#9  _$LT$alloc..raw_vec..RawVec$LT$T$C$$u20$A$GT$$GT$::reserve_exact::h6136a190042d8782 () at /checkout/src/liballoc/raw_vec.rs:446
#10 0x00007f183580ad5c in _$LT$alloc..vec..Vec$LT$T$GT$$GT$::reserve_exact::hadc338bd943a6253 () at /checkout/src/liballoc/vec.rs:490
#11 std::ffi::c_str::CString::from_vec_unchecked::hb78a5c1ba968117a ()
```

# Sanitizing your app
https://github.com/japaric/rust-san#memorysanitizer-use-of-uninitialized-value-in-the-test-runner
- Use nightly
- Pass a flag
- Specify target

Rust app works OK, (e.g., unit tests of exonum). Native ITs can work: https://github.com/exonum/exonum-java-binding/pull/276#issuecomment-394166208, but yield lots of false-positives.
