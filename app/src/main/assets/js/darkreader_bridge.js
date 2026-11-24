(function (runtimeKey, bridgeToken, createAmoledController) {
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  var PREPAINT_STYLE_ID = 'swiftfox-prepaint-dark';
  var OBSERVED_THEME_ATTRS = ['class', 'data-theme', 'data-color-mode', 'data-bs-theme', 'data-mode'];
  var DARK_TOKEN_REGEX = /(?:^|[\s_-])(?:dark|night|dim|amoled)(?:mode|theme)?(?:$|[\s_-])|(?:dark|night)(?:mode|theme)/i;

  var fetchMethodSet = false;
  var domListenerAttached = false;
  var loadListenerAttached = false;
  var domRecheckTimer = null;
  var nativeDarkCache = null;
  var probedDarkSignalSignature = null;
  var nativeDarkObserver = null;
  var nativeDarkObserverTimer = null;
  var nativeDarkMutationDebounce = null;
  var lastConfig = null;
  var appliedSignature = null;

  var amoledCtrl = typeof createAmoledController === 'function'
    ? createAmoledController(function () { return lastConfig; }, isDarkReaderActive, true)
    : {
        parseColor: function () { return null; },
        patchVariablesRule: function () {},
        install: function () {},
        teardown: function () {}
      };

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

  function patchAmoledVariablesRule(config) {
    amoledCtrl.patchVariablesRule(config);
  }

  function teardownAmoledVarOverride() {
    amoledCtrl.teardown();
  }

  function installAmoledVarOverride(config) {
    amoledCtrl.install(config);
  }

  function checkElementDarkBg(el) {
    if (!el || typeof window.getComputedStyle !== 'function') return null;
    try {
      var bg = window.getComputedStyle(el).backgroundColor;
      var parsed = amoledCtrl.parseColor(bg);
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

  function extractDarkHintTokens(attrName, rawValue) {
    if (!rawValue || typeof rawValue !== 'string') return '';
    if (attrName === 'class') {
      var parts = rawValue.trim().split(/\s+/);
      var matches = [];
      for (var i = 0; i < parts.length; i++) {
        if (parts[i] && DARK_TOKEN_REGEX.test(parts[i])) {
          matches.push(parts[i].toLowerCase());
        }
      }
      matches.sort();
      return matches.join(' ');
    }
    var trimmed = rawValue.trim().toLowerCase();
    return DARK_TOKEN_REGEX.test(trimmed) ? trimmed : '';
  }

  function buildElementDarkAttrSignature(el) {
    if (!el || typeof el.getAttribute !== 'function') return '';
    var parts = [];
    for (var i = 0; i < OBSERVED_THEME_ATTRS.length; i++) {
      var attr = OBSERVED_THEME_ATTRS[i];
      var hint = extractDarkHintTokens(attr, el.getAttribute(attr));
      if (hint) {
        parts.push(attr + '=' + hint);
      }
    }
    return parts.join(';');
  }

  function currentDarkThemeSignalSignature() {
    var metaDark = hasExplicitDarkColorScheme(getMetaColorSchemeContent()) ? '1' : '0';
    return [
      metaDark,
      buildElementDarkAttrSignature(document.documentElement),
      buildElementDarkAttrSignature(document.body)
    ].join('|');
  }

  function hasNewDarkThemeSignal() {
    var current = currentDarkThemeSignalSignature();
    return current !== '0||' && current !== probedDarkSignalSignature;
  }

  function saveCache(value) {
    probedDarkSignalSignature = currentDarkThemeSignalSignature();
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

  function syncSubframes(config) {
    if (!config || typeof config !== 'object') return;
    try {
      var frames = document.querySelectorAll('iframe');
      if (!frames || frames.length === 0) return;
      var payload = {
        __swiftfoxDarkSync: true,
        config: {
          enabled: !!config.enabled,
          amoled: !!config.amoled,
          brightness: typeof config.brightness === 'number' ? config.brightness : 100,
          contrast: typeof config.contrast === 'number' ? config.contrast : 100,
          sepia: typeof config.sepia === 'number' ? config.sepia : 0,
          skipNativeDark: config.skipNativeDark !== false,
          forceOverride: !!config.forceOverride
        }
      };
      for (var i = 0; i < frames.length; i++) {
        try {
          if (frames[i].contentWindow) {
            frames[i].contentWindow.postMessage(payload, '*');
          }
        } catch (e) {}
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
      attributeFilter: OBSERVED_THEME_ATTRS
    };
    try {
      nativeDarkObserver = new MutationObserver(function () {
        if (!lastConfig || !lastConfig.enabled || !lastConfig.skipNativeDark ||
            lastConfig.forceOverride || lastConfig.amoled) {
          teardownNativeDarkObserver();
          return;
        }
        // Ignore routine non-theme class/attr churn (fonts, hydration, modals) so light sites never flash white.
        if (!hasNewDarkThemeSignal()) {
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
          if (!hasNewDarkThemeSignal()) {
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
            // Only re-probe on window.load if the DOM hadn't settled during the prior probe
            // or a new dark-theme attribute/meta signal appeared late; never flash settled light sites.
            var needsRecheck = !nativeDarkCache || !nativeDarkCache.settled || hasNewDarkThemeSignal();
            if (needsRecheck) {
              nativeDarkCache = null;
              api.apply(lastConfig);
            }
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
        probedDarkSignalSignature = null;
        appliedSignature = null;
        disableDarkReaderSafe();
        clearPrepaintStyle();
        syncSubframes({ enabled: false });
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
            syncSubframes(config);
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
          syncSubframes(config);
          notifyStatus(true);
          return;
        }
        attachNativeDarkMutationObserver();
      } else {
        teardownNativeDarkObserver();
        if (config.amoled) {
          nativeDarkCache = null;
          probedDarkSignalSignature = null;
        }
      }

      var nextSignature = buildConfigSignature(config);
      if (isDarkReaderActive() && appliedSignature === nextSignature) {
        if (config.amoled) {
          patchAmoledVariablesRule(config);
        }
        clearPrepaintStyle();
        syncSubframes(config);
        notifyStatus(false);
        return;
      }

      try {
        enableDarkReader(config);
        appliedSignature = nextSignature;
        syncSubframes(config);
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
