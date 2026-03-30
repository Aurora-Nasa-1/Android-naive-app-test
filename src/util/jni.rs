use crate::server::{start_server, ServerConfig};
use jni::objects::{JClass, JString};
use jni::JNIEnv;
use std::sync::atomic::{AtomicBool, Ordering};
use tracing_subscriber::prelude::*;

static SERVER_STARTED: AtomicBool = AtomicBool::new(false);

/// # Safety
///
/// This function is called by the Android application via JNI.
/// It starts the Rust server in a background thread.
#[no_mangle]
#[allow(unsafe_code)]
pub unsafe extern "system" fn Java_com_ncm_player_util_RustServerManager_startNativeServer(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    port: i32,
) {
    if SERVER_STARTED.swap(true, Ordering::SeqCst) {
        return;
    }

    // Initialize Android logging
    #[cfg(feature = "jni")]
    {
        let _ = tracing_subscriber::registry()
            .with(tracing_android::layer("ncm-rust").unwrap())
            .try_init();
    }

    let host_str: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => "127.0.0.1".to_string(),
    };

    let config = ServerConfig {
        host: host_str,
        port: port as u16,
        ..Default::default()
    };

    std::thread::spawn(move || {
        let rt = tokio::runtime::Runtime::new().unwrap();
        rt.block_on(async {
            start_server(config).await;
        });
    });
}
