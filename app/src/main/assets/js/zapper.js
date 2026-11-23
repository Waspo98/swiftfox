(function (runtimeKey, bridgeToken) {
  'use strict';
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  var OVERLAY_ID = '__swiftfox_zapper_overlay';
  var SHIELD_ID = '__swiftfox_zapper_shield';
  var STYLE_ID = 'swiftfox-zapped';
  var MAX_ANCESTORS = 8;
  var MAX_PATH_SEGMENTS = 8;
  var UNSAFE_BARE_TAGS = {
    html: true,
    body: true,
    head: true,
    div: true,
    span: true,
    p: true,
    a: true,
    main: true,
    section: true,
    article: true,
    ul: true,
    ol: true,
    li: true,
    table: true,
    tr: true,
    td: true,
    form: true,
    iframe: true,
    img: true,
    svg: true,
    button: true,
    input: true,
    ':root': true,
    '*': true
  };

  var UTILITY_CLASS_RE = /^(?:flex|inline-flex|grid|inline-grid|block|inline-block|inline|hidden|contents|relative|absolute|fixed|sticky|static|visible|invisible|overflow-[a-z-]+|w-|h-|min-w-|min-h-|max-w-|max-h-|p-|px-|py-|pt-|pb-|pl-|pr-|m-|mx-|my-|mt-|mb-|ml-|mr-|gap-|space-|text-|font-|leading-|tracking-|bg-|border|rounded|shadow|opacity-|z-|items-|justify-|self-|place-|col-|row-|order-|inset-|top-|bottom-|left-|right-|cursor-|select-|pointer-events-|transition|duration-|ease-|transform|scale-|translate-|rotate-|sm:|md:|lg:|xl:|2xl:|hover:|focus:|active:|dark:)/i;

  function applyZappedCss(cssText) {
    var text = typeof cssText === 'string' ? cssText : '';
    function attach() {
      var style = document.getElementById(STYLE_ID);
      if (!text) {
        if (style) style.textContent = '';
        return true;
      }
      var target = document.documentElement || document.head || document.body;
      if (!target) return false;
      if (!style) {
        style = document.createElement('style');
        style.id = STYLE_ID;
        style.type = 'text/css';
        target.appendChild(style);
      } else if (style.parentNode !== target || style !== target.lastElementChild) {
        // Keep zapped rules at the end of documentElement so !important declarations win tie-breakers
        target.appendChild(style);
      }
      style.textContent = text;
      return true;
    }
    if (!attach() && typeof document.addEventListener === 'function') {
      document.addEventListener('DOMContentLoaded', attach, { once: true });
    }
  }

  var state = {
    active: false,
    accentHex: '#E86A17',
    overlay: null,
    shield: null,
    chain: [],
    scopeDepth: 0,
    currentSelector: '',
    currentSummary: '',
    currentMatchCount: 0,
    undoStack: [],
    touchStartX: 0,
    touchStartY: 0,
    touchMoved: false,
    suppressNextClickUntil: 0,
    rafPending: false
  };

  function normalizeHex(hex) {
    if (typeof hex !== 'string') return '#E86A17';
    var trimmed = hex.trim();
    if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) return trimmed;
    if (/^#[0-9a-fA-F]{8}$/.test(trimmed)) {
      return '#' + trimmed.slice(3);
    }
    if (/^#[0-9a-fA-F]{3}$/.test(trimmed)) {
      return '#' +
        trimmed.charAt(1) + trimmed.charAt(1) +
        trimmed.charAt(2) + trimmed.charAt(2) +
        trimmed.charAt(3) + trimmed.charAt(3);
    }
    return '#E86A17';
  }

  function hexToRgba(hex, alpha) {
    var norm = normalizeHex(hex);
    var r = parseInt(norm.slice(1, 3), 16);
    var g = parseInt(norm.slice(3, 5), 16);
    var b = parseInt(norm.slice(5, 7), 16);
    if (isNaN(r) || isNaN(g) || isNaN(b)) {
      return 'rgba(232, 106, 23, ' + alpha + ')';
    }
    return 'rgba(' + r + ', ' + g + ', ' + b + ', ' + alpha + ')';
  }

  function escapeCssIdent(value) {
    if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
      return CSS.escape(value);
    }
    var str = String(value);
    var length = str.length;
    var result = '';
    var firstCodeUnit = str.charCodeAt(0);
    for (var i = 0; i < length; i++) {
      var codeUnit = str.charCodeAt(i);
      if (codeUnit === 0x0000) {
        result += '\uFFFD';
        continue;
      }
      if (
        (codeUnit >= 0x0001 && codeUnit <= 0x001F) ||
        codeUnit === 0x007F ||
        (i === 0 && codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
        (i === 1 && codeUnit >= 0x0030 && codeUnit <= 0x0039 && firstCodeUnit === 0x002D)
      ) {
        result += '\\' + codeUnit.toString(16) + ' ';
        continue;
      }
      if (i === 0 && length === 1 && codeUnit === 0x002D) {
        result += '\\' + str.charAt(i);
        continue;
      }
      if (
        codeUnit >= 0x0080 ||
        codeUnit === 0x002D ||
        codeUnit === 0x005F ||
        (codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
        (codeUnit >= 0x0041 && codeUnit <= 0x005A) ||
        (codeUnit >= 0x0061 && codeUnit <= 0x007A)
      ) {
        result += str.charAt(i);
        continue;
      }
      result += '\\' + str.charAt(i);
    }
    return result;
  }

  function escapeAttrValue(value) {
    return String(value)
      .replace(/\\/g, '\\\\')
      .replace(/"/g, '\\"');
  }

  function isSafeAttrText(value) {
    if (typeof value !== 'string') return false;
    var trimmed = value.trim();
    if (!trimmed || trimmed.length > 96) return false;
    if (trimmed.indexOf('/*') !== -1 || trimmed.indexOf('*/') !== -1) return false;
    if (/[{};<\x00-\x1f]/.test(trimmed)) return false;
    return true;
  }

  function isZapperNode(el) {
    if (!el) return false;
    return el.id === OVERLAY_ID || el.id === SHIELD_ID;
  }

  function isStableId(id) {
    if (!id || typeof id !== 'string') return false;
    if (id === OVERLAY_ID || id === SHIELD_ID || id === STYLE_ID) return false;
    if (id.indexOf('/*') !== -1 || id.indexOf('*/') !== -1) return false;
    if (/[{};<\s\x00-\x1f]/.test(id)) return false;
    if (/^\d+$/.test(id)) return false;
    if (/^css-[a-z0-9_-]+$/i.test(id)) return false;
    if (/^ember\d*$/i.test(id)) return false;
    if (/^:r[a-z0-9]*:$/i.test(id)) return false;
    if (/^_?r_[a-z0-9_]+$/i.test(id)) return false;
    if (/^[0-9a-f]{8,}$/i.test(id)) return false;
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) return false;
    if (/^(?:yui_|ext-gen|gwt-uid|mui-|radix-|headlessui-|google_ads_iframe_|twitter-widget-)[a-z0-9_-]*$/i.test(id)) return false;
    if (/[a-z0-9_-]+_[0-9a-f]{6,}$/i.test(id)) return false;
    return true;
  }

  function isStableClass(cls) {
    if (!cls || typeof cls !== 'string') return false;
    if (cls.indexOf('/*') !== -1 || cls.indexOf('*/') !== -1) return false;
    if (/[{};<\s\x00-\x1f]/.test(cls)) return false;
    if (/^css-[a-z0-9_-]+$/i.test(cls)) return false;
    if (/^sc-[a-z0-9_-]+$/i.test(cls)) return false;
    if (/^_[a-zA-Z0-9]{5,}$/.test(cls)) return false;
    if (/^jsx-\d+$/i.test(cls)) return false;
    if (/^svelte-[a-z0-9]+$/i.test(cls)) return false;
    if (/^[0-9a-f]{6,}$/i.test(cls)) return false;
    if (/^[a-z0-9-]+_[a-z0-9-]+__[a-zA-Z0-9]{4,}$/i.test(cls)) return false;
    if (/^ng-[a-z0-9-]+$/i.test(cls)) return false;
    if (/^(?:is-|has-|active|open|show|visible|selected|focus|hover)/i.test(cls)) return false;
    return true;
  }

  function getTagName(el) {
    if (!el) return '*';
    return (el.localName || el.tagName || '*').toLowerCase();
  }

  function isCustomElementTag(tag) {
    return typeof tag === 'string' && tag.indexOf('-') > 0 && /^[a-z][a-z0-9-]*$/.test(tag);
  }

  function getStableClasses(el, maxCount) {
    var limit = maxCount || 2;
    var semantic = [];
    var fallback = [];
    if (!el || !el.classList) return semantic;
    for (var i = 0; i < el.classList.length; i++) {
      var cls = el.classList[i];
      if (!isStableClass(cls)) continue;
      if (UTILITY_CLASS_RE.test(cls)) {
        if (fallback.length < limit) fallback.push(cls);
      } else {
        semantic.push(cls);
        if (semantic.length >= limit) break;
      }
    }
    return semantic.length > 0 ? semantic : fallback;
  }

  function safeQueryMatches(selector) {
    if (!selector) return [];
    try {
      return document.querySelectorAll(selector);
    } catch (_e) {
      return [];
    }
  }

  function queryCountContaining(selector, el) {
    var list = safeQueryMatches(selector);
    if (!list || list.length === 0) return 0;
    for (var i = 0; i < list.length; i++) {
      if (list[i] === el) return list.length;
    }
    return 0;
  }

  function extractStableIframeSrcPrefix(el) {
    if (!el || getTagName(el) !== 'iframe') return null;
    var rawSrc = el.getAttribute('src') || '';
    if (!rawSrc || rawSrc.indexOf('about:') === 0 || rawSrc.indexOf('javascript:') === 0 || rawSrc.indexOf('data:') === 0) {
      return null;
    }
    try {
      var parsed = new URL(rawSrc, window.location.href);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null;
      var host = parsed.hostname;
      if (!host) return null;
      var pathParts = (parsed.pathname || '').split('/').filter(function (p) { return p.length > 0; });
      var prefix = host;
      if (pathParts.length > 0 && pathParts[0].length <= 32 && !/^[0-9a-f-]{8,}$/i.test(pathParts[0])) {
        prefix = host + '/' + pathParts[0];
      }
      return isSafeAttrText(prefix) ? prefix : (isSafeAttrText(host) ? host : null);
    } catch (_e) {
      return null;
    }
  }

  function getUniqueAnchorSelector(el) {
    if (!el || el === document.body || el === document.documentElement) return null;
    var tag = getTagName(el);
    var escapedTag = escapeCssIdent(tag);

    if (isStableId(el.id)) {
      var idSel = '#' + escapeCssIdent(el.id);
      if (queryCountContaining(idSel, el) === 1) return idSel;
    }

    if (isCustomElementTag(tag)) {
      if (queryCountContaining(escapedTag, el) === 1) return escapedTag;
    }

    var testId = el.getAttribute('data-testid');
    if (isSafeAttrText(testId)) {
      var testSel = escapedTag + '[data-testid="' + escapeAttrValue(testId.trim()) + '"]';
      if (queryCountContaining(testSel, el) === 1) return testSel;
    }

    var ariaLabel = el.getAttribute('aria-label');
    if (isSafeAttrText(ariaLabel)) {
      var ariaSel = escapedTag + '[aria-label="' + escapeAttrValue(ariaLabel.trim()) + '"]';
      if (queryCountContaining(ariaSel, el) === 1) return ariaSel;
    }

    var role = el.getAttribute('role');
    if (isSafeAttrText(role)) {
      var roleSel = escapedTag + '[role="' + escapeAttrValue(role.trim()) + '"]';
      if (queryCountContaining(roleSel, el) === 1) return roleSel;
    }

    var classes = getStableClasses(el, 2);
    if (classes.length > 0) {
      var classSel = escapedTag + classes.map(function (c) {
        return '.' + escapeCssIdent(c);
      }).join('');
      if (queryCountContaining(classSel, el) === 1) return classSel;
    }

    return null;
  }

  function getNthOfTypeSegment(el) {
    var tag = getTagName(el);
    var escapedTag = escapeCssIdent(tag);
    var parent = el.parentElement;
    if (!parent) return escapedTag;

    var index = 1;
    var sameTagTotal = 0;
    var children = parent.children;
    for (var i = 0; i < children.length; i++) {
      var sibling = children[i];
      if (isZapperNode(sibling)) continue;
      if (getTagName(sibling) === tag) {
        sameTagTotal++;
        if (sibling === el) {
          index = sameTagTotal;
        }
      }
    }
    var seg = sameTagTotal > 1 ? escapedTag + ':nth-of-type(' + index + ')' : escapedTag;
    var segClasses = getStableClasses(el, 1);
    if (segClasses.length > 0 && sameTagTotal === 1) {
      seg = escapedTag + '.' + escapeCssIdent(segClasses[0]);
    }
    return seg;
  }

  function isUnsafeBareSelector(sel) {
    if (!sel) return true;
    var trimmed = sel.trim().toLowerCase();
    return Boolean(UNSAFE_BARE_TAGS[trimmed]);
  }

  function buildResilientSelector(el) {
    if (!el || el.nodeType !== 1) return '';
    var tag = getTagName(el);
    var escapedTag = escapeCssIdent(tag);

    if (isStableId(el.id)) {
      var idSel = '#' + escapeCssIdent(el.id);
      if (queryCountContaining(idSel, el) === 1) {
        return idSel;
      }
    }

    // Custom web components (e.g., <instaread-player>, <twitter-widget>) are great durable anchors
    if (isCustomElementTag(tag)) {
      var customCount = queryCountContaining(escapedTag, el);
      if (customCount > 0 && customCount <= 3) {
        return escapedTag;
      }
    }

    // Embedded iframes: prefer stable src host/prefix or title/name so cross-origin widgets persist cleanly
    if (tag === 'iframe') {
      var iframePrefix = extractStableIframeSrcPrefix(el);
      if (iframePrefix) {
        var srcSel = 'iframe[src*="' + escapeAttrValue(iframePrefix) + '"]';
        var srcCount = queryCountContaining(srcSel, el);
        if (srcCount > 0 && srcCount <= 3) {
          return srcSel;
        }
      }
      var iframeTitle = el.getAttribute('title');
      if (isSafeAttrText(iframeTitle)) {
        var titleSel = 'iframe[title="' + escapeAttrValue(iframeTitle.trim()) + '"]';
        if (queryCountContaining(titleSel, el) === 1) {
          return titleSel;
        }
      }
      var iframeName = el.getAttribute('name');
      if (isSafeAttrText(iframeName) && !/^(?:google_ads|twitter-widget|\d+)/i.test(iframeName.trim())) {
        var nameSel = 'iframe[name="' + escapeAttrValue(iframeName.trim()) + '"]';
        if (queryCountContaining(nameSel, el) === 1) {
          return nameSel;
        }
      }
    }

    var testId = el.getAttribute('data-testid');
    if (isSafeAttrText(testId)) {
      var testIdSel = '[data-testid="' + escapeAttrValue(testId.trim()) + '"]';
      var testCount = queryCountContaining(testIdSel, el);
      if (testCount > 0 && testCount <= 3) {
        return testIdSel;
      }
      var tagTestSel = escapedTag + testIdSel;
      var tagTestCount = queryCountContaining(tagTestSel, el);
      if (tagTestCount > 0 && tagTestCount <= 3) {
        return tagTestSel;
      }
    }

    var ariaLabel = el.getAttribute('aria-label');
    if (isSafeAttrText(ariaLabel)) {
      var ariaSel = escapedTag + '[aria-label="' + escapeAttrValue(ariaLabel.trim()) + '"]';
      var ariaCount = queryCountContaining(ariaSel, el);
      if (ariaCount > 0 && ariaCount <= 2) {
        return ariaSel;
      }
    }

    var role = el.getAttribute('role');
    if (isSafeAttrText(role)) {
      var roleSel = escapedTag + '[role="' + escapeAttrValue(role.trim()) + '"]';
      if (queryCountContaining(roleSel, el) === 1) {
        return roleSel;
      }
    }

    var classes = getStableClasses(el, 2);
    if (classes.length > 0) {
      var classSel = escapedTag + classes.map(function (c) {
        return '.' + escapeCssIdent(c);
      }).join('');
      var classCount = queryCountContaining(classSel, el);
      if (classCount > 0 && classCount <= 2) {
        return classSel;
      }
    }

    // Walk up toward a unique ancestor anchor
    var segments = [];
    var cur = el;
    var anchored = false;
    while (
      cur &&
      cur.nodeType === 1 &&
      cur !== document.documentElement &&
      segments.length < MAX_PATH_SEGMENTS
    ) {
      if (cur === document.body) {
        segments.unshift('body');
        anchored = true;
        break;
      }
      if (cur !== el) {
        var anchor = getUniqueAnchorSelector(cur);
        if (anchor) {
          segments.unshift(anchor);
          anchored = true;
          break;
        }
      }
      segments.unshift(getNthOfTypeSegment(cur));
      cur = cur.parentElement;
    }

    var pathSel = segments.join(' > ');
    if (pathSel && !isUnsafeBareSelector(pathSel)) {
      var pathCount = queryCountContaining(pathSel, el);
      if (pathCount === 1 || (anchored && pathCount > 0 && pathCount <= 3)) {
        return pathSel;
      }
    }

    var MAX_SELECTOR_LENGTH = 1024;

    // Full path from nearest unique anchor or body to guarantee precision
    var fallbackSegments = [];
    cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.documentElement) {
      if (cur === document.body) {
        fallbackSegments.unshift('body');
        break;
      }
      if (cur !== el) {
        var fbAnchor = getUniqueAnchorSelector(cur);
        if (fbAnchor) {
          fallbackSegments.unshift(fbAnchor);
          break;
        }
      }
      fallbackSegments.unshift(getNthOfTypeSegment(cur));
      cur = cur.parentElement;
    }
    var fallbackSel = fallbackSegments.join(' > ');
    // Trim leading ancestor segments if deeply nested DOM exceeds Kotlin's 1024-char selector cap
    while (fallbackSel.length > MAX_SELECTOR_LENGTH && fallbackSegments.length > 2) {
      fallbackSegments.shift();
      fallbackSel = fallbackSegments.join(' > ');
    }
    if (
      fallbackSel &&
      fallbackSel.length <= MAX_SELECTOR_LENGTH &&
      !isUnsafeBareSelector(fallbackSel) &&
      queryCountContaining(fallbackSel, el) > 0
    ) {
      return fallbackSel;
    }
    return '';
  }

  function buildSummary(el) {
    if (!el || el.nodeType !== 1) return '';
    var tag = getTagName(el);
    if (tag === 'iframe') {
      var iframePrefix = extractStableIframeSrcPrefix(el);
      if (iframePrefix) {
        return 'iframe (' + iframePrefix.split('/')[0] + ')';
      }
      var iframeTitle = el.getAttribute('title');
      if (iframeTitle && iframeTitle.trim()) {
        return 'iframe "' + iframeTitle.trim().slice(0, 26) + '"';
      }
    }
    if (isStableId(el.id)) {
      return tag + '#' + el.id;
    }
    var testId = el.getAttribute('data-testid');
    if (testId && testId.trim()) {
      return tag + '[data-testid="' + testId.trim().slice(0, 32) + '"]';
    }
    var ariaLabel = el.getAttribute('aria-label');
    if (ariaLabel && ariaLabel.trim()) {
      return tag + ' "' + ariaLabel.trim().slice(0, 32) + '"';
    }
    var classes = getStableClasses(el, 2);
    if (classes.length > 0) {
      return tag + '.' + classes.join('.');
    }
    var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
    if (text) {
      return tag + ' "' + text.slice(0, 28) + (text.length > 28 ? '…' : '') + '"';
    }
    return tag;
  }

  function ensureShield() {
    var existing = document.getElementById(SHIELD_ID);
    if (existing) {
      state.shield = existing;
      styleShield(existing);
      return existing;
    }
    var shield = document.createElement('div');
    shield.id = SHIELD_ID;
    styleShield(shield);
    shield.addEventListener('touchstart', onShieldTouchStart, { passive: true });
    shield.addEventListener('touchmove', onShieldTouchMove, { passive: true });
    shield.addEventListener('touchend', onShieldTouchEnd, false);
    shield.addEventListener('touchcancel', onShieldTouchCancel, false);
    shield.addEventListener('pointerdown', onShieldSwallow, false);
    shield.addEventListener('pointerup', onShieldSwallow, false);
    shield.addEventListener('mousedown', onShieldSwallow, false);
    shield.addEventListener('mouseup', onShieldSwallow, false);
    shield.addEventListener('click', onShieldClick, false);
    var root = document.documentElement || document.body;
    if (root) {
      root.appendChild(shield);
    }
    state.shield = shield;
    return shield;
  }

  function styleShield(shield) {
    shield.style.setProperty('position', 'fixed', 'important');
    shield.style.setProperty('inset', '0', 'important');
    shield.style.setProperty('width', '100vw', 'important');
    shield.style.setProperty('height', '100vh', 'important');
    shield.style.setProperty('z-index', '2147483646', 'important');
    shield.style.setProperty('background', 'transparent', 'important');
    shield.style.setProperty('pointer-events', 'auto', 'important');
    shield.style.setProperty('touch-action', 'pan-x pan-y', 'important');
    shield.style.setProperty('user-select', 'none', 'important');
    shield.style.setProperty('-webkit-user-select', 'none', 'important');
    shield.style.setProperty('-webkit-tap-highlight-color', 'transparent', 'important');
    shield.style.setProperty('margin', '0', 'important');
    shield.style.setProperty('padding', '0', 'important');
    shield.style.setProperty('border', 'none', 'important');
  }

  function ensureOverlay() {
    var existing = document.getElementById(OVERLAY_ID);
    if (existing) {
      state.overlay = existing;
      styleOverlay(existing);
      return existing;
    }
    var box = document.createElement('div');
    box.id = OVERLAY_ID;
    styleOverlay(box);
    var root = document.documentElement || document.body;
    if (root) {
      root.appendChild(box);
    }
    state.overlay = box;
    return box;
  }

  function styleOverlay(box) {
    var borderHex = normalizeHex(state.accentHex);
    box.style.setProperty('position', 'fixed', 'important');
    box.style.setProperty('pointer-events', 'none', 'important');
    box.style.setProperty('z-index', '2147483647', 'important');
    box.style.setProperty('box-sizing', 'border-box', 'important');
    box.style.setProperty('border', '2.5px solid ' + borderHex, 'important');
    box.style.setProperty('background', hexToRgba(borderHex, 0.2), 'important');
    box.style.setProperty('border-radius', '10px', 'important');
    box.style.setProperty('margin', '0', 'important');
    box.style.setProperty('padding', '0', 'important');
    box.style.setProperty('box-shadow', '0 0 0 1px rgba(0,0,0,0.35)', 'important');
    if (state.chain.length === 0) {
      box.style.setProperty('display', 'none', 'important');
    }
  }

  function hideOverlay() {
    if (state.overlay) {
      state.overlay.style.setProperty('display', 'none', 'important');
    }
  }

  function updateOverlayPosition() {
    state.rafPending = false;
    if (!state.active || !state.overlay || state.chain.length === 0) {
      hideOverlay();
      return;
    }
    var el = state.chain[state.scopeDepth];
    if (!el || !el.isConnected) {
      hideOverlay();
      return;
    }
    var rect = el.getBoundingClientRect();
    if (rect.width <= 0 && rect.height <= 0) {
      hideOverlay();
      return;
    }
    state.overlay.style.setProperty('display', 'block', 'important');
    state.overlay.style.setProperty('left', rect.left + 'px', 'important');
    state.overlay.style.setProperty('top', rect.top + 'px', 'important');
    state.overlay.style.setProperty('width', rect.width + 'px', 'important');
    state.overlay.style.setProperty('height', rect.height + 'px', 'important');
  }

  function scheduleOverlayPositionUpdate() {
    if (!state.active || state.rafPending) return;
    state.rafPending = true;
    if (typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(updateOverlayPosition);
    } else {
      updateOverlayPosition();
    }
  }

  function emitBridge(eventName, payloadObj) {
    var ch = window.SwiftfoxNativeChannel;
    if (bridgeToken && ch && typeof ch.postMessage === 'function') {
      try {
        ch.postMessage(
          JSON.stringify({
            channel: 'zapper',
            token: bridgeToken,
            event: eventName,
            json: JSON.stringify(payloadObj)
          })
        );
      } catch (_e) {
      }
    }
  }

  function computeViewportCenterY(el) {
    try {
      var vh = window.innerHeight || document.documentElement.clientHeight || 1;
      var rect = el.getBoundingClientRect();
      var center = (rect.top + rect.height * 0.5) / vh;
      if (isNaN(center)) return 0.3;
      return Math.max(0, Math.min(1, center));
    } catch (_e) {
      return 0.3;
    }
  }

  function selectAtDepth(depth) {
    if (state.chain.length === 0) return false;
    var maxDepth = state.chain.length - 1;
    var clamped = Math.max(0, Math.min(maxDepth, depth | 0));
    var el = state.chain[clamped];
    var selector = buildResilientSelector(el);
    if (!selector || selector.length > 1024) {
      return false;
    }
    state.scopeDepth = clamped;
    var summary = buildSummary(el);
    var matchCount = safeQueryMatches(selector).length;

    state.currentSelector = selector;
    state.currentSummary = summary;
    state.currentMatchCount = matchCount;

    updateOverlayPosition();
    emitBridge('onSelectionChanged', {
      selector: selector,
      summary: summary,
      scopeDepth: clamped,
      maxScopeDepth: maxDepth,
      matchCount: matchCount,
      viewportCenterY: computeViewportCenterY(el)
    });
    return true;
  }

  function buildAncestorChain(tappedEl) {
    var chain = [];
    var cur = tappedEl;
    while (
      cur &&
      cur.nodeType === 1 &&
      cur !== document.body &&
      cur !== document.documentElement &&
      !isZapperNode(cur) &&
      chain.length <= MAX_ANCESTORS
    ) {
      chain.push(cur);
      cur = cur.parentElement;
    }
    return chain;
  }

  function isTransparentColor(colorStr) {
    if (!colorStr || colorStr === 'transparent') return true;
    var m = colorStr.match(/^rgba\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*,\s*([\d.]+)\s*\)$/i);
    return Boolean(m && parseFloat(m[1]) <= 0.01);
  }

  function hasDirectText(el) {
    if (!el || !el.childNodes) return false;
    for (var i = 0; i < el.childNodes.length; i++) {
      var n = el.childNodes[i];
      if (n.nodeType === 3 && n.nodeValue && n.nodeValue.trim().length > 0) {
        return true;
      }
    }
    return false;
  }

  function isInertOrHiddenCandidate(el) {
    if (!el || el.nodeType !== 1) return true;
    if (isZapperNode(el)) return true;
    if (el === document.body || el === document.documentElement) return true;
    var tag = getTagName(el);
    if (tag === 'head' || tag === 'script' || tag === 'style' || tag === 'link' || tag === 'meta' || tag === 'noscript') {
      return true;
    }
    var rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return true;
    var cs = null;
    try {
      cs = window.getComputedStyle(el);
    } catch (_e) {}
    if (cs) {
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') {
        return true;
      }
      var op = parseFloat(cs.opacity);
      if (!isNaN(op) && op <= 0.01) {
        return true;
      }
      var isMediaOrVisual = (tag === 'iframe' || tag === 'img' || tag === 'svg' || tag === 'video' || tag === 'canvas' || tag === 'input' || tag === 'button' || tag === 'a');
      var bgTransparent = isTransparentColor(cs.backgroundColor) && (!cs.backgroundImage || cs.backgroundImage === 'none');
      // Skip invisible fullscreen inert modal/drawer wrappers (e.g., closed slide-over drawers)
      var vw = window.innerWidth || document.documentElement.clientWidth || 1;
      var vh = window.innerHeight || document.documentElement.clientHeight || 1;
      var isFullViewport = rect.width >= vw * 0.9 && rect.height >= vh * 0.85;
      if (isFullViewport && bgTransparent && !isMediaOrVisual && !hasDirectText(el)) {
        return true;
      }
      if (cs.pointerEvents === 'none' && bgTransparent && !isMediaOrVisual && !hasDirectText(el)) {
        return true;
      }
    }
    return false;
  }

  function pickElementAtPoint(clientX, clientY) {
    if (typeof clientX !== 'number' || typeof clientY !== 'number') return null;
    var shield = state.shield;
    var overlay = state.overlay;
    if (shield) shield.style.setProperty('pointer-events', 'none', 'important');
    if (overlay) overlay.style.setProperty('pointer-events', 'none', 'important');
    var chosen = null;
    try {
      if (typeof document.elementsFromPoint === 'function') {
        var stack = document.elementsFromPoint(clientX, clientY);
        for (var i = 0; i < stack.length; i++) {
          var cand = stack[i];
          if (!isInertOrHiddenCandidate(cand)) {
            chosen = cand;
            break;
          }
        }
        // Fallback: if every candidate was filtered as transparent container, take first non-zapper non-body element
        if (!chosen) {
          for (var j = 0; j < stack.length; j++) {
            var fb = stack[j];
            if (fb && fb.nodeType === 1 && !isZapperNode(fb) && fb !== document.body && fb !== document.documentElement) {
              chosen = fb;
              break;
            }
          }
        }
      } else if (typeof document.elementFromPoint === 'function') {
        var single = document.elementFromPoint(clientX, clientY);
        if (single && !isInertOrHiddenCandidate(single)) {
          chosen = single;
        }
      }
    } finally {
      if (shield && state.active) {
        shield.style.setProperty('pointer-events', 'auto', 'important');
      }
    }
    return chosen;
  }

  function selectTargetElement(target) {
    if (!target) return;
    var chain = buildAncestorChain(target);
    if (chain.length === 0) return;

    state.chain = chain;
    ensureOverlay();
    selectAtDepth(0);
  }

  function onShieldTouchStart(e) {
    if (!state.active) return;
    e.stopPropagation();
    e.stopImmediatePropagation();
    if (!e.touches || e.touches.length !== 1) {
      // Multi-touch pinch-zoom must not fire a tap selection when fingers lift
      state.touchMoved = true;
      return;
    }
    state.touchStartX = e.touches[0].clientX;
    state.touchStartY = e.touches[0].clientY;
    state.touchMoved = false;
  }

  function onShieldTouchMove(e) {
    if (!state.active) return;
    e.stopPropagation();
    e.stopImmediatePropagation();
    if (!e.touches || e.touches.length !== 1) return;
    var dx = Math.abs(e.touches[0].clientX - state.touchStartX);
    var dy = Math.abs(e.touches[0].clientY - state.touchStartY);
    if (dx > 12 || dy > 12) {
      state.touchMoved = true;
    }
  }

  function onShieldTouchCancel(e) {
    if (!state.active) return;
    e.stopPropagation();
    e.stopImmediatePropagation();
    state.touchMoved = false;
  }

  function onShieldTouchEnd(e) {
    if (!state.active) return;
    e.stopPropagation();
    e.stopImmediatePropagation();
    if (state.touchMoved) {
      state.touchMoved = false;
      return;
    }
    var touch = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0] : null;
    if (!touch) return;

    e.preventDefault();
    state.suppressNextClickUntil = Date.now() + 500;
    var target = pickElementAtPoint(touch.clientX, touch.clientY);
    if (target) {
      selectTargetElement(target);
    }
  }

  function onShieldSwallow(e) {
    if (!state.active) return;
    e.stopPropagation();
    e.stopImmediatePropagation();
  }

  function onShieldClick(e) {
    if (!state.active) return;
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
    if (Date.now() < state.suppressNextClickUntil) {
      return;
    }
    var target = pickElementAtPoint(e.clientX, e.clientY);
    if (target) {
      selectTargetElement(target);
    }
  }

  function onDocumentBlockInteraction(e) {
    if (!state.active) return;
    if (e.target && e.target.id === SHIELD_ID) return;
    if (e.type === 'focusin' && e.target && typeof e.target.blur === 'function') {
      try { e.target.blur(); } catch (_e) {}
    }
    if (e.cancelable) {
      e.preventDefault();
    }
    e.stopPropagation();
    e.stopImmediatePropagation();
  }

  function start(accentHex) {
    if (accentHex) {
      state.accentHex = normalizeHex(accentHex);
    }
    ensureShield();
    ensureOverlay();
    if (!state.active) {
      state.active = true;
      document.addEventListener('pointerdown', onDocumentBlockInteraction, true);
      document.addEventListener('pointerup', onDocumentBlockInteraction, true);
      document.addEventListener('mousedown', onDocumentBlockInteraction, true);
      document.addEventListener('mouseup', onDocumentBlockInteraction, true);
      document.addEventListener('click', onDocumentBlockInteraction, true);
      document.addEventListener('submit', onDocumentBlockInteraction, true);
      document.addEventListener('focusin', onDocumentBlockInteraction, true);
      window.addEventListener('scroll', scheduleOverlayPositionUpdate, true);
      window.addEventListener('resize', scheduleOverlayPositionUpdate, true);
    } else {
      updateOverlayPosition();
    }
  }

  function setScopeDepth(depth) {
    if (!state.active || state.chain.length === 0) return;
    var maxDepth = state.chain.length - 1;
    var target = Math.max(0, Math.min(maxDepth, depth | 0));
    if (selectAtDepth(target)) return;
    // Step past rejected ancestors toward requested direction
    var dir = target >= state.scopeDepth ? 1 : -1;
    for (var d = target + dir; d >= 0 && d <= maxDepth; d += dir) {
      if (selectAtDepth(d)) return;
    }
  }

  function adjustScope(delta) {
    if (!state.active || state.chain.length === 0) return;
    var d = delta | 0;
    if (d === 0) return;
    var maxDepth = state.chain.length - 1;
    var dir = d > 0 ? 1 : -1;
    var start = Math.max(0, Math.min(maxDepth, state.scopeDepth + d));
    for (var cur = start; cur >= 0 && cur <= maxDepth; cur += dir) {
      if (selectAtDepth(cur)) return;
    }
  }

  function zapOnce() {
    if (state.chain.length === 0) return false;
    var el = state.chain[state.scopeDepth];
    if (!el || !el.isConnected) return false;

    var prevDisplay = el.style.getPropertyValue('display');
    var prevPriority = el.style.getPropertyPriority('display');
    var summary = state.currentSummary || buildSummary(el);

    el.style.setProperty('display', 'none', 'important');
    state.undoStack.push({
      el: el,
      prevDisplay: prevDisplay,
      prevPriority: prevPriority,
      summary: summary
    });

    state.chain = [];
    state.scopeDepth = 0;
    state.currentSelector = '';
    state.currentSummary = '';
    state.currentMatchCount = 0;
    hideOverlay();

    emitBridge('onTemporaryZap', {
      summary: summary,
      temporaryZapCount: state.undoStack.length
    });
    return true;
  }

  function undoLastZap() {
    if (state.undoStack.length === 0) return false;
    var entry = state.undoStack.pop();
    if (entry && entry.el) {
      if (entry.prevDisplay) {
        entry.el.style.setProperty('display', entry.prevDisplay, entry.prevPriority || '');
      } else {
        entry.el.style.removeProperty('display');
      }
    }
    if (state.chain.length > 0) {
      updateOverlayPosition();
    }
    emitBridge('onUndoTemporaryZap', {
      temporaryZapCount: state.undoStack.length
    });
    return Boolean(entry);
  }

  function confirmBlockPermanently() {
    if (state.chain.length === 0) return false;
    var el = state.chain[state.scopeDepth];
    var selector = state.currentSelector || (el ? buildResilientSelector(el) : '');
    if (!selector || isUnsafeBareSelector(selector)) return false;

    var label = state.currentSummary || (el ? buildSummary(el) : selector);
    var matchCount = state.currentMatchCount || safeQueryMatches(selector).length;

    var existingStyle = document.getElementById(STYLE_ID);
    var currentCss = existingStyle ? (existingStyle.textContent || '') : '';
    var newRule = selector + ' { display: none !important; }';
    applyZappedCss(currentCss ? currentCss + '\n' + newRule : newRule);

    state.chain = [];
    state.scopeDepth = 0;
    state.currentSelector = '';
    state.currentSummary = '';
    state.currentMatchCount = 0;
    hideOverlay();

    emitBridge('onPermanentRule', {
      selector: selector,
      label: label,
      matchCount: matchCount
    });
    return true;
  }

  function stop() {
    state.active = false;
    state.chain = [];
    state.scopeDepth = 0;
    state.currentSelector = '';
    state.currentSummary = '';
    state.currentMatchCount = 0;
    state.undoStack = [];
    state.touchMoved = false;
    state.rafPending = false;

    document.removeEventListener('pointerdown', onDocumentBlockInteraction, true);
    document.removeEventListener('pointerup', onDocumentBlockInteraction, true);
    document.removeEventListener('mousedown', onDocumentBlockInteraction, true);
    document.removeEventListener('mouseup', onDocumentBlockInteraction, true);
    document.removeEventListener('click', onDocumentBlockInteraction, true);
    document.removeEventListener('submit', onDocumentBlockInteraction, true);
    document.removeEventListener('focusin', onDocumentBlockInteraction, true);
    window.removeEventListener('scroll', scheduleOverlayPositionUpdate, true);
    window.removeEventListener('resize', scheduleOverlayPositionUpdate, true);

    var shield = document.getElementById(SHIELD_ID);
    if (shield && shield.parentNode) {
      shield.parentNode.removeChild(shield);
    }
    state.shield = null;

    var overlay = document.getElementById(OVERLAY_ID);
    if (overlay && overlay.parentNode) {
      overlay.parentNode.removeChild(overlay);
    }
    state.overlay = null;
  }

  var api = {
    start: start,
    setScopeDepth: setScopeDepth,
    adjustScope: adjustScope,
    zapOnce: zapOnce,
    undoLastZap: undoLastZap,
    confirmBlockPermanently: confirmBlockPermanently,
    stop: stop
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
