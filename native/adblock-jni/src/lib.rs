use std::borrow::Cow;
use std::collections::HashSet;
use std::fs;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::ptr;

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::{MimeType, PermissionMask, Resource, ResourceType};
use adblock::Engine;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use base64::Engine as _;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;

const _: fn() = || {
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<Engine>();
};

#[derive(Serialize)]
struct CosmeticPayload {
    css: String,
    #[serde(rename = "injectedScript")]
    injected_script: String,
    generichide: bool,
    exceptions: Vec<String>,
}

impl CosmeticPayload {
    fn empty_json() -> String {
        r#"{"css":"","injectedScript":"","generichide":false,"exceptions":[]}"#.to_string()
    }
}

fn make_js_resource(name: &str, aliases: &[&str], code: &str) -> Resource {
    Resource {
        name: name.to_string(),
        aliases: aliases.iter().map(|s| (*s).to_string()).collect(),
        kind: ResourceType::Mime(MimeType::ApplicationJavascript),
        content: BASE64_STANDARD.encode(code.as_bytes()),
        dependencies: Vec::new(),
        permission: PermissionMask::default(),
    }
}

fn default_scriptlet_resources() -> Vec<Resource> {
    vec![
        make_js_resource(
            "noop.js",
            &["noopjs", "noopfunc"],
            "function noop() {}",
        ),
        make_js_resource(
            "window.name-defuser.js",
            &["window.name-defuser"],
            "function windowNameDefuser() { if (window === window.top) { try { window.name = ''; } catch (_) {} } }",
        ),
        make_js_resource(
            "set-constant.js",
            &["set.js", "ubo-set-constant.js", "set", "set-constant"],
            r#"function setConstant(chain, cValue) {
    if (!chain) return;
    var val;
    if (cValue === 'undefined') val = undefined;
    else if (cValue === 'false') val = false;
    else if (cValue === 'true') val = true;
    else if (cValue === 'null') val = null;
    else if (cValue === '' || cValue === 'emptyStr') val = '';
    else if (cValue === 'emptyArr' || cValue === '[]') val = [];
    else if (cValue === 'emptyObj' || cValue === '{}') val = {};
    else if (cValue === 'noopFunc') val = function() {};
    else if (cValue === 'trueFunc') val = function() { return true; };
    else if (cValue === 'falseFunc') val = function() { return false; };
    else if (/^-?\d+$/.test(cValue)) {
        var n = parseInt(cValue, 10);
        if (Math.abs(n) <= 32767) val = n; else return;
    } else return;
    var parts = String(chain).split('.');
    var owner = window;
    for (var i = 0; i < parts.length - 1; i++) {
        var p = parts[i];
        if (!(p in owner) || owner[p] === null || typeof owner[p] !== 'object') return;
        owner = owner[p];
    }
    var prop = parts[parts.length - 1];
    try {
        Object.defineProperty(owner, prop, {
            configurable: false,
            enumerable: true,
            get: function() { return val; },
            set: function() {}
        });
    } catch (_) {}
}"#,
        ),
        make_js_resource(
            "abort-on-property-read.js",
            &["aopr.js", "ubo-aopr.js", "aopr", "abort-on-property-read"],
            r#"function abortOnPropertyRead(chain) {
    if (!chain) return;
    var magic = '__sf_abrt_' + Math.random().toString(36).slice(2);
    var abort = function() { throw new ReferenceError(magic); };
    var parts = String(chain).split('.');
    var bindProp = function(owner, idx) {
        var prop = parts[idx];
        if (idx === parts.length - 1) {
            try {
                Object.defineProperty(owner, prop, { configurable: true, get: abort, set: function() {} });
            } catch (_) {}
            return;
        }
        var cur = owner[prop];
        if (cur && typeof cur === 'object') { bindProp(cur, idx + 1); return; }
        var desc = Object.getOwnPropertyDescriptor(owner, prop);
        if (desc && !desc.configurable) return;
        try {
            Object.defineProperty(owner, prop, {
                configurable: true,
                get: function() { return cur; },
                set: function(v) { cur = v; if (v && typeof v === 'object') bindProp(v, idx + 1); }
            });
        } catch (_) {}
    };
    bindProp(window, 0);
    var prevOnError = window.onerror;
    window.onerror = function(msg) {
        if (typeof msg === 'string' && msg.indexOf(magic) !== -1) return true;
        return typeof prevOnError === 'function' ? prevOnError.apply(this, arguments) : false;
    };
}"#,
        ),
        make_js_resource(
            "abort-on-property-write.js",
            &["aopw.js", "ubo-aopw.js", "aopw", "abort-on-property-write"],
            r#"function abortOnPropertyWrite(chain) {
    if (!chain) return;
    var magic = '__sf_abrt_' + Math.random().toString(36).slice(2);
    var parts = String(chain).split('.');
    var owner = window;
    for (var i = 0; i < parts.length - 1; i++) {
        var p = parts[i];
        if (!owner[p] || typeof owner[p] !== 'object') return;
        owner = owner[p];
    }
    var prop = parts[parts.length - 1];
    var cur = owner[prop];
    try {
        Object.defineProperty(owner, prop, {
            configurable: true,
            get: function() { return cur; },
            set: function() { throw new ReferenceError(magic); }
        });
    } catch (_) {}
    var prevOnError = window.onerror;
    window.onerror = function(msg) {
        if (typeof msg === 'string' && msg.indexOf(magic) !== -1) return true;
        return typeof prevOnError === 'function' ? prevOnError.apply(this, arguments) : false;
    };
}"#,
        ),
        make_js_resource(
            "abort-current-inline-script.js",
            &["acis.js", "ubo-acis.js", "acis", "abort-current-inline-script"],
            r#"function abortCurrentInlineScript(prop, needle) {
    if (!prop) return;
    var magic = '__sf_acis_' + Math.random().toString(36).slice(2);
    var needleStr = needle ? String(needle) : '';
    var parts = String(prop).split('.');
    var owner = window;
    for (var i = 0; i < parts.length - 1; i++) {
        if (!owner[parts[i]] || typeof owner[parts[i]] !== 'object') return;
        owner = owner[parts[i]];
    }
    var leaf = parts[parts.length - 1];
    var cur = owner[leaf];
    var check = function() {
        var cs = document.currentScript;
        if (cs && cs.src === '' && (!needleStr || (cs.textContent || '').indexOf(needleStr) !== -1)) {
            throw new ReferenceError(magic);
        }
    };
    try {
        Object.defineProperty(owner, leaf, {
            configurable: true,
            get: function() { check(); return cur; },
            set: function(v) { check(); cur = v; }
        });
    } catch (_) {}
    var prevOnError = window.onerror;
    window.onerror = function(msg) {
        if (typeof msg === 'string' && msg.indexOf(magic) !== -1) return true;
        return typeof prevOnError === 'function' ? prevOnError.apply(this, arguments) : false;
    };
}"#,
        ),
        make_js_resource(
            "addEventListener-defuser.js",
            &["aeld.js", "prevent-addEventListener.js", "ubo-aeld.js", "aeld"],
            r#"function addEventListenerDefuser(typeNeedle, fnNeedle) {
    var orig = EventTarget.prototype.addEventListener;
    if (typeof orig !== 'function') return;
    var tStr = typeNeedle ? String(typeNeedle) : '';
    var fStr = fnNeedle ? String(fnNeedle) : '';
    EventTarget.prototype.addEventListener = function(type, listener) {
        var tMatch = !tStr || String(type).indexOf(tStr) !== -1;
        var s = '';
        try { s = typeof listener === 'function' ? Function.prototype.toString.call(listener) : String(listener); } catch (_) {}
        var fMatch = !fStr || s.indexOf(fStr) !== -1;
        if (tMatch && fMatch) return;
        return orig.apply(this, arguments);
    };
}"#,
        ),
        make_js_resource(
            "no-setTimeout-if.js",
            &["nostif.js", "prevent-setTimeout.js", "ubo-nostif.js", "nostif"],
            r#"function noSetTimeoutIf(needle, delayArg) {
    var orig = window.setTimeout;
    var nStr = needle ? String(needle) : '';
    var dNum = (delayArg !== undefined && delayArg !== '') ? parseInt(delayArg, 10) : NaN;
    window.setTimeout = function(cb, delay) {
        var s = '';
        try { s = typeof cb === 'function' ? Function.prototype.toString.call(cb) : String(cb); } catch (_) {}
        var nMatch = !nStr || s.indexOf(nStr) !== -1;
        var dMatch = isNaN(dNum) || delay === dNum;
        if (nMatch && dMatch) return orig.call(window, function() {}, delay);
        return orig.apply(this, arguments);
    };
}"#,
        ),
        make_js_resource(
            "no-setInterval-if.js",
            &["nosiif.js", "prevent-setInterval.js", "ubo-nosiif.js", "nosiif"],
            r#"function noSetIntervalIf(needle, delayArg) {
    var orig = window.setInterval;
    var nStr = needle ? String(needle) : '';
    var dNum = (delayArg !== undefined && delayArg !== '') ? parseInt(delayArg, 10) : NaN;
    window.setInterval = function(cb, delay) {
        var s = '';
        try { s = typeof cb === 'function' ? Function.prototype.toString.call(cb) : String(cb); } catch (_) {}
        var nMatch = !nStr || s.indexOf(nStr) !== -1;
        var dMatch = isNaN(dNum) || delay === dNum;
        if (nMatch && dMatch) return orig.call(window, function() {}, delay);
        return orig.apply(this, arguments);
    };
}"#,
        ),
        make_js_resource(
            "no-fetch-if.js",
            &["prevent-fetch.js", "ubo-no-fetch-if.js"],
            r#"function noFetchIf(needle) {
    if (typeof window.fetch !== 'function') return;
    var orig = window.fetch;
    var nStr = needle ? String(needle) : '';
    window.fetch = function(input) {
        var u = '';
        try { u = typeof input === 'string' ? input : (input && input.url ? String(input.url) : String(input)); } catch (_) {}
        if (!nStr || u.indexOf(nStr) !== -1) {
            return Promise.resolve(new Response('', { status: 200, statusText: 'OK' }));
        }
        return orig.apply(this, arguments);
    };
}"#,
        ),
        make_js_resource(
            "remove-attr.js",
            &["ra.js", "ubo-ra.js", "ra"],
            r#"function removeAttr(attrsArg, selectorArg) {
    if (!attrsArg) return;
    var attrs = String(attrsArg).split('|').map(function(s) { return s.trim(); }).filter(Boolean);
    if (!attrs.length) return;
    var sel = selectorArg ? String(selectorArg) : '[' + attrs.join('],[') + ']';
    var sweep = function() {
        try {
            var nodes = document.querySelectorAll(sel);
            for (var i = 0; i < nodes.length; i++) {
                for (var j = 0; j < attrs.length; j++) nodes[i].removeAttribute(attrs[j]);
            }
        } catch (_) {}
    };
    sweep();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sweep, { once: true });
    }
}"#,
        ),
        make_js_resource(
            "remove-class.js",
            &["rc.js", "ubo-rc.js", "rc"],
            r#"function removeClass(classesArg, selectorArg) {
    if (!classesArg) return;
    var classes = String(classesArg).split('|').map(function(s) { return s.trim(); }).filter(Boolean);
    if (!classes.length) return;
    var sel = selectorArg ? String(selectorArg) : '.' + classes.join(',.');
    var sweep = function() {
        try {
            var nodes = document.querySelectorAll(sel);
            for (var i = 0; i < nodes.length; i++) {
                for (var j = 0; j < classes.length; j++) nodes[i].classList.remove(classes[j]);
            }
        } catch (_) {}
    };
    sweep();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sweep, { once: true });
    }
}"#,
        ),
    ]
}

fn attach_default_resources(engine: &mut Engine) {
    engine.use_resources(default_scriptlet_resources());
}

fn request_type_from_code(code: jint) -> &'static str {
    match code {
        1 => "script",
        2 => "stylesheet",
        3 => "image",
        4 => "font",
        5 => "media",
        6 => "xmlhttprequest",
        7 => "subdocument",
        8 => "document",
        _ => "other",
    }
}

fn is_safe_css_selector(sel: &str) -> bool {
    let trimmed = sel.trim();
    if trimmed.is_empty() || trimmed.len() > 2048 {
        return false;
    }
    if trimmed.starts_with('@') || trimmed.starts_with("+js(") {
        return false;
    }
    if trimmed.contains('{')
        || trimmed.contains('}')
        || trimmed.contains(';')
        || trimmed.contains("/*")
        || trimmed.contains("*/")
        || trimmed.contains('\n')
        || trimmed.contains('\r')
    {
        return false;
    }
    let lower = trimmed.to_ascii_lowercase();
    if lower.contains("</style")
        || lower.contains(":has-text(")
        || lower.contains(":-abp-")
        || lower.contains(":upward(")
        || lower.contains(":style(")
        || lower.contains(":xpath(")
        || lower.contains(":matches-path(")
        || lower.contains(":matches-css")
        || lower.contains(":min-text-length(")
        || lower.contains(":watch-attr(")
        || lower.contains(":remove(")
    {
        return false;
    }
    true
}

fn format_selectors_css<'a, I>(selectors: I) -> String
where
    I: IntoIterator<Item = &'a str>,
{
    let mut safe: Vec<&str> = selectors
        .into_iter()
        .map(str::trim)
        .filter(|s| is_safe_css_selector(s))
        .collect();
    safe.sort_unstable();
    safe.dedup();

    let mut css = String::with_capacity(safe.len().saturating_mul(48));
    // Emit one rule per selector so a single unsupported selector cannot invalidate siblings
    for sel in safe {
        css.push_str(sel);
        css.push_str("{display:none!important}\n");
    }
    css
}

fn format_cosmetic_json(engine: &Engine, url: &str) -> String {
    let resources = engine.url_cosmetic_resources(url);
    let css = format_selectors_css(resources.hide_selectors.iter().map(String::as_str));

    let mut exceptions: Vec<String> = resources.exceptions.into_iter().collect();
    exceptions.sort_unstable();

    let payload = CosmeticPayload {
        css,
        injected_script: resources.injected_script,
        generichide: resources.generichide,
        exceptions,
    };

    serde_json::to_string(&payload).unwrap_or_else(|_| CosmeticPayload::empty_json())
}

fn add_rules_chunk(filter_set: &mut FilterSet, text: &str) {
    let opts = ParseOptions::default();
    for line in text.lines() {
        let trimmed = line.trim();
        if !trimmed.is_empty() && !trimmed.starts_with('!') && !trimmed.starts_with('[') {
            let _ = filter_set.add_filter(trimmed, opts);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCreateFromRules(
    mut env: JNIEnv,
    _class: JClass,
    rules_text: JString,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let mut filter_set = FilterSet::new(false);
        {
            let rules_jstr = match env.get_string(&rules_text) {
                Ok(s) => s,
                Err(_) => return 0,
            };
            let rules_cow: Cow<str> = (&rules_jstr).into();
            add_rules_chunk(&mut filter_set, &rules_cow);
        }
        let mut engine = Engine::from_filter_set(filter_set, true);
        attach_default_resources(&mut engine);
        Box::into_raw(Box::new(engine)) as jlong
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCreateFromFiles(
    mut env: JNIEnv,
    _class: JClass,
    starter_text: JString,
    file_paths: JObjectArray,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let mut filter_set = FilterSet::new(false);
        if !starter_text.is_null() {
            if let Ok(starter_jstr) = env.get_string(&starter_text) {
                let starter_cow: Cow<str> = (&starter_jstr).into();
                add_rules_chunk(&mut filter_set, &starter_cow);
            }
        }
        if !file_paths.is_null() {
            let len = env.get_array_length(&file_paths).unwrap_or(0);
            for i in 0..len {
                if let Ok(elem) = env.get_object_array_element(&file_paths, i) {
                    if !elem.is_null() {
                        let path_jstr = JString::from(elem);
                        let maybe_path: Option<String> = env.get_string(&path_jstr).ok().map(Into::into);
                        if let Some(path_str) = maybe_path {
                            if let Ok(contents) = fs::read_to_string(&path_str) {
                                add_rules_chunk(&mut filter_set, &contents);
                            }
                        }
                    }
                }
            }
        }
        let mut engine = Engine::from_filter_set(filter_set, true);
        attach_default_resources(&mut engine);
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
            Ok(()) => {
                drop(bytes);
                attach_default_resources(&mut engine);
                Box::into_raw(Box::new(engine)) as jlong
            }
            Err(_) => 0,
        }
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeDeserializeFromFile(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let path_str: String = match env.get_string(&path) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };
        let bytes = match fs::read(&path_str) {
            Ok(b) if !b.is_empty() => b,
            _ => return 0,
        };
        let mut engine = Engine::new(true);
        match engine.deserialize(&bytes) {
            Ok(()) => {
                drop(bytes);
                attach_default_resources(&mut engine);
                Box::into_raw(Box::new(engine)) as jlong
            }
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
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeSerializeToFile(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) -> jboolean {
    if handle == 0 {
        return JNI_FALSE;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let target_path: String = match env.get_string(&path) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };
        let engine = unsafe { &*(handle as *const Engine) };
        let bytes = match engine.serialize() {
            Ok(b) if !b.is_empty() => b,
            _ => return JNI_FALSE,
        };
        let target = Path::new(&target_path);
        let tmp_path = format!("{}.tmp", target_path);
        if fs::write(&tmp_path, &bytes).is_err() {
            let _ = fs::remove_file(&tmp_path);
            return JNI_FALSE;
        }
        drop(bytes);
        let renamed = fs::rename(&tmp_path, target).is_ok()
            || (fs::remove_file(target).is_ok() && fs::rename(&tmp_path, target).is_ok());
        if !renamed {
            let _ = fs::remove_file(&tmp_path);
            return JNI_FALSE;
        }
        JNI_TRUE
    }))
    .unwrap_or(JNI_FALSE)
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
        let url_jstr = match env.get_string(&url) {
            Ok(s) => s,
            Err(_) => return JNI_FALSE,
        };
        let source_jstr = match env.get_string(&source_url) {
            Ok(s) => s,
            Err(_) => return JNI_FALSE,
        };
        let req_type_jstr = match env.get_string(&request_type) {
            Ok(s) => s,
            Err(_) => return JNI_FALSE,
        };
        let url_cow: Cow<str> = (&url_jstr).into();
        let source_cow: Cow<str> = (&source_jstr).into();
        let req_type_cow: Cow<str> = (&req_type_jstr).into();

        let request = match Request::new(&url_cow, &source_cow, &req_type_cow) {
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
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeCheckNetworkRequestCode(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    source_url: JString,
    request_type_code: jint,
) -> jboolean {
    if handle == 0 {
        return JNI_FALSE;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let url_jstr = match env.get_string(&url) {
            Ok(s) => s,
            Err(_) => return JNI_FALSE,
        };
        let source_jstr = match env.get_string(&source_url) {
            Ok(s) => s,
            Err(_) => return JNI_FALSE,
        };
        let url_cow: Cow<str> = (&url_jstr).into();
        let source_cow: Cow<str> = (&source_jstr).into();
        let req_type = request_type_from_code(request_type_code);

        let request = match Request::new(&url_cow, &source_cow, req_type) {
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
                    let url_cow: Cow<str> = (&s).into();
                    let engine = unsafe { &*(handle as *const Engine) };
                    format_cosmetic_json(engine, &url_cow)
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
pub extern "system" fn Java_app_overbay_swiftfox_features_adblock_NativeAdblockBridge_nativeHiddenClassIdSelectors(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    classes_json: JString,
    ids_json: JString,
    exceptions_json: JString,
) -> jstring {
    catch_unwind(AssertUnwindSafe(|| {
        let css = if handle == 0 {
            String::new()
        } else {
            let classes_jstr = env.get_string(&classes_json).ok();
            let ids_jstr = env.get_string(&ids_json).ok();
            let exceptions_jstr = env.get_string(&exceptions_json).ok();

            let classes_cow: Cow<str> = classes_jstr.as_ref().map(Into::into).unwrap_or_default();
            let ids_cow: Cow<str> = ids_jstr.as_ref().map(Into::into).unwrap_or_default();
            let exceptions_cow: Cow<str> = exceptions_jstr
                .as_ref()
                .map(Into::into)
                .unwrap_or_default();

            let classes: Vec<String> = serde_json::from_str(&classes_cow).unwrap_or_default();
            let ids: Vec<String> = serde_json::from_str(&ids_cow).unwrap_or_default();
            let exceptions: HashSet<String> =
                serde_json::from_str(&exceptions_cow).unwrap_or_default();

            if classes.is_empty() && ids.is_empty() {
                String::new()
            } else {
                let engine = unsafe { &*(handle as *const Engine) };
                let matched = engine.hidden_class_id_selectors(&classes, &ids, &exceptions);
                format_selectors_css(matched.iter().map(String::as_str))
            }
        };

        match env.new_string(&css) {
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
