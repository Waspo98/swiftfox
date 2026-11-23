(function (runtimeKey, bridgeToken) {
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  var fetchMethodSet = false;
  var domListenerAttached = false;
  var nativeDarkCache = null;
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

  function parseRgba(colorStr) {
    if (!colorStr || typeof colorStr !== 'string' || colorStr === 'transparent') {
      return null;
    }
    var m = colorStr.match(
      /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:[\s,/]+([\d.]+%?))?\s*\)$/i
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

  function checkElementDarkBg(el) {
    if (!el || typeof window.getComputedStyle !== 'function') return null;
    try {
      var bg = window.getComputedStyle(el).backgroundColor;
      var parsed = parseRgba(bg);
      if (!parsed || parsed.a <= 0.5) return null;
      var luminance = (0.2126 * parsed.r + 0.7152 * parsed.g + 0.0722 * parsed.b) / 255;
      return luminance < 0.22;
    } catch (e) {
      return null;
    }
  }

  function detectNativeDarkRaw() {
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

  function enableDarkReader(config) {
    var brightness = typeof config.brightness === 'number' ? config.brightness : 100;
    var contrast = typeof config.contrast === 'number' ? config.contrast : 100;
    var sepia = typeof config.sepia === 'number' ? config.sepia : 0;
    DarkReader.enable({
      mode: 1,
      brightness: brightness,
      contrast: contrast,
      sepia: sepia,
      darkSchemeBackgroundColor: config.amoled ? '#000000' : '#18161b',
      darkSchemeTextColor: config.amoled ? '#f3eef7' : '#ece7f0'
    });
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

  function isDarkReaderActive() {
    return typeof DarkReader !== 'undefined' &&
      typeof DarkReader.isEnabled === 'function' &&
      DarkReader.isEnabled();
  }

  function evaluateNativeDark(restoreIfDisabled) {
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
    DarkReader.disable();
    var detected = detectNativeDarkRaw();
    saveCache(detected);
    if (restoreIfDisabled && !detected && lastConfig && lastConfig.enabled) {
      enableDarkReader(lastConfig);
      appliedSignature = buildConfigSignature(lastConfig);
    }
    return detected;
  }

  function scheduleDomReadyRecheck() {
    if (domListenerAttached || isDomSettled()) return;
    domListenerAttached = true;
    document.addEventListener('DOMContentLoaded', function () {
      domListenerAttached = false;
      if (lastConfig) {
        api.apply(lastConfig);
      }
    }, { once: true });
  }

  var api = {
    apply: function (config) {
      if (!config || typeof config !== 'object') return;
      lastConfig = config;

      if (typeof DarkReader === 'undefined') {
        return;
      }

      if (!fetchMethodSet && typeof DarkReader.setFetchMethod === 'function' &&
          typeof window.fetch === 'function') {
        DarkReader.setFetchMethod(window.fetch.bind(window));
        fetchMethodSet = true;
      }

      if (!config.enabled) {
        nativeDarkCache = null;
        appliedSignature = null;
        DarkReader.disable();
        notifyStatus(false);
        return;
      }

      if (!document.documentElement) {
        scheduleDomReadyRecheck();
        return;
      }

      if (config.skipNativeDark && !config.forceOverride) {
        // Wait for DOMContentLoaded before enabling Dark Reader if we're still loading
        // and don't yet have a body background — avoids enabling early only to flash white later.
        if (!isDomSettled() && !document.body) {
          scheduleDomReadyRecheck();
          if (hasExplicitDarkColorScheme(getMetaColorSchemeContent())) {
            appliedSignature = null;
            DarkReader.disable();
            notifyStatus(true);
          }
          return;
        }
        if (!isDomSettled()) {
          scheduleDomReadyRecheck();
        }
        if (evaluateNativeDark(false)) {
          appliedSignature = null;
          DarkReader.disable();
          notifyStatus(true);
          return;
        }
      }

      var nextSignature = buildConfigSignature(config);
      if (isDarkReaderActive() && appliedSignature === nextSignature) {
        notifyStatus(false);
        return;
      }

      try {
        enableDarkReader(config);
        appliedSignature = nextSignature;
        notifyStatus(false);
      } catch (e) {
        appliedSignature = null;
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
