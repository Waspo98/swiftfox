use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

use adblock::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;

#[derive(Serialize)]
struct CosmeticPayload {
    css: String,
    #[serde(rename = "injectedScript")]
    injected_script: String,
    generichide: bool,
}

impl CosmeticPayload {
    fn empty_json() -> String {
        r#"{"css":"","injectedScript":"","generichide":false}"#.to_string()
    }
}

fn format_cosmetic_json(engine: &Engine, url: &str) -> String {
    let resources = engine.url_cosmetic_resources(url);
    let mut css = String::new();

    if !resources.hide_selectors.is_empty() {
        let mut selectors: Vec<&str> = resources.hide_selectors.iter().map(String::as_str).collect();
        selectors.sort_unstable();
        for chunk in selectors.chunks(400) {
            if !chunk.is_empty() {
                css.push_str(&chunk.join(","));
                css.push_str("{display:none!important}\n");
            }
        }
    }

    let payload = CosmeticPayload {
        css,
        injected_script: resources.injected_script,
        generichide: resources.generichide,
    };

    serde_json::to_string(&payload).unwrap_or_else(|_| CosmeticPayload::empty_json())
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCreateFromRules(
    mut env: JNIEnv,
    _class: JClass,
    rules_text: JString,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let rules: String = match env.get_string(&rules_text) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };
        let mut filter_set = FilterSet::new(true);
        let lines: Vec<&str> = rules.lines().collect();
        filter_set.add_filters(&lines, ParseOptions::default());
        let engine = Engine::from_filter_set(filter_set, true);
        Box::into_raw(Box::new(engine)) as jlong
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeDeserialize(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let bytes = match env.convert_byte_array(&data) {
            Ok(b) => b,
            Err(_) => return 0,
        };
        if bytes.is_empty() {
            return 0;
        }
        let mut engine = Engine::new(true);
        match engine.deserialize(&bytes) {
            Ok(()) => Box::into_raw(Box::new(engine)) as jlong,
            Err(_) => 0,
        }
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeSerialize(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    if handle == 0 {
        return ptr::null_mut();
    }
    catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(handle as *const Engine) };
        let bytes = match engine.serialize() {
            Ok(b) => b,
            Err(_) => return ptr::null_mut(),
        };
        match env.byte_array_from_slice(&bytes) {
            Ok(arr) => arr.into_raw(),
            Err(_) => ptr::null_mut(),
        }
    }))
    .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCheckNetworkRequest(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    source_url: JString,
    request_type: JString,
) -> jboolean {
    if handle == 0 {
        return JNI_FALSE;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let url_str: String = match env.get_string(&url) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };
        let source_str: String = match env.get_string(&source_url) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };
        let req_type_str: String = match env.get_string(&request_type) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };

        let request = match Request::new(&url_str, &source_str, &req_type_str) {
            Ok(r) => r,
            Err(_) => return JNI_FALSE,
        };

        let engine = unsafe { &*(handle as *const Engine) };
        if engine.check_network_request(&request).matched {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    }))
    .unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCosmeticForUrl(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jstring {
    catch_unwind(AssertUnwindSafe(|| {
        let json = if handle == 0 {
            CosmeticPayload::empty_json()
        } else {
            match env.get_string(&url) {
                Ok(s) => {
                    let url_str: String = s.into();
                    let engine = unsafe { &*(handle as *const Engine) };
                    format_cosmetic_json(engine, &url_str)
                }
                Err(_) => CosmeticPayload::empty_json(),
            }
        };

        match env.new_string(&json) {
            Ok(s) => s.into_raw(),
            Err(_) => ptr::null_mut(),
        }
    }))
    .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        unsafe {
            drop(Box::from_raw(handle as *mut Engine));
        }
    }));
}
