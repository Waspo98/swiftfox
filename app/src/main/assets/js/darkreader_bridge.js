(function () {
  if (window.__swiftfoxDarkReader) {
    return;
  }

  var fetchMethodSet = false;
  var domListenerAttached = false;
  var nativeDarkCache = null;

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

  function currentHref() {
    try {
      return window.location ? window.location.href : '';
    } catch (e) {
      return '';
    }
  }

  function isCacheValid() {
    if (!nativeDarkCache) return false;
    if (nativeDarkCache.url !== currentHref()) return false;
    if (!nativeDarkCache.hasBody || !document.body) return false;
    return nativeDarkCache.readyState === 'complete' ||
      nativeDarkCache.readyState === document.readyState;
  }

  function saveCache(value) {
    if (!document.body) return;
    nativeDarkCache = {
      url: currentHref(),
      hasBody: true,
      readyState: document.readyState || 'loading',
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

  function notifyStatus(active, skippedNativeDark) {
    try {
      if (window.SwiftfoxDarkReaderBridge &&
          typeof window.SwiftfoxDarkReaderBridge.onDarkReaderStatus === 'function') {
        window.SwiftfoxDarkReaderBridge.onDarkReaderStatus(
          JSON.stringify({
            active: !!active,
            skippedNativeDark: !!skippedNativeDark
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
    DarkReader.disable();
    var detected = detectNativeDarkRaw();
    saveCache(detected);
    if (restoreIfDisabled && !detected && window.__swiftfoxDarkReaderLastConfig &&
        window.__swiftfoxDarkReaderLastConfig.enabled) {
      enableDarkReader(window.__swiftfoxDarkReaderLastConfig);
    }
    return detected;
  }

  function scheduleDomReadyRecheck() {
    if (domListenerAttached || document.readyState === 'complete') return;
    domListenerAttached = true;
    document.addEventListener('DOMContentLoaded', function () {
      domListenerAttached = false;
      var latest = window.__swiftfoxDarkReaderLastConfig;
      if (latest) {
        api.apply(latest);
      }
    }, { once: true });
  }

  var api = {
    isNativeDarkSite: function () {
      return evaluateNativeDark(true);
    },

    apply: function (config) {
      if (!config || typeof config !== 'object') return;
      window.__swiftfoxDarkReaderLastConfig = config;

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
        DarkReader.disable();
        notifyStatus(false, false);
        return;
      }

      if (!document.documentElement) {
        scheduleDomReadyRecheck();
        return;
      }

      if (config.skipNativeDark && !config.forceOverride) {
        if (!document.body) {
          scheduleDomReadyRecheck();
        }
        if (evaluateNativeDark(false)) {
          DarkReader.disable();
          notifyStatus(false, true);
          return;
        }
      }

      try {
        enableDarkReader(config);
        notifyStatus(true, false);
      } catch (e) {
        notifyStatus(false, false);
      }
    }
  };

  window.__swiftfoxDarkReader = api;
})();
