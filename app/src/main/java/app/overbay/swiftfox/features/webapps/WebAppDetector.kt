package app.overbay.swiftfox.features.webapps

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

data class PwaThemeSurfaceUpdate(
    val metaThemeColorHex: String?,
    val sampledTopColorHex: String?,
    val sampledBottomColorHex: String?,
    val viewportFitCover: Boolean
)

/**
 * Generates page-inspection and runtime JS scripts for Web App Manifest detection,
 * live `<meta name="theme-color">` + rendered surface sampling, and `beforeinstallprompt` integration.
 */
object WebAppDetector {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildInspectPageScript(bridgeToken: String, expectedOrigin: String): String {
        val tokenLit = json.encodeToString(bridgeToken)
        val originLit = json.encodeToString(expectedOrigin)
        return """
            (function() {
              if (window !== window.top) return;
              if (!location || location.origin !== $originLit) return;
              var ch = window.SwiftfoxNativeChannel;
              if (!ch || typeof ch.postMessage !== 'function') return;

              function absUrl(raw) {
                if (!raw) return '';
                try { return new URL(raw, location.href).href; } catch (_e) { return ''; }
              }
              function metaContent(name) {
                var el = document.querySelector('meta[name="' + name + '" i]');
                return el && el.content ? String(el.content).trim() : '';
              }
              function effectiveThemeColor() {
                var darkMq = false;
                try { darkMq = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches; } catch (_e) {}
                var metas = document.querySelectorAll('meta[name="theme-color" i]');
                var fallback = '';
                for (var i = 0; i < metas.length; i++) {
                  var m = metas[i];
                  var content = m.content ? String(m.content).trim() : '';
                  if (!content) continue;
                  var media = m.media ? String(m.media).trim() : '';
                  if (media) {
                    try {
                      if (window.matchMedia && window.matchMedia(media).matches) return content;
                    } catch (_e) {}
                  } else if (!fallback) {
                    fallback = content;
                  }
                }
                if (fallback) return fallback;
                if (metas.length > 0 && metas[0].content) return String(metas[0].content).trim();
                return '';
              }

              var manifestEl = document.querySelector('link[rel="manifest" i]');
              var manifestHref = manifestEl ? absUrl(manifestEl.getAttribute('href')) : '';
              var touchIcons = [];
              document.querySelectorAll('link[rel="apple-touch-icon" i], link[rel="apple-touch-icon-precomposed" i]').forEach(function(el) {
                var src = absUrl(el.getAttribute('href'));
                if (src) touchIcons.push({ src: src, sizes: el.getAttribute('sizes') || '', type: el.getAttribute('type') || '' });
              });
              var pageIcons = [];
              document.querySelectorAll('link[rel~="icon" i]').forEach(function(el) {
                var src = absUrl(el.getAttribute('href'));
                if (src) pageIcons.push({ src: src, sizes: el.getAttribute('sizes') || '', type: el.getAttribute('type') || '' });
              });

              var payload = {
                channel: 'pwa',
                event: 'metadata',
                token: $tokenLit,
                pageUrl: location.href,
                pageTitle: document.title || '',
                manifestUrl: manifestHref,
                inlineManifestJson: '',
                applicationName: metaContent('application-name') || metaContent('apple-mobile-web-app-title'),
                themeColorRaw: effectiveThemeColor(),
                appleMobileCapable: /^(yes|true)$/i.test(metaContent('apple-mobile-web-app-capable')),
                mobileCapable: /^(yes|true)$/i.test(metaContent('mobile-web-app-capable')),
                touchIcons: touchIcons,
                pageIcons: pageIcons
              };

              function send(p) {
                try { ch.postMessage(JSON.stringify(p)); } catch (_e) {}
              }

              if (manifestHref && /^data:/i.test(manifestHref)) {
                try {
                  var comma = manifestHref.indexOf(',');
                  if (comma > 0) {
                    var head = manifestHref.slice(0, comma);
                    var body = manifestHref.slice(comma + 1);
                    payload.inlineManifestJson = /;base64/i.test(head) ? atob(body) : decodeURIComponent(body);
                  }
                } catch (_e) {}
                send(payload);
                return;
              }

              if (manifestHref && /^https?:/i.test(manifestHref)) {
                var cred = (manifestEl && manifestEl.crossOrigin === 'use-credentials') ? 'include' : 'same-origin';
                fetch(manifestHref, { credentials: cred })
                  .then(function(r) {
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.text();
                  })
                  .then(function(txt) {
                    if (txt && txt.length <= 262144) {
                      payload.inlineManifestJson = txt;
                    }
                    send(payload);
                  })
                  .catch(function() {
                    send(payload);
                  });
              } else {
                send(payload);
              }
            })();
        """.trimIndent()
    }

    fun buildDispatchBeforeInstallPromptScript(bridgeToken: String, expectedOrigin: String): String {
        val tokenLit = json.encodeToString(bridgeToken)
        val originLit = json.encodeToString(expectedOrigin)
        val guardLit = json.encodeToString("__sfPwaPrompt_${bridgeToken.take(12)}")
        return """
            (function() {
              if (window !== window.top) return;
              if (!location || location.origin !== $originLit) return;
              if (window[$guardLit]) return;
              window[$guardLit] = true;
              var ch = window.SwiftfoxNativeChannel;
              if (!ch || typeof ch.postMessage !== 'function') return;
              try {
                var ev = new Event('beforeinstallprompt', { cancelable: true });
                ev.platforms = ['web', 'android'];
                ev.userChoice = new Promise(function(resolve) {
                  window.__sfResolveInstallChoice = resolve;
                });
                ev.prompt = function() {
                  try {
                    ch.postMessage(JSON.stringify({
                      channel: 'pwa',
                      event: 'promptInstall',
                      token: $tokenLit,
                      pageUrl: location.href
                    }));
                  } catch (_e) {}
                  return ev.userChoice;
                };
                window.dispatchEvent(ev);
              } catch (_e) {}
            })();
        """.trimIndent()
    }

    fun buildResolveBeforeInstallChoiceScript(accepted: Boolean): String {
        val outcome = if (accepted) "accepted" else "dismissed"
        return """
            (function() {
              try {
                if (typeof window.__sfResolveInstallChoice === 'function') {
                  window.__sfResolveInstallChoice({ outcome: '$outcome', platform: 'android' });
                  window.__sfResolveInstallChoice = null;
                }
              } catch (_e) {}
            })();
        """.trimIndent()
    }

    fun buildThemeColorObserverScript(bridgeToken: String, expectedOrigin: String): String {
        val tokenLit = json.encodeToString(bridgeToken)
        val originLit = json.encodeToString(expectedOrigin)
        val guardLit = json.encodeToString("__sfThemeObs_${bridgeToken.take(12)}")
        return """
            (function() {
              if (window !== window.top) return;
              if (!location || location.origin !== $originLit) return;
              var ch = window.SwiftfoxNativeChannel;
              if (!ch || typeof ch.postMessage !== 'function') return;
              function readMetaTheme() {
                var metas = document.querySelectorAll('meta[name="theme-color" i]');
                var fallback = '';
                for (var i = 0; i < metas.length; i++) {
                  var m = metas[i];
                  var c = m.content ? String(m.content).trim() : '';
                  if (!c) continue;
                  var media = m.media ? String(m.media).trim() : '';
                  if (media) {
                    try { if (window.matchMedia && window.matchMedia(media).matches) return c; } catch (_e) {}
                  } else if (!fallback) {
                    fallback = c;
                  }
                }
                if (fallback) return fallback;
                return metas.length > 0 && metas[0].content ? String(metas[0].content).trim() : '';
              }
              function parseRgba(raw) {
                if (!raw || raw === 'transparent') return null;
                var m = raw.match(/rgba?\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)(?:\s*,\s*([0-9.]+))?\s*\)/i);
                if (!m) return null;
                var a = m[4] !== undefined ? parseFloat(m[4]) : 1;
                if (!(a > 0.02)) return null;
                return { r: parseFloat(m[1]), g: parseFloat(m[2]), b: parseFloat(m[3]), a: Math.min(1, a) };
              }
              function blendColors(layers) {
                var r = 255, g = 255, b = 255;
                var anyOpaque = false;
                for (var i = layers.length - 1; i >= 0; i--) {
                  var c = layers[i];
                  if (c.a >= 0.99 && !anyOpaque) {
                    r = c.r; g = c.g; b = c.b;
                    anyOpaque = true;
                  } else {
                    r = c.r * c.a + r * (1 - c.a);
                    g = c.g * c.a + g * (1 - c.a);
                    b = c.b * c.a + b * (1 - c.a);
                  }
                }
                if (!anyOpaque && layers.length === 0) return '';
                return 'rgb(' + Math.round(r) + ',' + Math.round(g) + ',' + Math.round(b) + ')';
              }
              function collectBgAt(x, y) {
                var layers = [];
                var seen = [];
                var el = null;
                try { el = document.elementFromPoint(x, y); } catch (_e) {}
                while (el && el.nodeType === 1) {
                  seen.push(el);
                  try {
                    var cs = getComputedStyle(el);
                    var bg = parseRgba(cs.backgroundColor);
                    if (bg) {
                      layers.push(bg);
                      if (bg.a >= 0.99) return blendColors(layers);
                    }
                  } catch (_e) {}
                  el = el.parentElement;
                }
                var roots = [document.body, document.documentElement];
                for (var i = 0; i < roots.length; i++) {
                  var root = roots[i];
                  if (root && seen.indexOf(root) === -1) {
                    try {
                      var rbg = parseRgba(getComputedStyle(root).backgroundColor);
                      if (rbg) {
                        layers.push(rbg);
                        if (rbg.a >= 0.99) return blendColors(layers);
                      }
                    } catch (_e) {}
                  }
                }
                return layers.length > 0 ? blendColors(layers) : '';
              }
              function sampleEdgeColor(isTop) {
                var w = window.innerWidth || document.documentElement.clientWidth || 360;
                var h = window.innerHeight || document.documentElement.clientHeight || 640;
                var y = isTop ? 3 : Math.max(4, h - 6);
                var cMid = collectBgAt(Math.round(w * 0.5), y);
                if (cMid) return cMid;
                var cLeft = collectBgAt(Math.round(w * 0.15), y);
                if (cLeft) return cLeft;
                return collectBgAt(Math.round(w * 0.85), y);
              }
              function isViewportFitCover() {
                var vp = document.querySelector('meta[name="viewport" i]');
                var content = vp && vp.content ? String(vp.content) : '';
                return /viewport-fit\s*=\s*cover/i.test(content);
              }
              var lastSig = null;
              function emit() {
                var meta = readMetaTheme();
                var topBg = sampleEdgeColor(true);
                var bottomBg = sampleEdgeColor(false);
                var cover = isViewportFitCover();
                var sig = meta + '|' + topBg + '|' + bottomBg + '|' + cover;
                if (sig === lastSig) return;
                lastSig = sig;
                try {
                  ch.postMessage(JSON.stringify({
                    channel: 'pwa',
                    event: 'themeSurface',
                    token: $tokenLit,
                    themeColorRaw: meta,
                    sampledTopColorRaw: topBg,
                    sampledBottomColorRaw: bottomBg,
                    viewportFitCover: cover
                  }));
                } catch (_e) {}
              }
              var timer = null;
              function scheduleEmit() {
                if (timer) return;
                timer = setTimeout(function() {
                  timer = null;
                  emit();
                }, 250);
              }
              emit();
              setTimeout(emit, 350);
              if (window[$guardLit]) return;
              window[$guardLit] = true;
              if (typeof MutationObserver === 'function') {
                var headObserver = new MutationObserver(scheduleEmit);
                if (document.head) {
                  headObserver.observe(document.head, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['content', 'media', 'name']
                  });
                }
                var rootObserver = new MutationObserver(scheduleEmit);
                if (document.documentElement) {
                  rootObserver.observe(document.documentElement, {
                    attributes: true,
                    attributeFilter: ['class', 'style', 'data-theme']
                  });
                }
                if (document.body) {
                  rootObserver.observe(document.body, {
                    attributes: true,
                    attributeFilter: ['class', 'style', 'data-theme']
                  });
                }
              }
              window.addEventListener('scroll', scheduleEmit, { passive: true });
              window.addEventListener('resize', scheduleEmit, { passive: true });
              try {
                var mq = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)');
                if (mq && typeof mq.addEventListener === 'function') {
                  mq.addEventListener('change', scheduleEmit);
                }
              } catch (_e) {}
            })();
        """.trimIndent()
    }

    fun parseMetadataMessage(rawJson: String): PageWebAppMetadata? {
        val obj = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return null
        val pageUrl = obj["pageUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return PageWebAppMetadata(
            pageUrl = pageUrl,
            pageTitle = obj["pageTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            manifestUrl = obj["manifestUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            inlineManifestJson = obj["inlineManifestJson"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            applicationName = obj["applicationName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            themeColorRaw = obj["themeColorRaw"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            appleMobileCapable = obj["appleMobileCapable"]?.jsonPrimitive?.booleanOrNull == true,
            mobileCapable = obj["mobileCapable"]?.jsonPrimitive?.booleanOrNull == true,
            touchIcons = parseIconList(obj["touchIcons"]),
            pageIcons = parseIconList(obj["pageIcons"])
        )
    }

    private fun parseIconList(el: kotlinx.serialization.json.JsonElement?): List<ManifestIconCandidate> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val src = o["src"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ManifestIconCandidate(
                src = src,
                sizes = o["sizes"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                type = o["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                purpose = "any"
            )
        }
    }

    fun parseThemeSurfaceMessage(rawJson: String): PwaThemeSurfaceUpdate? {
        val obj = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return null
        val meta = obj["themeColorRaw"]?.jsonPrimitive?.contentOrNull
            ?.let { WebAppManifestParser.normalizeCssColorHex(it) }
        val top = obj["sampledTopColorRaw"]?.jsonPrimitive?.contentOrNull
            ?.let { WebAppManifestParser.normalizeCssColorHex(it) }
        val bottom = obj["sampledBottomColorRaw"]?.jsonPrimitive?.contentOrNull
            ?.let { WebAppManifestParser.normalizeCssColorHex(it) }
        val cover = obj["viewportFitCover"]?.jsonPrimitive?.booleanOrNull == true
        return PwaThemeSurfaceUpdate(
            metaThemeColorHex = meta,
            sampledTopColorHex = top,
            sampledBottomColorHex = bottom,
            viewportFitCover = cover
        )
    }
}
