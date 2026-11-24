(function (ensureDarkReaderLoaded, createAmoledController) {
  if (window === window.top) return;

  var HANDOFF_CFG_KEY = '__sfDarkSubframePending';
  var HANDOFF_BOOT_KEY = '__sfDarkSubframeBoot';
  var INIT_GUARD_KEY = '__sfDarkSubframeInit';

  // Always consume and remove any transient handoff config immediately so nothing lingers on window
  var pendingResolvedConfig = null;
  if (Object.prototype.hasOwnProperty.call(window, HANDOFF_CFG_KEY)) {
    pendingResolvedConfig = window[HANDOFF_CFG_KEY];
    try { delete window[HANDOFF_CFG_KEY]; } catch (e) {}
  }

  if (Object.prototype.hasOwnProperty.call(window, INIT_GUARD_KEY)) return;

  var href = '';
  var protocol = '';
  try {
    protocol = (window.location && window.location.protocol) || '';
    href = (window.location && window.location.href) || '';
  } catch (e) {
    return;
  }

  function setNonEnumerable(key, value) {
    try {
      Object.defineProperty(window, key, {
        value: value,
        writable: true,
        enumerable: false,
        configurable: true
      });
    } catch (e) {}
  }

  function installNoopBootCleanup() {
    if (pendingResolvedConfig === null) {
      setNonEnumerable(HANDOFF_BOOT_KEY, function () {
        try { delete window[HANDOFF_BOOT_KEY]; } catch (e) {}
      });
    }
  }

  if (protocol !== 'http:' && protocol !== 'https:' && href !== 'about:srcdoc') {
    installNoopBootCleanup();
    return;
  }

  function normalizeHost(raw) {
    if (!raw || typeof raw !== 'string') return '';
    return raw.trim().toLowerCase().replace(/^\[|\]$/g, '').replace(/^www\./, '');
  }

  function isDenylistedSubframe() {
    if (href === 'about:srcdoc') return false;
    var host = '';
    var path = '';
    try {
      host = normalizeHost(window.location.hostname || '');
      path = (window.location.pathname || '').toLowerCase();
    } catch (e) {
      return false;
    }
    if (!host) return false;

    // CAPTCHAs & bot verification widgets
    if (host === 'challenges.cloudflare.com' ||
        host === 'hcaptcha.com' || host.slice(-13) === '.hcaptcha.com' ||
        host === 'recaptcha.net' || host.slice(-14) === '.recaptcha.net' ||
        host === 'arkoselabs.com' || host.slice(-15) === '.arkoselabs.com' ||
        host === 'funcaptcha.com' || host.slice(-15) === '.funcaptcha.com') {
      return true;
    }
    if ((host === 'google.com' || host === 'gstatic.com') && path.indexOf('/recaptcha') === 0) {
      return true;
    }

    // Payment & checkout iframes
    if (host === 'js.stripe.com' || host === 'hooks.stripe.com' || host === 'checkout.stripe.com' ||
        host === 'paypal.com' || host.slice(-11) === '.paypal.com' ||
        host === 'braintreegateway.com' || host.slice(-21) === '.braintreegateway.com' ||
        host === 'pay.google.com' || host === 'applepay.cdn-apple.com') {
      return true;
    }

    // Native dark video/audio player embeds
    if ((host === 'youtube.com' || host === 'youtube-nocookie.com') && path.indexOf('/embed') === 0) {
      return true;
    }
    if (host === 'player.vimeo.com' ||
        host === 'player.twitch.tv' || host === 'clips.twitch.tv' || host === 'embed.twitch.tv' ||
        host === 'streamable.com' ||
        (host === 'dailymotion.com' && path.indexOf('/embed') === 0) ||
        (host === 'open.spotify.com' && path.indexOf('/embed') === 0)) {
      return true;
    }

    return false;
  }

  if (isDenylistedSubframe()) {
    installNoopBootCleanup();
    return;
  }

  setNonEnumerable(INIT_GUARD_KEY, true);

  var MIN_FRAME_DIM_PX = 24;

  var fetchMethodSet = false;
  var lastConfig = null;
  var appliedSignature = null;
  var nativeDarkCache = null;
  var resizeObserver = null;
  var resizeListenerAttached = false;
  var domListenerAttached = false;
  var mediaQueryListenerAttached = false;
  var mediaQueryRecheckTimer = null;

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

  function sanitizeResolvedConfig(cfg) {
    if (!cfg || typeof cfg !== 'object') return null;
    return {
      enabled: !!cfg.enabled,
      amoled: !!cfg.amoled,
      brightness: typeof cfg.brightness === 'number' ? cfg.brightness : 100,
      contrast: typeof cfg.contrast === 'number' ? cfg.contrast : 100,
      sepia: typeof cfg.sepia === 'number' ? cfg.sepia : 0,
      skipNativeDark: cfg.skipNativeDark !== false,
      forceOverride: !!cfg.forceOverride
    };
  }

  function isDarkReaderActive() {
    return typeof DarkReader !== 'undefined' &&
      typeof DarkReader.isEnabled === 'function' &&
      DarkReader.isEnabled();
  }

  function disableDarkReaderSafe() {
    amoledCtrl.teardown();
    if (typeof DarkReader !== 'undefined' && typeof DarkReader.disable === 'function') {
      DarkReader.disable();
    }
  }

  function isFrameSubstantial() {
    return (window.innerWidth || 0) >= MIN_FRAME_DIM_PX &&
      (window.innerHeight || 0) >= MIN_FRAME_DIM_PX;
  }

  function teardownResizeWatch() {
    if (resizeObserver) {
      try { resizeObserver.disconnect(); } catch (e) {}
      resizeObserver = null;
    }
    if (resizeListenerAttached) {
      try { window.removeEventListener('resize', onFrameResized); } catch (e) {}
      resizeListenerAttached = false;
    }
  }

  function onFrameResized() {
    if (!lastConfig || !lastConfig.enabled) {
      teardownResizeWatch();
      return;
    }
    if (isFrameSubstantial()) {
      teardownResizeWatch();
      applySubframeConfig(lastConfig);
    }
  }

  function waitForSubstantialSize() {
    if (resizeObserver || resizeListenerAttached) return;
    if (typeof ResizeObserver === 'function' && document.documentElement) {
      try {
        resizeObserver = new ResizeObserver(onFrameResized);
        resizeObserver.observe(document.documentElement);
      } catch (e) {
        resizeObserver = null;
      }
    }
    if (!resizeListenerAttached) {
      resizeListenerAttached = true;
      window.addEventListener('resize', onFrameResized);
    }
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

  function isPureVideoEmbed() {
    try {
      if (document.querySelector('article')) return false;
      var video = document.querySelector('video');
      if (!video) return false;
      var rect = video.getBoundingClientRect();
      var vw = window.innerWidth || 1;
      var vh = window.innerHeight || 1;
      var coverage = (rect.width * rect.height) / (vw * vh);
      if (coverage < 0.8) return false;
      var textLen = document.body && document.body.innerText ? document.body.innerText.trim().length : 0;
      return textLen < 25;
    } catch (e) {
      return false;
    }
  }

  function detectSubframeNativeDark() {
    try {
      var search = (window.location && window.location.search) || '';
      if (/[?&]theme=dark(?:&|$)/i.test(search)) {
        return true;
      }
    } catch (e) {}

    var bodyDark = checkElementDarkBg(document.body);
    if (bodyDark !== null) return bodyDark;

    var rootDark = checkElementDarkBg(document.documentElement);
    if (rootDark !== null) return rootDark;

    // Embedded cards (Twitter/X, Bluesky, Reddit, Instagram) often keep html/body transparent
    // around a rounded opaque card container. Inspect the first substantial opaque container
    // instead of trusting inherited color-scheme from parent iframe CSS.
    if (document.body) {
      var queue = [];
      var children = document.body.children;
      for (var i = 0; i < children.length && queue.length < 24; i++) {
        queue.push(children[i]);
      }
      var visited = 0;
      while (queue.length > 0 && visited < 24) {
        var el = queue.shift();
        visited++;
        if (!el) continue;
        try {
          var rect = el.getBoundingClientRect();
          if (rect.width >= 40 && rect.height >= 40) {
            var elDark = checkElementDarkBg(el);
            if (elDark !== null) {
              return elDark;
            }
          }
          var kids = el.children;
          for (var j = 0; j < kids.length && queue.length < 24; j++) {
            queue.push(kids[j]);
          }
        } catch (e) {}
      }
    }

    return false;
  }

  function evaluateSubframeNativeDark() {
    if (!isDarkReaderActive()) {
      nativeDarkCache = detectSubframeNativeDark();
      return nativeDarkCache;
    }
    if (nativeDarkCache !== null) {
      return nativeDarkCache;
    }
    // Re-probe once when transitioning from AMOLED or skipNativeDark=false to skipNativeDark=true
    appliedSignature = null;
    disableDarkReaderSafe();
    nativeDarkCache = detectSubframeNativeDark();
    return nativeDarkCache;
  }

  function forwardToNestedIframes(payload) {
    try {
      var frames = document.querySelectorAll('iframe');
      for (var i = 0; i < frames.length; i++) {
        try {
          if (frames[i].contentWindow) {
            frames[i].contentWindow.postMessage(payload, '*');
          }
        } catch (e) {}
      }
    } catch (e) {}
  }

  function attachColorSchemeMediaListener() {
    if (mediaQueryListenerAttached || typeof window.matchMedia !== 'function') return;
    try {
      var mql = window.matchMedia('(prefers-color-scheme: dark)');
      if (!mql) return;
      var onChange = function () {
        nativeDarkCache = null;
        if (!lastConfig || !lastConfig.enabled || !lastConfig.skipNativeDark ||
            lastConfig.forceOverride || lastConfig.amoled) {
          return;
        }
        if (mediaQueryRecheckTimer !== null) {
          clearTimeout(mediaQueryRecheckTimer);
        }
        mediaQueryRecheckTimer = setTimeout(function () {
          mediaQueryRecheckTimer = null;
          if (lastConfig && lastConfig.enabled && lastConfig.skipNativeDark &&
              !lastConfig.forceOverride && !lastConfig.amoled) {
            nativeDarkCache = null;
            applySubframeConfig(lastConfig);
          }
        }, 60);
      };
      if (typeof mql.addEventListener === 'function') {
        mql.addEventListener('change', onChange);
        mediaQueryListenerAttached = true;
      } else if (typeof mql.addListener === 'function') {
        mql.addListener(onChange);
        mediaQueryListenerAttached = true;
      }
    } catch (e) {}
  }

  function clearMediaQueryRecheckTimer() {
    if (mediaQueryRecheckTimer !== null) {
      clearTimeout(mediaQueryRecheckTimer);
      mediaQueryRecheckTimer = null;
    }
  }

  function applySubframeConfig(config) {
    if (!config || typeof config !== 'object') return;
    lastConfig = config;

    if (!config.enabled) {
      teardownResizeWatch();
      clearMediaQueryRecheckTimer();
      nativeDarkCache = null;
      appliedSignature = null;
      disableDarkReaderSafe();
      return;
    }

    attachColorSchemeMediaListener();

    if (document.readyState === 'loading' || !document.documentElement || !document.body) {
      if (!domListenerAttached) {
        domListenerAttached = true;
        document.addEventListener('DOMContentLoaded', function () {
          domListenerAttached = false;
          if (lastConfig) {
            applySubframeConfig(lastConfig);
          }
        }, { once: true });
      }
      return;
    }

    if (!isFrameSubstantial()) {
      waitForSubstantialSize();
      return;
    }
    teardownResizeWatch();

    if (isPureVideoEmbed()) {
      appliedSignature = null;
      disableDarkReaderSafe();
      return;
    }

    if (config.skipNativeDark && !config.forceOverride && !config.amoled) {
      if (evaluateSubframeNativeDark()) {
        appliedSignature = null;
        disableDarkReaderSafe();
        return;
      }
    } else {
      clearMediaQueryRecheckTimer();
      nativeDarkCache = null;
    }

    var nextSignature = buildConfigSignature(config);
    if (isDarkReaderActive() && appliedSignature === nextSignature) {
      if (config.amoled) {
        amoledCtrl.patchVariablesRule(config);
      }
      return;
    }

    try {
      if (typeof ensureDarkReaderLoaded === 'function') {
        ensureDarkReaderLoaded();
      }
      if (typeof DarkReader === 'undefined' || typeof DarkReader.enable !== 'function') {
        return;
      }
      if (!fetchMethodSet && typeof DarkReader.setFetchMethod === 'function' &&
          typeof window.fetch === 'function') {
        DarkReader.setFetchMethod(window.fetch.bind(window));
        fetchMethodSet = true;
      }

      var brightness = typeof config.brightness === 'number' ? config.brightness : 100;
      var contrast = typeof config.contrast === 'number' ? config.contrast : 100;
      var sepia = typeof config.sepia === 'number' ? config.sepia : 0;

      amoledCtrl.teardown();
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
        amoledCtrl.install(config);
      }
      appliedSignature = nextSignature;
    } catch (e) {
      amoledCtrl.teardown();
      appliedSignature = null;
    }
  }

  function bootFromResolvedConfig(resolvedCfg) {
    try { delete window[HANDOFF_BOOT_KEY]; } catch (e) {}
    var sanitized = sanitizeResolvedConfig(resolvedCfg);
    if (sanitized) {
      applySubframeConfig(sanitized);
    }
  }

  if (pendingResolvedConfig !== null) {
    bootFromResolvedConfig(pendingResolvedConfig);
  } else {
    setNonEnumerable(HANDOFF_BOOT_KEY, bootFromResolvedConfig);
  }

  window.addEventListener('message', function (event) {
    try {
      if (!event || !event.data || event.data.__swiftfoxDarkSync !== true) return;
      if (event.source !== window.parent && event.source !== window.top) return;
      var sanitized = sanitizeResolvedConfig(event.data.config);
      if (!sanitized) return;
      applySubframeConfig(sanitized);
      forwardToNestedIframes(event.data);
    } catch (e) {}
  });
})
