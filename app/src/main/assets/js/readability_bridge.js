(function () {
  if (typeof window.__swiftfoxIsReadable === 'function' &&
      typeof window.__swiftfoxExtractArticle === 'function') {
    return;
  }

  function resolveRelativeUrl(rawUrl, baseUrl) {
    if (!rawUrl) return rawUrl;
    var trimmed = String(rawUrl).trim();
    if (!trimmed) return trimmed;
    if (/^(#|javascript:|data:|blob:|mailto:|tel:)/i.test(trimmed)) {
      return trimmed;
    }
    try {
      return new URL(trimmed, baseUrl).href;
    } catch (e) {
      return rawUrl;
    }
  }

  function hasVisiblePasswordInput() {
    var pwInputs = document.querySelectorAll('input[type="password"]');
    for (var i = 0; i < pwInputs.length; i++) {
      var input = pwInputs[i];
      if (input.offsetWidth > 0 || input.offsetHeight > 0 ||
          (input.getClientRects && input.getClientRects().length > 0)) {
        return true;
      }
    }
    return false;
  }

  window.__swiftfoxIsReadable = function () {
    try {
      if (!document || !document.body) {
        return false;
      }

      var pathname = (window.location && window.location.pathname)
        ? window.location.pathname.toLowerCase()
        : '';
      if (/(?:^|\/)(login|signin|sign-in|signup|sign-up|register|auth|oauth|sso|checkout|cart|search|settings|preferences|forgot-password|reset-password)(?:\/|$)/.test(pathname)) {
        return false;
      }

      var paragraphs = document.querySelectorAll('p');
      var paragraphLength = 0;
      var paragraphCount = 0;
      for (var i = 0; i < paragraphs.length; i++) {
        var p = paragraphs[i];
        if (p.closest) {
          if (p.closest('nav, footer')) continue;
          var parentForm = p.closest('form');
          if (parentForm && parentForm.querySelector('input[type="password"], input[type="email"]')) {
            continue;
          }
        }
        var pText = (p.textContent || '').trim();
        if (pText.length > 0) {
          paragraphLength += pText.length;
          paragraphCount++;
        }
      }

      var containers = document.querySelectorAll('article, main');
      var maxContainerLength = 0;
      var articleLength = 0;
      for (var j = 0; j < containers.length; j++) {
        var el = containers[j];
        var elText = (el.textContent || '').trim();
        if (elText.length > maxContainerLength) {
          maxContainerLength = elText.length;
        }
        if (el.tagName === 'ARTICLE' &&
            !(el.parentElement && el.parentElement.closest && el.parentElement.closest('article'))) {
          articleLength += elText.length;
        }
      }

      if (hasVisiblePasswordInput() && paragraphLength < 800 && maxContainerLength < 800) {
        return false;
      }

      if ((paragraphLength >= 420 || articleLength >= 420) && paragraphCount >= 2) {
        return true;
      }
      if (maxContainerLength >= 600) {
        return true;
      }

      return false;
    } catch (e) {
      return false;
    }
  };

  window.__swiftfoxExtractArticle = function () {
    try {
      var ReadabilityCtor = typeof window.Readability === 'function'
        ? window.Readability
        : (typeof Readability === 'function' ? Readability : null);

      if (!ReadabilityCtor || !document || !document.documentElement) {
        return null;
      }

      var clone = document.cloneNode(true);
      var baseUrl = document.baseURI || (window.location ? window.location.href : '');

      if (baseUrl) {
        var anchors = clone.querySelectorAll('a[href]');
        for (var i = 0; i < anchors.length; i++) {
          var rawHref = anchors[i].getAttribute('href');
          var resolvedHref = resolveRelativeUrl(rawHref, baseUrl);
          if (resolvedHref && resolvedHref !== rawHref) {
            anchors[i].setAttribute('href', resolvedHref);
          }
        }

        var images = clone.querySelectorAll('img[src]');
        for (var j = 0; j < images.length; j++) {
          var rawSrc = images[j].getAttribute('src');
          var resolvedSrc = resolveRelativeUrl(rawSrc, baseUrl);
          if (resolvedSrc && resolvedSrc !== rawSrc) {
            images[j].setAttribute('src', resolvedSrc);
          }
        }
      }

      var stripNodes = clone.querySelectorAll('script, style, iframe, noscript');
      for (var k = 0; k < stripNodes.length; k++) {
        var node = stripNodes[k];
        if (node.parentNode) {
          node.parentNode.removeChild(node);
        }
      }

      var article = new ReadabilityCtor(clone).parse();
      if (!article || !article.content || !String(article.content).trim()) {
        return null;
      }

      var plainText = (article.textContent || '').trim();
      if (!plainText) {
        return null;
      }

      var wordCount = plainText.split(/\s+/).filter(Boolean).length;
      if (wordCount === 0) {
        return null;
      }

      var readingTimeMinutes = Math.max(1, Math.round(wordCount / 220));

      return JSON.stringify({
        title: (article.title || document.title || '').trim(),
        byline: article.byline ? String(article.byline).trim() || null : null,
        siteName: article.siteName ? String(article.siteName).trim() || null : null,
        excerpt: article.excerpt ? String(article.excerpt).trim() || null : null,
        htmlContent: article.content,
        wordCount: wordCount,
        readingTimeMinutes: readingTimeMinutes,
        url: window.location ? window.location.href : ''
      });
    } catch (e) {
      return null;
    }
  };
})();
