(function (runtimeKey, bridgeToken) {
  'use strict';
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  var OVERLAY_ID = '__swiftfox_zapper_overlay';
  var STYLE_ID = 'swiftfox-zapped';
  var MAX_ANCESTORS = 6;
  var MAX_PATH_SEGMENTS = 5;
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
    ':root': true,
    '*': true
  };

  function applyZappedCss(cssText) {
    var text = typeof cssText === 'string' ? cssText : '';
    function attach() {
      var style = document.getElementById(STYLE_ID);
      if (!text) {
        if (style) style.textContent = '';
        return true;
      }
      if (!style) {
        var target = document.head || document.documentElement;
        if (!target) return false;
        style = document.createElement('style');
        style.id = STYLE_ID;
        style.type = 'text/css';
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
    chain: [],
    scopeDepth: 0,
    currentSelector: '',
    currentSummary: '',
    currentMatchCount: 0,
    undoStack: [],
    touchStartX: 0,
    touchStartY: 0,
    touchMoved: false,
    suppressNextClickUntil: 0
  };

  function normalizeHex(hex) {
    if (typeof hex !== 'string') return '#E86A17';
    var trimmed = hex.trim();
    if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) return trimmed;
    if (/^#[0-9a-fA-F]{8}$/.test(trimmed)) {
      // Android ARGB hex (#AARRGGBB) -> strip leading alpha channel
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
    if (!trimmed || trimmed.length > 80) return false;
    if (trimmed.indexOf('/*') !== -1 || trimmed.indexOf('*/') !== -1) return false;
    if (/[{};<\x00-\x1f]/.test(trimmed)) return false;
    return true;
  }

  function isStableId(id) {
    if (!id || typeof id !== 'string') return false;
    if (id === OVERLAY_ID || id === STYLE_ID) return false;
    if (id.indexOf('/*') !== -1 || id.indexOf('*/') !== -1) return false;
    if (/[{};<\s\x00-\x1f]/.test(id)) return false;
    if (/^\d+$/.test(id)) return false;
    if (/^css-[a-z0-9_-]+$/i.test(id)) return false;
    if (/^ember\d*$/i.test(id)) return false;
    if (/^:r[a-z0-9]*:$/i.test(id)) return false;
    if (/^_?r_[a-z0-9_]+$/i.test(id)) return false;
    if (/^[0-9a-f]{8,}$/i.test(id)) return false;
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) return false;
    if (/^(?:yui_|ext-gen|gwt-uid|mui-|radix-|headlessui-)[a-z0-9_-]*$/i.test(id)) return false;
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
    return true;
  }

  function getTagName(el) {
    if (!el) return '*';
    return (el.localName || el.tagName || '*').toLowerCase();
  }

  function getStableClasses(el) {
    var classes = [];
    if (!el || !el.classList) return classes;
    for (var i = 0; i < el.classList.length && classes.length < 2; i++) {
      var cls = el.classList[i];
      if (isStableClass(cls)) {
        classes.push(cls);
      }
    }
    return classes;
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

  function getUniqueAnchorSelector(el) {
    if (!el || el === document.body || el === document.documentElement) return null;
    var escapedTag = escapeCssIdent(getTagName(el));

    if (isStableId(el.id)) {
      var idSel = '#' + escapeCssIdent(el.id);
      if (queryCountContaining(idSel, el) === 1) return idSel;
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
      if (sibling.id === OVERLAY_ID) continue;
      if (getTagName(sibling) === tag) {
        sameTagTotal++;
        if (sibling === el) {
          index = sameTagTotal;
        }
      }
    }
    return sameTagTotal > 1 ? escapedTag + ':nth-of-type(' + index + ')' : escapedTag;
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

    var classes = getStableClasses(el);
    if (classes.length > 0) {
      var classSel = escapedTag + classes.map(function (c) {
        return '.' + escapeCssIdent(c);
      }).join('');
      var classCount = queryCountContaining(classSel, el);
      if (classCount > 0 && classCount <= 3) {
        return classSel;
      }
    }

    var segments = [];
    var cur = el;
    while (
      cur &&
      cur.nodeType === 1 &&
      cur !== document.documentElement &&
      segments.length < MAX_PATH_SEGMENTS
    ) {
      if (cur === document.body) {
        segments.unshift('body');
        break;
      }
      if (cur !== el) {
        var anchor = getUniqueAnchorSelector(cur);
        if (anchor) {
          segments.unshift(anchor);
          break;
        }
      }
      var segClasses = getStableClasses(cur);
      var seg = getNthOfTypeSegment(cur);
      if (cur === el && segClasses.length > 0 && seg.indexOf(':nth-of-type(') === -1) {
        seg = escapedTag + segClasses.map(function (c) {
          return '.' + escapeCssIdent(c);
        }).join('');
      }
      segments.unshift(seg);
      cur = cur.parentElement;
    }

    var pathSel = segments.join(' > ');
    if (pathSel && !isUnsafeBareSelector(pathSel) && queryCountContaining(pathSel, el) > 0) {
      return pathSel;
    }

    var fallbackSegments = [];
    cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.documentElement) {
      if (cur === document.body) {
        fallbackSegments.unshift('body');
        break;
      }
      fallbackSegments.unshift(getNthOfTypeSegment(cur));
      cur = cur.parentElement;
    }
    var fallbackSel = fallbackSegments.join(' > ');
    if (fallbackSel && !isUnsafeBareSelector(fallbackSel) && queryCountContaining(fallbackSel, el) > 0) {
      return fallbackSel;
    }
    // Never fall back to a bare generic tag like "div" that could wipe out the entire site
    return '';
  }

  function buildSummary(el) {
    if (!el || el.nodeType !== 1) return '';
    var tag = getTagName(el);
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
    var classes = getStableClasses(el);
    if (classes.length > 0) {
      return tag + '.' + classes.join('.');
    }
    var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
    if (text) {
      return tag + ' "' + text.slice(0, 28) + (text.length > 28 ? '…' : '') + '"';
    }
    return tag;
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
    box.style.setProperty('background', hexToRgba(borderHex, 0.18), 'important');
    box.style.setProperty('border-radius', '10px', 'important');
    box.style.setProperty('margin', '0', 'important');
    box.style.setProperty('padding', '0', 'important');
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

  function selectAtDepth(depth) {
    if (state.chain.length === 0) return;
    var maxDepth = state.chain.length - 1;
    var clamped = Math.max(0, Math.min(maxDepth, depth | 0));
    var el = state.chain[clamped];
    var selector = buildResilientSelector(el);
    if (!selector) {
      return;
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
      matchCount: matchCount
    });
  }

  function buildAncestorChain(tappedEl) {
    var chain = [];
    var cur = tappedEl;
    while (
      cur &&
      cur.nodeType === 1 &&
      cur !== document.body &&
      cur !== document.documentElement &&
      cur.id !== OVERLAY_ID &&
      chain.length <= MAX_ANCESTORS
    ) {
      chain.push(cur);
      cur = cur.parentElement;
    }
    return chain;
  }

  function resolveElementFromTarget(rawTarget, clientX, clientY) {
    var target = rawTarget;
    if (typeof clientX === 'number' && typeof clientY === 'number' && typeof document.elementsFromPoint === 'function') {
      var stack = document.elementsFromPoint(clientX, clientY);
      for (var i = 0; i < stack.length; i++) {
        var cand = stack[i];
        if (cand && cand.id !== OVERLAY_ID) {
          target = cand;
          break;
        }
      }
    }
    if (target && target.nodeType !== 1) {
      target = target.parentElement;
    }
    if (!target) return null;
    if (target.id === OVERLAY_ID || (target.closest && target.closest('#' + OVERLAY_ID))) {
      return null;
    }
    if (target === document.body || target === document.documentElement) {
      return null;
    }
    return target;
  }

  function selectTargetElement(target) {
    if (!target) return;
    var chain = buildAncestorChain(target);
    if (chain.length === 0) return;

    state.chain = chain;
    ensureOverlay();
    selectAtDepth(0);
  }

  function onTouchStart(e) {
    if (!state.active || !e.touches || e.touches.length !== 1) return;
    state.touchStartX = e.touches[0].clientX;
    state.touchStartY = e.touches[0].clientY;
    state.touchMoved = false;
  }

  function onTouchMove(e) {
    if (!state.active || !e.touches || e.touches.length !== 1) return;
    var dx = Math.abs(e.touches[0].clientX - state.touchStartX);
    var dy = Math.abs(e.touches[0].clientY - state.touchStartY);
    if (dx > 12 || dy > 12) {
      state.touchMoved = true;
    }
  }

  function onTouchEnd(e) {
    if (!state.active || state.touchMoved) return;
    var touch = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0] : null;
    var target = resolveElementFromTarget(
      e.target,
      touch ? touch.clientX : undefined,
      touch ? touch.clientY : undefined
    );
    if (!target) return;

    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
    state.suppressNextClickUntil = Date.now() + 450;
    selectTargetElement(target);
  }

  function onClick(e) {
    if (!state.active) return;
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    if (Date.now() < state.suppressNextClickUntil) {
      return;
    }
    var target = resolveElementFromTarget(e.target, e.clientX, e.clientY);
    selectTargetElement(target);
  }

  function onBlockInteraction(e) {
    if (!state.active) return;
    if (e.type === 'focusin' && e.target && typeof e.target.blur === 'function') {
      try { e.target.blur(); } catch (_e) {}
    }
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
  }

  function start(accentHex) {
    if (accentHex) {
      state.accentHex = normalizeHex(accentHex);
    }
    ensureOverlay();
    if (!state.active) {
      state.active = true;
      document.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
      document.addEventListener('touchmove', onTouchMove, { capture: true, passive: true });
      document.addEventListener('touchend', onTouchEnd, true);
      document.addEventListener('click', onClick, true);
      document.addEventListener('submit', onBlockInteraction, true);
      document.addEventListener('focusin', onBlockInteraction, true);
      window.addEventListener('scroll', updateOverlayPosition, true);
      window.addEventListener('resize', updateOverlayPosition, true);
    } else {
      updateOverlayPosition();
    }
  }

  function setScopeDepth(depth) {
    if (!state.active || state.chain.length === 0) return;
    selectAtDepth(depth);
  }

  function adjustScope(delta) {
    if (!state.active || state.chain.length === 0) return;
    selectAtDepth(state.scopeDepth + (delta | 0));
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

    document.removeEventListener('touchstart', onTouchStart, true);
    document.removeEventListener('touchmove', onTouchMove, true);
    document.removeEventListener('touchend', onTouchEnd, true);
    document.removeEventListener('click', onClick, true);
    document.removeEventListener('submit', onBlockInteraction, true);
    document.removeEventListener('focusin', onBlockInteraction, true);
    window.removeEventListener('scroll', updateOverlayPosition, true);
    window.removeEventListener('resize', updateOverlayPosition, true);

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
    stop: stop,
    buildResilientSelector: buildResilientSelector
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
