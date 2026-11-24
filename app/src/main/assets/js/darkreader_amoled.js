(function () {
  var AMOLED_MAX_CHROMA = 20;
  var AMOLED_BG_MAX_LUMINANCE = 0.11;
  var AMOLED_TEXT_MIN_LUMINANCE = 0.78;

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
      prop.indexOf('--darkreader-bg--') === 0 ||
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

  function findDarkReaderRootRule(styleSelector) {
    try {
      var styleEl = document.querySelector(styleSelector);
      var sheet = styleEl && styleEl.sheet;
      var rules = sheet && sheet.cssRules;
      if (!rules) return null;
      for (var i = 0; i < rules.length; i++) {
        if (rules[i].selectorText === ':root' && rules[i].style) {
          return { sheet: sheet, rule: rules[i] };
        }
      }
      return { sheet: sheet, rule: null };
    } catch (e) {}
    return null;
  }

  function sweepAmoledStyleProps(styleObj, rawSet, config) {
    if (!styleObj || typeof rawSet !== 'function') return;
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

  return function createAmoledController(getConfig, isDarkReaderActive, observeWindowLoad) {
    var amoledHeadObserver = null;
    var amoledSweepScheduled = false;
    var amoledLoadListenerAttached = false;
    var hookedVarStyleObj = null;
    var hookedVarOrigSetProperty = null;
    var hookedRootVarsSheet = null;
    var hookedRootVarsOrigInsertRule = null;

    function patchRootVarsRule(config) {
      if (!config || !config.amoled) return;
      var found = findDarkReaderRootRule('style.darkreader--root-vars');
      if (!found || !found.sheet) return;
      var sheet = found.sheet;

      if (hookedRootVarsSheet !== sheet && typeof sheet.insertRule === 'function') {
        if (hookedRootVarsSheet && hookedRootVarsOrigInsertRule) {
          try { delete hookedRootVarsSheet.insertRule; } catch (e) {}
        }
        var origInsert = sheet.insertRule;
        hookedRootVarsSheet = sheet;
        hookedRootVarsOrigInsertRule = origInsert;
        try {
          Object.defineProperty(sheet, 'insertRule', {
            configurable: true,
            writable: true,
            value: function (ruleText, index) {
              var res = index !== undefined
                ? origInsert.call(this, ruleText, index)
                : origInsert.call(this, ruleText);
              var cfg = getConfig();
              if (cfg && cfg.enabled && cfg.amoled) {
                try {
                  var rules = this.cssRules;
                  for (var k = 0; k < rules.length; k++) {
                    if (rules[k].selectorText === ':root' && rules[k].style) {
                      sweepAmoledStyleProps(rules[k].style, rules[k].style.setProperty, cfg);
                    }
                  }
                } catch (e) {}
              }
              return res;
            }
          });
        } catch (e) {}
      }

      if (found.rule && found.rule.style) {
        sweepAmoledStyleProps(found.rule.style, found.rule.style.setProperty, config);
      }
    }

    function patchVariablesRule(config) {
      if (!config || !config.amoled) return;
      var found = findDarkReaderRootRule('style.darkreader--variables');
      var rootRule = found && found.rule;
      if (rootRule && rootRule.style) {
        var styleObj = rootRule.style;

        if (hookedVarStyleObj !== styleObj && typeof styleObj.setProperty === 'function') {
          if (hookedVarStyleObj && hookedVarOrigSetProperty) {
            try { delete hookedVarStyleObj.setProperty; } catch (e) {}
          }
          var origSet = styleObj.setProperty;
          hookedVarStyleObj = styleObj;
          hookedVarOrigSetProperty = origSet;
          try {
            Object.defineProperty(styleObj, 'setProperty', {
              configurable: true,
              writable: true,
              value: function (prop, val, priority) {
                var nextVal = normalizeAmoledVarValue(prop, val, getConfig());
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
        sweepAmoledStyleProps(styleObj, rawSet, config);
      }

      patchRootVarsRule(config);
    }

    function scheduleSweep() {
      if (amoledSweepScheduled) return;
      amoledSweepScheduled = true;
      var run = function () {
        amoledSweepScheduled = false;
        var cfg = getConfig();
        if (cfg && cfg.enabled && cfg.amoled && isDarkReaderActive()) {
          patchVariablesRule(cfg);
        }
      };
      if (typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(run);
      } else {
        setTimeout(run, 16);
      }
    }

    function teardown() {
      amoledSweepScheduled = false;
      if (amoledHeadObserver) {
        try { amoledHeadObserver.disconnect(); } catch (e) {}
        amoledHeadObserver = null;
      }
      if (hookedVarStyleObj && hookedVarOrigSetProperty) {
        try { delete hookedVarStyleObj.setProperty; } catch (e) {}
      }
      hookedVarStyleObj = null;
      hookedVarOrigSetProperty = null;
      if (hookedRootVarsSheet && hookedRootVarsOrigInsertRule) {
        try { delete hookedRootVarsSheet.insertRule; } catch (e) {}
      }
      hookedRootVarsSheet = null;
      hookedRootVarsOrigInsertRule = null;
    }

    function install(config) {
      teardown();
      if (!config || !config.amoled) return;
      patchVariablesRule(config);
      var headOrRoot = document.head || document.documentElement;
      if (typeof MutationObserver === 'function' && headOrRoot) {
        try {
          amoledHeadObserver = new MutationObserver(function () {
            scheduleSweep();
          });
          amoledHeadObserver.observe(headOrRoot, {
            childList: true,
            subtree: !document.head
          });
        } catch (e) {
          amoledHeadObserver = null;
        }
      }
      if (observeWindowLoad && !amoledLoadListenerAttached && document.readyState !== 'complete') {
        amoledLoadListenerAttached = true;
        window.addEventListener('load', function () {
          amoledLoadListenerAttached = false;
          var cfg = getConfig();
          if (cfg && cfg.enabled && cfg.amoled && isDarkReaderActive()) {
            patchVariablesRule(cfg);
          }
        }, { once: true });
      }
    }

    return {
      parseColor: parseColor,
      patchVariablesRule: patchVariablesRule,
      install: install,
      teardown: teardown
    };
  };
})()
