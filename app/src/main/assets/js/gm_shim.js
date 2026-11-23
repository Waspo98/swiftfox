(function (runtimeKey, bridgeToken) {
  'use strict';
  if (!runtimeKey || !bridgeToken || Object.prototype.hasOwnProperty.call(window, runtimeKey)) {
    return;
  }

  const pendingXhr = Object.create(null);
  let xhrSeq = 0;

  function postGm(action, fields) {
    const ch = window.SwiftfoxNativeChannel;
    if (!ch || typeof ch.postMessage !== 'function') {
      return false;
    }
    const payload = Object.assign({ channel: 'gm', token: bridgeToken, action: action }, fields);
    ch.postMessage(JSON.stringify(payload));
    return true;
  }

  function hasOwn(obj, key) {
    return Object.prototype.hasOwnProperty.call(obj, key);
  }

  function cloneStorage(initialStorage) {
    const storage = Object.create(null);
    if (initialStorage && typeof initialStorage === 'object') {
      const keys = Object.keys(initialStorage);
      for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        if (k !== '__proto__') {
          storage[k] = initialStorage[k];
        }
      }
    }
    return storage;
  }

  function resolveXhrInternal(reqId, eventName, payloadJson) {
    const entry = pendingXhr[reqId];
    if (!entry || entry.aborted) {
      return;
    }

    let payload = {};
    if (typeof payloadJson === 'string') {
      try {
        payload = JSON.parse(payloadJson) || {};
      } catch (_e) {
        payload = {};
      }
    } else if (payloadJson && typeof payloadJson === 'object') {
      payload = payloadJson;
    }

    const isTerminal =
      eventName === 'onload' ||
      eventName === 'onerror' ||
      eventName === 'ontimeout' ||
      eventName === 'onabort';
    if (isTerminal) {
      delete pendingXhr[reqId];
    }

    const text = typeof payload.responseText === 'string' ? payload.responseText : '';
    let parsedResponse = text;
    if (entry.responseType === 'json') {
      try {
        parsedResponse = text ? JSON.parse(text) : null;
      } catch (_e) {
        parsedResponse = null;
      }
    }

    const response = {
      status: typeof payload.status === 'number' ? payload.status : 0,
      statusText: typeof payload.statusText === 'string' ? payload.statusText : '',
      responseText: text,
      response: parsedResponse,
      responseHeaders: typeof payload.responseHeaders === 'string' ? payload.responseHeaders : '',
      finalUrl: typeof payload.finalUrl === 'string' ? payload.finalUrl : '',
      readyState: typeof payload.readyState === 'number' ? payload.readyState : (isTerminal ? 4 : 0),
      error: payload.error
    };

    try {
      if (eventName === 'onload' && typeof entry.onreadystatechange === 'function') {
        entry.onreadystatechange(response);
      }
      const cb = entry[eventName];
      if (typeof cb === 'function') {
        cb(response);
      }
    } catch (err) {
      console.error('[Swiftfox GM_xmlhttpRequest callback]', err);
    }
  }

  function resolveXhr(token, reqId, eventName, payloadJson) {
    if (!bridgeToken || token !== bridgeToken) {
      return;
    }
    resolveXhrInternal(reqId, eventName, payloadJson);
  }

  function createApi(token, scriptId, meta, initialStorage, grants) {
    if (!bridgeToken || token !== bridgeToken) {
      throw new Error('Unauthorized Swiftfox GM runtime access');
    }

    const id = scriptId == null ? '' : String(scriptId);
    const safeMeta = meta && typeof meta === 'object' ? meta : {};
    const storage = cloneStorage(initialStorage);
    const normalizedGrants = Array.isArray(grants)
      ? grants.slice()
      : (Array.isArray(safeMeta.grants) ? safeMeta.grants.slice() : []);

    const hasNoneGrant = normalizedGrants.indexOf('none') !== -1;
    const isLegacyDefaultGrants = normalizedGrants.length === 0;

    function isGranted(underscoreName, dotName) {
      if (hasNoneGrant) {
        return false;
      }
      if (isLegacyDefaultGrants) {
        // Legacy scripts with no @grant receive basic style/storage APIs, never network XHR.
        return underscoreName !== 'GM_xmlhttpRequest';
      }
      return (
        normalizedGrants.indexOf(underscoreName) !== -1 ||
        normalizedGrants.indexOf(dotName) !== -1
      );
    }

    const GM_info = {
      script: {
        id: id || safeMeta.id || '',
        name: safeMeta.name || '',
        namespace: safeMeta.namespace || '',
        version: safeMeta.version || '1.0',
        description: safeMeta.description || '',
        matches: Array.isArray(safeMeta.matches) ? safeMeta.matches.slice() : [],
        excludes: Array.isArray(safeMeta.excludes) ? safeMeta.excludes.slice() : [],
        'run-at': safeMeta.runAt || 'document-end',
        runAt: safeMeta.runAt || 'document-end',
        grants: normalizedGrants
      },
      scriptHandler: 'Swiftfox',
      version: '1.0'
    };

    function implAddStyle(css) {
      const style = document.createElement('style');
      style.setAttribute('data-swiftfox-style', '1');
      style.textContent = css == null ? '' : String(css);
      const target = document.head || document.documentElement;
      if (target) {
        target.appendChild(style);
      } else if (typeof document.addEventListener === 'function') {
        document.addEventListener(
          'DOMContentLoaded',
          function () {
            const readyTarget = document.head || document.documentElement;
            if (readyTarget) {
              readyTarget.appendChild(style);
            }
          },
          { once: true }
        );
      }
      return style;
    }

    function implGetValue(key, defaultValue) {
      const k = String(key);
      if (hasOwn(storage, k) && storage[k] !== undefined) {
        return storage[k];
      }
      return defaultValue;
    }

    function implSetValue(key, value) {
      const k = String(key);
      if (k === '__proto__') {
        return;
      }
      let serialized;
      try {
        serialized = JSON.stringify(value === undefined ? null : value);
      } catch (_e) {
        return;
      }
      if (typeof serialized !== 'string') {
        return;
      }
      storage[k] = value;
      postGm('setValue', {
        scriptId: id,
        key: k,
        valueJson: serialized
      });
    }

    function implDeleteValue(key) {
      const k = String(key);
      delete storage[k];
      postGm('deleteValue', {
        scriptId: id,
        key: k
      });
    }

    function implListValues() {
      return Object.keys(storage);
    }

    function implXmlHttpRequest(details) {
      const opts = details && typeof details === 'object' ? details : {};
      const reqId = 'xhr_' + (++xhrSeq) + '_' + Date.now();
      const entry = {
        aborted: false,
        responseType: typeof opts.responseType === 'string' ? opts.responseType.toLowerCase() : '',
        onload: typeof opts.onload === 'function' ? opts.onload : null,
        onerror: typeof opts.onerror === 'function' ? opts.onerror : null,
        ontimeout: typeof opts.ontimeout === 'function' ? opts.ontimeout : null,
        onreadystatechange: typeof opts.onreadystatechange === 'function' ? opts.onreadystatechange : null,
        onabort: typeof opts.onabort === 'function' ? opts.onabort : null
      };
      pendingXhr[reqId] = entry;

      const payload = {
        method: opts.method ? String(opts.method).toUpperCase() : 'GET',
        url: opts.url ? String(opts.url) : '',
        headers: opts.headers && typeof opts.headers === 'object' ? opts.headers : {},
        data: opts.data !== undefined && opts.data !== null ? String(opts.data) : null,
        timeout: typeof opts.timeout === 'number' ? opts.timeout : 15000
      };

      const sent = postGm('xhr', {
        scriptId: id,
        reqId: reqId,
        requestJson: JSON.stringify(payload)
      });
      if (!sent) {
        setTimeout(function () {
          resolveXhrInternal(
            reqId,
            'onerror',
            JSON.stringify({
              status: 0,
              statusText: 'SwiftfoxNativeChannel unavailable',
              responseText: '',
              responseHeaders: '',
              finalUrl: payload.url,
              readyState: 4,
              error: 'SwiftfoxNativeChannel unavailable'
            })
          );
        }, 0);
      }

      return {
        abort: function () {
          const pending = pendingXhr[reqId];
          if (!pending) {
            return;
          }
          pending.aborted = true;
          delete pendingXhr[reqId];
          try {
            postGm('abortXhr', { reqId: reqId });
          } catch (_e) {}
          if (typeof pending.onabort === 'function') {
            try {
              pending.onabort({
                status: 0,
                statusText: 'Aborted',
                responseText: '',
                response: '',
                responseHeaders: '',
                finalUrl: payload.url,
                readyState: 4
              });
            } catch (err) {
              console.error('[Swiftfox GM_xmlhttpRequest onabort]', err);
            }
          }
        }
      };
    }

    const canAddStyle = isGranted('GM_addStyle', 'GM.addStyle');
    const canGetValue = isGranted('GM_getValue', 'GM.getValue');
    const canSetValue = isGranted('GM_setValue', 'GM.setValue');
    const canDeleteValue = isGranted('GM_deleteValue', 'GM.deleteValue');
    const canListValues = isGranted('GM_listValues', 'GM.listValues');
    const canXhr = isGranted('GM_xmlhttpRequest', 'GM.xmlHttpRequest');

    const GM = {
      info: GM_info
    };
    if (canAddStyle) {
      GM.addStyle = function (css) {
        return Promise.resolve(implAddStyle(css));
      };
    }
    if (canGetValue) {
      GM.getValue = function (key, defaultValue) {
        return Promise.resolve(implGetValue(key, defaultValue));
      };
    }
    if (canSetValue) {
      GM.setValue = function (key, value) {
        implSetValue(key, value);
        return Promise.resolve();
      };
    }
    if (canDeleteValue) {
      GM.deleteValue = function (key) {
        implDeleteValue(key);
        return Promise.resolve();
      };
    }
    if (canListValues) {
      GM.listValues = function () {
        return Promise.resolve(implListValues());
      };
    }
    if (canXhr) {
      GM.xmlHttpRequest = function (details) {
        const opts = details && typeof details === 'object' ? details : {};
        let handle = null;
        const promise = new Promise(function (resolve, reject) {
          const wrapped = Object.assign({}, opts, {
            onload: function (res) {
              try {
                if (typeof opts.onload === 'function') {
                  opts.onload(res);
                }
              } finally {
                resolve(res);
              }
            },
            onerror: function (res) {
              try {
                if (typeof opts.onerror === 'function') {
                  opts.onerror(res);
                }
              } finally {
                reject(res);
              }
            },
            ontimeout: function (res) {
              try {
                if (typeof opts.ontimeout === 'function') {
                  opts.ontimeout(res);
                }
              } finally {
                reject(res);
              }
            },
            onabort: function (res) {
              try {
                if (typeof opts.onabort === 'function') {
                  opts.onabort(res);
                }
              } finally {
                reject(res);
              }
            }
          });
          handle = implXmlHttpRequest(wrapped);
        });
        promise.abort = handle && typeof handle.abort === 'function' ? handle.abort : function () {};
        return promise;
      };
    }

    return {
      GM_info: GM_info,
      unsafeWindow: window,
      GM_addStyle: canAddStyle ? implAddStyle : undefined,
      GM_getValue: canGetValue ? implGetValue : undefined,
      GM_setValue: canSetValue ? implSetValue : undefined,
      GM_deleteValue: canDeleteValue ? implDeleteValue : undefined,
      GM_listValues: canListValues ? implListValues : undefined,
      GM_xmlhttpRequest: canXhr ? implXmlHttpRequest : undefined,
      GM: GM
    };
  }

  const runtimeApi = {
    createApi: createApi,
    resolveXhr: resolveXhr
  };
  try {
    Object.defineProperty(window, runtimeKey, {
      value: runtimeApi,
      writable: false,
      configurable: false,
      enumerable: false
    });
  } catch (e) {
    window[runtimeKey] = runtimeApi;
  }
})
