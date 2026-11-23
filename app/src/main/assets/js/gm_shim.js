(function () {
  'use strict';
  if (window.__swiftfoxGmRuntime) {
    return;
  }

  const pendingXhr = Object.create(null);
  let xhrSeq = 0;

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

  function resolveXhr(reqId, eventName, payloadJson) {
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
    const response = {
      status: typeof payload.status === 'number' ? payload.status : 0,
      statusText: typeof payload.statusText === 'string' ? payload.statusText : '',
      responseText: text,
      response: text,
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

  function createApi(scriptId, meta, initialStorage, grants) {
    const id = scriptId == null ? '' : String(scriptId);
    const safeMeta = meta && typeof meta === 'object' ? meta : {};
    const storage = cloneStorage(initialStorage);
    const normalizedGrants = Array.isArray(grants)
      ? grants.slice()
      : (Array.isArray(safeMeta.grants) ? safeMeta.grants.slice() : []);

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

    function GM_addStyle(css) {
      const style = document.createElement('style');
      style.setAttribute('data-swiftfox-gm', id);
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

    function GM_getValue(key, defaultValue) {
      const k = String(key);
      if (hasOwn(storage, k) && storage[k] !== undefined) {
        return storage[k];
      }
      return defaultValue;
    }

    function GM_setValue(key, value) {
      const k = String(key);
      if (k === '__proto__') {
        return;
      }
      storage[k] = value;
      const serialized = JSON.stringify(value === undefined ? null : value);
      const bridge = window.SwiftfoxGmBridge;
      if (bridge && typeof bridge.onSetValue === 'function') {
        bridge.onSetValue(id, k, serialized);
      }
    }

    function GM_deleteValue(key) {
      const k = String(key);
      delete storage[k];
      const bridge = window.SwiftfoxGmBridge;
      if (bridge && typeof bridge.onDeleteValue === 'function') {
        bridge.onDeleteValue(id, k);
      }
    }

    function GM_listValues() {
      return Object.keys(storage);
    }

    function GM_xmlhttpRequest(details) {
      const opts = details && typeof details === 'object' ? details : {};
      const reqId = 'xhr_' + (++xhrSeq) + '_' + Date.now();
      const entry = {
        aborted: false,
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

      const bridge = window.SwiftfoxGmBridge;
      if (bridge && typeof bridge.onXmlHttpRequest === 'function') {
        bridge.onXmlHttpRequest(id, reqId, JSON.stringify(payload));
      } else {
        setTimeout(function () {
          resolveXhr(
            reqId,
            'onerror',
            JSON.stringify({
              status: 0,
              statusText: 'SwiftfoxGmBridge unavailable',
              responseText: '',
              responseHeaders: '',
              finalUrl: payload.url,
              readyState: 4,
              error: 'SwiftfoxGmBridge unavailable'
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

    const GM = {
      info: GM_info,
      addStyle: function (css) {
        return Promise.resolve(GM_addStyle(css));
      },
      getValue: function (key, defaultValue) {
        return Promise.resolve(GM_getValue(key, defaultValue));
      },
      setValue: function (key, value) {
        GM_setValue(key, value);
        return Promise.resolve();
      },
      deleteValue: function (key) {
        GM_deleteValue(key);
        return Promise.resolve();
      },
      listValues: function () {
        return Promise.resolve(GM_listValues());
      },
      xmlHttpRequest: function (details) {
        const opts = details && typeof details === 'object' ? details : {};
        let handle = null;
        const promise = new Promise(function (resolve, reject) {
          const wrapped = Object.assign({}, opts, {
            onload: function (res) {
              if (typeof opts.onload === 'function') {
                opts.onload(res);
              }
              resolve(res);
            },
            onerror: function (res) {
              if (typeof opts.onerror === 'function') {
                opts.onerror(res);
              }
              reject(res);
            },
            ontimeout: function (res) {
              if (typeof opts.ontimeout === 'function') {
                opts.ontimeout(res);
              }
              reject(res);
            },
            onabort: function (res) {
              if (typeof opts.onabort === 'function') {
                opts.onabort(res);
              }
              reject(res);
            }
          });
          handle = GM_xmlhttpRequest(wrapped);
        });
        promise.abort = handle && typeof handle.abort === 'function' ? handle.abort : function () {};
        return promise;
      }
    };

    return {
      GM_info: GM_info,
      GM_addStyle: GM_addStyle,
      GM_getValue: GM_getValue,
      GM_setValue: GM_setValue,
      GM_deleteValue: GM_deleteValue,
      GM_listValues: GM_listValues,
      GM_xmlhttpRequest: GM_xmlhttpRequest,
      GM: GM
    };
  }

  window.__swiftfoxGmRuntime = {
    createApi: createApi,
    resolveXhr: resolveXhr
  };
})();
