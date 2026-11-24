(function (runtimeKey, bridgeToken) {
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  var PREPAINT_STYLE_ID = 'swiftfox-prepaint-dark';
  var AMOLED_MAX_CHROMA = 20;
  var AMOLED_BG_MAX_LUMINANCE = 0.11;
  var AMOLED_TEXT_MIN_LUMINANCE = 0.78;

  var fetchMethodSet = false;
  var domListenerAttached = false;
  var loadListenerAttached = false;
  var amoledLoadListenerAttached = false;
  var domRecheckTimer = null;
  var nativeDarkCache = null;
  var nativeDarkObserver = null;
  var nativeDarkObserverTimer = null;
  var nativeDarkMutationDebounce = null;
  var amoledHeadObserver = null;
  var amoledSweepScheduled = false;
  var hookedVarStyleObj = null;
  var hookedVarOrigSetProperty = null;
  var lastConfig = null;
  var appliedSignature = null;

  function buildConfigSignature(config) {
    var brightness = typeof config.brightness === 'number' ? config.brightness : 100;
    var contrast = typeof config.contrast === 'number' ? config.contrast : 100;
    var sepia = typeof config.sepia === 'number' ? config.sepia : 0;
    return [!!config.amoled, brightness, contrast, sepia].join('|');
  }

  function hasExplicitDarkColorScheme(raw) {
    if (!raw || typeof raw !== 'string') return false;
    var tokens = raw.trim().toLowerCase().split(/[\s,]+/);
    return tokens.indexOf('dark') !== -1 && tokens.indexOf('light') === -1;
  }

  function supportsBothColorSchemes(raw) {
    if (!raw || typeof raw !== 'string') return false;
    var tokens = raw.trim().toLowerCase().split(/[\s,]+/);
    return tokens.indexOf('dark') !== -1 && tokens.indexOf('light') !== -1;
  }

  function getMetaColorSchemeContent() {
    try {
      var metas = document.querySelectorAll('meta[name]');
      for (var i = 0; i < metas.length; i++) {
        var name = metas[i].getAttribute('name');
        if (name && name.toLowerCase() === 'color-scheme') {
          return metas[i].getAttribute('content') || '';
        }
      }
    } catch (e) {}
    return '';
  }

  function parseColor(colorStr) {
    if (!colorStr || typeof colorStr !== 'string') return null;
    var trimmed = colorStr.trim().toLowerCase();
    if (!trimmed || trimmed === 'transparent') return null;

    if (trimmed.charAt(0) === '#') {
      var hex = trimmed.slice(1);
      if (hex.length === 3 || hex.length === 4) {
        var r3 = parseInt(hex.charAt(0) + hex.charAt(0), 16);
        var g3 = parseInt(hex.charAt(1) + hex.charAt(1), 16);
        var b3 = parseInt(hex.charAt(2) + hex.charAt(2), 16);
        var a3 = hex.length === 4 ? parseInt(hex.charAt(3) + hex.charAt(3), 16) / 255 : 1;
        if (isNaN(r3) || isNaN(g3) || isNaN(b3) || isNaN(a3)) return null;
        return { r: r3, g: g3, b: b3, a: a3 };
      }
      if (hex.length === 6 || hex.length === 8) {
        var r6 = parseInt(hex.slice(0, 2), 16);
        var g6 = parseInt(hex.slice(2, 4), 16);
        var b6 = parseInt(hex.slice(4, 6), 16);
        var a6 = hex.length === 8 ? parseInt(hex.slice(6, 8), 16) / 255 : 1;
        if (isNaN(r6) || isNaN(g6) || isNaN(b6) || isNaN(a6)) return null;
        return { r: r6, g: g6, b: b6, a: a6 };
      }
      return null;
    }

    var m = trimmed.match(
      /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:[\s,/]+([\d.]+%?))?\s*\)$/
    );
    if (!m) return null;
    var r = parseFloat(m[1]);
    var g = parseFloat(m[2]);
    var b = parseFloat(m[3]);
    var a = 1;
    if (m[4] !== undefined) {
      a = m[4].slice(-1) === '%' ? parseFloat(m[4]) / 100 : parseFloat(m[4]);
    }
    if (isNaN(r) || isNaN(g) || isNaN(b) || isNaN(a)) return null;
    return { r: r, g: g, b: b, a: a };
  }

  function formatAlpha(a) {
    var clamped = Math.max(0, Math.min(1, a));
    return String(Math.round(clamped * 1000) / 1000);
  }

  function normalizeAmoledVarValue(prop, value, config) {
    if (!config || !config.amoled || typeof prop !== 'string' || typeof value !== 'string') {
      return value;
    }
    var isBg = prop.indexOf('--darkreader-background-') === 0 ||
      prop === '--darkreader-neutral-background';
    var isText = !isBg && (
      prop.indexOf('--darkreader-text-') === 0 ||
      prop === '--darkreader-neutral-text'
    );
    if (!isBg && !isText) return value;

    var parsed = parseColor(value);
    if (!parsed || parsed.a <= 0) return value;

    var maxC = Math.max(parsed.r, parsed.g, parsed.b);
    var minC = Math.min(parsed.r, parsed.g, parsed.b);
    if (maxC - minC > AMOLED_MAX_CHROMA) return value;

    var luminance = (0.2126 * parsed.r + 0.7152 * parsed.g + 0.0722 * parsed.b) / 255;

    if (isBg) {
      if (luminance <= AMOLED_BG_MAX_LUMINANCE) {
        return parsed.a < 0.999
          ? 'rgba(0, 0, 0, ' + formatAlpha(parsed.a) + ')'
          : '#000000';
      }
      return value;
    }

    var brightness = typeof config.brightness === 'number' ? config.brightness : 100;
    var sepia = typeof config.sepia === 'number' ? config.sepia : 0;
    if (sepia === 0 && brightness >= 100 && luminance >= AMOLED_TEXT_MIN_LUMINANCE) {
      return parsed.a < 0.999
        ? 'rgba(255, 255, 255, ' + formatAlpha(parsed.a) + ')'
        : '#ffffff';
    }
    return value;
  }

  function findDarkReaderVariablesRootRule() {
    try {
      var styleEl = document.querySelector('style.darkreader--variables');
      var sheet = styleEl && styleEl.sheet;
      var rules = sheet && sheet.cssRules;
      if (!rules) return null;
      for (var i = 0; i < rules.length; i++) {
        if (rules[i].selectorText === ':root' && rules[i].style) {
          return rules[i];
        }
      }
    } catch (e) {}
    return null;
  }

  function patchAmoledVariablesRule(config) {
    if (!config || !config.amoled) return;
    var rootRule = findDarkReaderVariablesRootRule();
    if (!rootRule || !rootRule.style) return;
    var styleObj = rootRule.style;

    if (hookedVarStyleObj !== styleObj && typeof styleObj.setProperty === 'function') {
      var origSet = styleObj.setProperty;
      hookedVarStyleObj = styleObj;
      hookedVarOrigSetProperty = origSet;
      try {
        Object.defineProperty(styleObj, 'setProperty', {
          configurable: true,
          writable: true,
          value: function (prop, val, priority) {
            var nextVal = normalizeAmoledVarValue(prop, val, lastConfig);
            if (priority !== undefined) {
              return origSet.call(this, prop, nextVal, priority);
            }
            return origSet.call(this, prop, nextVal);
          }
        });
      } catch (e) {}
    }

    var rawSet = (hookedVarStyleObj === styleObj && hookedVarOrigSetProperty)
      ? hookedVarOrigSetProperty
      : styleObj.setProperty;
    if (typeof rawSet !== 'function') return;

    var propNames = [];
    for (var i = 0; i < styleObj.length; i++) {
      propNames.push(styleObj[i]);
    }
    for (var j = 0; j < propNames.length; j++) {
      var name = propNames[j];
      if (name && name.indexOf('--darkreader-') === 0) {
        var cur = styleObj.getPropertyValue(name);
        var normalized = normalizeAmoledVarValue(name, cur, config);
        if (normalized !== cur) {
          rawSet.call(styleObj, name, normalized);
        }
      }
    }
  }

  function scheduleAmoledSweep() {
    if (amoledSweepScheduled) return;
    amoledSweepScheduled = true;
    var run = function () {
      amoledSweepScheduled = false;
      if (lastConfig && lastConfig.enabled && lastConfig.amoled && isDarkReaderActive()) {
        patchAmoledVariablesRule(lastConfig);
      }
    };
    if (typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(run);
    } else {
      setTimeout(run, 16);
    }
  }

  function teardownAmoledVarOverride() {
    amoledSweepScheduled = false;
    if (amoledHeadObserver) {
      try { amoledHeadObserver.disconnect(); } catch (e) {}
      amoledHeadObserver = null;
    }
    if (hookedVarStyleObj && hookedVarOrigSetProperty) {
      try {
        delete hookedVarStyleObj.setProperty;
      } catch (e) {}
    }
    hookedVarStyleObj = null;
    hookedVarOrigSetProperty = null;
  }

  function installAmoledVarOverride(config) {
    teardownAmoledVarOverride();
    if (!config || !config.amoled) return;
    patchAmoledVariablesRule(config);
    var headOrRoot = document.head || document.documentElement;
    if (typeof MutationObserver === 'function' && headOrRoot) {
      try {
        amoledHeadObserver = new MutationObserver(function () {
          scheduleAmoledSweep();
        });
        amoledHeadObserver.observe(headOrRoot, {
          childList: true,
          subtree: !document.head
        });
      } catch (e) {
        amoledHeadObserver = null;
      }
    }
    if (!amoledLoadListenerAttached && document.readyState !== 'complete') {
      amoledLoadListenerAttached = true;
      window.addEventListener('load', function () {
        amoledLoadListenerAttached = false;
        if (lastConfig && lastConfig.enabled && lastConfig.amoled && isDarkReaderActive()) {
          patchAmoledVariablesRule(lastConfig);
        }
      }, { once: true });
    }
  }

  function checkElementDarkBg(el) {
    if (!el || typeof window.getComputedStyle !== 'function') return null;
    try {
      var bg = window.getComputedStyle(el).backgroundColor;
      var parsed = parseColor(bg);
      if (!parsed || parsed.a <= 0.5) return null;
      var luminance = (0.2126 * parsed.r + 0.7152 * parsed.g + 0.0722 * parsed.b) / 255;
      return luminance < 0.22;
    } catch (e) {
      return null;
    }
  }

  function detectNativeDarkRaw() {
    var prepaintEl = document.getElementById(PREPAINT_STYLE_ID);
    var prevPrepaintDisabled = prepaintEl ? !!prepaintEl.disabled : false;
    var prevSheetDisabled = (prepaintEl && prepaintEl.sheet) ? !!prepaintEl.sheet.disabled : false;
    if (prepaintEl) {
      prepaintEl.disabled = true;
      if (prepaintEl.sheet) {
        prepaintEl.sheet.disabled = true;
      }
    }
    try {
      var root = document.documentElement;
      var metaScheme = getMetaColorSchemeContent();
      if (hasExplicitDarkColorScheme(metaScheme)) {
        return true;
      }

      var computedScheme = '';
      if (root && typeof window.getComputedStyle === 'function') {
        try {
          computedScheme = window.getComputedStyle(root).colorScheme || '';
        } catch (e) {}
      }
      if (hasExplicitDarkColorScheme(computedScheme)) {
        return true;
      }

      var bodyBgDark = checkElementDarkBg(document.body);
      if (bodyBgDark !== null) {
        return bodyBgDark;
      }

      var rootBgDark = checkElementDarkBg(root);
      if (rootBgDark !== null) {
        return rootBgDark;
      }

      if ((supportsBothColorSchemes(metaScheme) || supportsBothColorSchemes(computedScheme)) &&
          typeof window.matchMedia === 'function' &&
          window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return true;
      }

      return false;
    } finally {
      if (prepaintEl) {
        prepaintEl.disabled = prevPrepaintDisabled;
        if (prepaintEl.sheet) {
          prepaintEl.sheet.disabled = prevSheetDisabled;
        }
      }
    }
  }

  function currentOriginKey() {
    try {
      return window.location ? window.location.origin : '';
    } catch (e) {
      return '';
    }
  }

  function isDomSettled() {
    return document.readyState === 'interactive' || document.readyState === 'complete';
  }

  function isCacheValid() {
    if (!nativeDarkCache) return false;
    if (nativeDarkCache.origin !== currentOriginKey()) return false;
    if (!nativeDarkCache.hasBody || !document.body) return false;
    // Once measured after DOMContentLoaded ('interactive' or 'complete'), keep cache stable
    // so onPageFinished and slider tweaks never flash white by disabling Dark Reader.
    return nativeDarkCache.settled || !isDomSettled();
  }

  function saveCache(value) {
    if (!document.body) return;
    nativeDarkCache = {
      origin: currentOriginKey(),
      hasBody: true,
      settled: isDomSettled(),
      value: !!value
    };
  }

  function clearPrepaintStyle() {
    try {
      var el = document.getElementById(PREPAINT_STYLE_ID);
      if (el && el.parentNode) {
        el.parentNode.removeChild(el);
      }
    } catch (e) {}
  }

  function enableDarkReader(config) {
    var brightness = typeof config.brightness === 'number' ? config.brightness : 100;
    var contrast = typeof config.contrast === 'number' ? config.contrast : 100;
    var sepia = typeof config.sepia === 'number' ? config.sepia : 0;
    // Dark Reader 4.9's incremental fast-path only refreshes its internal color cache
    // without rewriting injected stylesheets when background/brightness/contrast/sepia change.
    // Disable first whenever updating an already-active instance so AMOLED & sliders apply live.
    teardownAmoledVarOverride();
    if (isDarkReaderActive()) {
      DarkReader.disable();
    }
    DarkReader.enable({
      mode: 1,
      brightness: brightness,
      contrast: contrast,
      sepia: sepia,
      darkSchemeBackgroundColor: config.amoled ? '#000000' : '#181818',
      darkSchemeTextColor: config.amoled ? '#ffffff' : '#e8e8e8'
    });
    if (config.amoled) {
      installAmoledVarOverride(config);
    }
    clearPrepaintStyle();
  }

  function notifyStatus(skippedNativeDark) {
    try {
      var ch = window.SwiftfoxNativeChannel;
      if (bridgeToken && ch && typeof ch.postMessage === 'function') {
        ch.postMessage(
          JSON.stringify({
            channel: 'darkReader',
            token: bridgeToken,
            json: JSON.stringify({
              skippedNativeDark: !!skippedNativeDark
            })
          })
        );
      }
    } catch (e) {}
  }

  function disableDarkReaderSafe() {
    teardownAmoledVarOverride();
    if (typeof DarkReader !== 'undefined' && typeof DarkReader.disable === 'function') {
      DarkReader.disable();
    }
  }

  function isDarkReaderActive() {
    return typeof DarkReader !== 'undefined' &&
      typeof DarkReader.isEnabled === 'function' &&
      DarkReader.isEnabled();
  }

  function teardownNativeDarkObserver() {
    if (nativeDarkObserver) {
      try { nativeDarkObserver.disconnect(); } catch (e) {}
      nativeDarkObserver = null;
    }
    if (nativeDarkObserverTimer !== null) {
      clearTimeout(nativeDarkObserverTimer);
      nativeDarkObserverTimer = null;
    }
    if (nativeDarkMutationDebounce !== null) {
      clearTimeout(nativeDarkMutationDebounce);
      nativeDarkMutationDebounce = null;
    }
    if (domRecheckTimer !== null) {
      clearTimeout(domRecheckTimer);
      domRecheckTimer = null;
    }
  }

  function attachNativeDarkMutationObserver() {
    if (nativeDarkObserver || typeof MutationObserver !== 'function') return;
    if (!lastConfig || !lastConfig.enabled || !lastConfig.skipNativeDark ||
        lastConfig.forceOverride || lastConfig.amoled) {
      return;
    }
    var attrOpts = {
      attributes: true,
      attributeFilter: ['class', 'data-theme', 'data-color-mode', 'data-bs-theme', 'data-mode']
    };
    try {
      nativeDarkObserver = new MutationObserver(function () {
        if (!lastConfig || !lastConfig.enabled || !lastConfig.skipNativeDark ||
            lastConfig.forceOverride || lastConfig.amoled) {
          teardownNativeDarkObserver();
          return;
        }
        if (nativeDarkMutationDebounce !== null) {
          clearTimeout(nativeDarkMutationDebounce);
        }
        nativeDarkMutationDebounce = setTimeout(function () {
          nativeDarkMutationDebounce = null;
          if (!lastConfig || !lastConfig.enabled || !lastConfig.skipNativeDark ||
              lastConfig.forceOverride || lastConfig.amoled) {
            teardownNativeDarkObserver();
            return;
          }
          nativeDarkCache = null;
          api.apply(lastConfig);
        }, 50);
      });
      if (document.documentElement) {
        nativeDarkObserver.observe(document.documentElement, attrOpts);
      }
      if (document.body) {
        nativeDarkObserver.observe(document.body, attrOpts);
      }
      nativeDarkObserverTimer = setTimeout(function () {
        teardownNativeDarkObserver();
      }, 3000);
    } catch (e) {
      teardownNativeDarkObserver();
    }

    if (!loadListenerAttached && document.readyState !== 'complete') {
      loadListenerAttached = true;
      window.addEventListener('load', function () {
        loadListenerAttached = false;
        setTimeout(function () {
          if (lastConfig && lastConfig.enabled && lastConfig.skipNativeDark &&
              !lastConfig.forceOverride && !lastConfig.amoled &&
              (!nativeDarkCache || !nativeDarkCache.value)) {
            nativeDarkCache = null;
            api.apply(lastConfig);
          }
          teardownNativeDarkObserver();
        }, 0);
      }, { once: true });
    }
  }

  function evaluateNativeDark() {
    if (!isDarkReaderActive()) {
      var raw = detectNativeDarkRaw();
      saveCache(raw);
      return raw;
    }
    if (isCacheValid()) {
      return nativeDarkCache.value;
    }
    // Only disable to re-probe once when transitioning from 'loading' to settled DOM
    appliedSignature = null;
    disableDarkReaderSafe();
    var detected = detectNativeDarkRaw();
    saveCache(detected);
    return detected;
  }

  function scheduleDomReadyRecheck() {
    if (domListenerAttached || isDomSettled()) return;
    domListenerAttached = true;
    document.addEventListener('DOMContentLoaded', function () {
      domListenerAttached = false;
      // Yield one macrotask so page DOMContentLoaded / jQuery.ready handlers that
      // apply native dark classes (e.g. body.darkmode) finish before we probe.
      domRecheckTimer = setTimeout(function () {
        domRecheckTimer = null;
        if (lastConfig) {
          api.apply(lastConfig);
        }
      }, 0);
    }, { once: true });
  }

  var api = {
    apply: function (config) {
      if (!config || typeof config !== 'object') return;
      lastConfig = config;

      if (typeof DarkReader === 'undefined' || typeof DarkReader.enable !== 'function') {
        clearPrepaintStyle();
        return;
      }

      if (!fetchMethodSet && typeof DarkReader.setFetchMethod === 'function' &&
          typeof window.fetch === 'function') {
        DarkReader.setFetchMethod(window.fetch.bind(window));
        fetchMethodSet = true;
      }

      if (!config.enabled) {
        teardownNativeDarkObserver();
        nativeDarkCache = null;
        appliedSignature = null;
        disableDarkReaderSafe();
        clearPrepaintStyle();
        notifyStatus(false);
        return;
      }

      if (!document.documentElement) {
        scheduleDomReadyRecheck();
        return;
      }

      if (config.skipNativeDark && !config.forceOverride && !config.amoled) {
        // Wait for DOMContentLoaded before enabling Dark Reader if we're still loading
        // and don't yet have a body background — avoids enabling early only to flash white later.
        if (!isDomSettled() && !document.body) {
          scheduleDomReadyRecheck();
          if (hasExplicitDarkColorScheme(getMetaColorSchemeContent())) {
            teardownNativeDarkObserver();
            appliedSignature = null;
            disableDarkReaderSafe();
            clearPrepaintStyle();
            notifyStatus(true);
          }
          return;
        }
        if (!isDomSettled()) {
          scheduleDomReadyRecheck();
        }
        if (evaluateNativeDark()) {
          teardownNativeDarkObserver();
          appliedSignature = null;
          disableDarkReaderSafe();
          clearPrepaintStyle();
          notifyStatus(true);
          return;
        }
        attachNativeDarkMutationObserver();
      } else {
        teardownNativeDarkObserver();
        if (config.amoled) {
          nativeDarkCache = null;
        }
      }

      var nextSignature = buildConfigSignature(config);
      if (isDarkReaderActive() && appliedSignature === nextSignature) {
        if (config.amoled) {
          patchAmoledVariablesRule(config);
        }
        clearPrepaintStyle();
        notifyStatus(false);
        return;
      }

      try {
        enableDarkReader(config);
        appliedSignature = nextSignature;
        notifyStatus(false);
      } catch (e) {
        teardownAmoledVarOverride();
        appliedSignature = null;
        clearPrepaintStyle();
        notifyStatus(false);
      }
    }
  };

  try {
    Object.defineProperty(window, runtimeKey, {
      value: api,
      writable: false,
      configurable: false,
      enumerable: false
    });
  } catch (e) {
    window[runtimeKey] = api;
  }
})
