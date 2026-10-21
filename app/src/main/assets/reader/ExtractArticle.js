/* On-demand extraction of article content already delivered in this document.
 * Sources inspired by BPC's JSON-LD/NEXT_DATA recovery; no extension APIs or
 * source-page rewriting. Output still passes through ReaderDocument's safelist.
 */
function shellAlphaExtractArticle(doc, expectedUrl) {
  "use strict";
  var MAX_CONTENT = 350000;
  if (!doc.body || doc.getElementsByTagName("*").length > 20000) return null;

  var regular = null;
  try {
    var readable = doc.cloneNode(true);
    // Hydration/advertising code is not article text. CNN's page can contain
    // megabytes of scripts and only a few thousand readable characters.
    readable.querySelectorAll("script,style,noscript,template").forEach(function(el) { el.remove(); });
    if (readable.body.textContent.length <= 1500000) {
      regular = new Readability(readable, {
        maxElemsToParse: 20000, charThreshold: 200
      }).parse();
    }
  } catch (_) { /* Structured article data can still be readable. */ }
  if (regular && (!regular.content || regular.content.length > MAX_CONTENT)) regular = null;
  var regularText = regular ? normalize(regular.textContent || "") : "";
  var candidates = [];

  function normalize(text) { return text.replace(/\s+/g, " ").trim(); }
  function matchesUrl(value) {
    if (typeof value !== "string" || !value) return false;
    try {
      var a = new URL(value, expectedUrl), b = new URL(expectedUrl);
      a.hash = ""; b.hash = "";
      return a.href === b.href;
    } catch (_) { return false; }
  }
  function escape(text) {
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function addText(text, title, byline) {
    // Do not guess how arbitrary HTML or nested CMS objects should be decoded.
    if (typeof text !== "string" || text.length > 100000 || /<\/?[a-z][^>]*>/i.test(text)) return;
    var normalized = normalize(text);
    if (normalized.length < 200) return;
    var html = "<p>" + escape(text.trim()).replace(/\r?\n\s*\r?\n/g, "</p><p>")
      .replace(/\r?\n/g, "<br>") + "</p>";
    if (html.length <= MAX_CONTENT) candidates.push({
      title: typeof title === "string" ? title.slice(0, 500) : "",
      byline: typeof byline === "string" ? byline.slice(0, 300) : "",
      content: html, text: normalized
    });
  }
  function authorName(author) {
    if (Array.isArray(author)) author = author[0];
    return typeof author === "string" ? author : author && author.name;
  }

  // Only explicit article body fields, never descriptions, search results, or
  // recursive guesses at keys named "text". Cap the total JSON work per click.
  var scripts = doc.querySelectorAll('script[type="application/ld+json"],script#__NEXT_DATA__');
  var remaining = 1000000;
  for (var i = 0; i < Math.min(scripts.length, 20); i++) {
    var raw = scripts[i].textContent;
    if (raw.length > 500000 || raw.length > remaining) continue;
    remaining -= raw.length;
    var data;
    try { data = JSON.parse(raw); } catch (_) { continue; }
    if (scripts[i].id === "__NEXT_DATA__") {
      var article = data && data.props && data.props.pageProps && data.props.pageProps.article;
      if (article && matchesUrl(article.url)) addText(article.body, article.title, authorName(article.author));
      continue;
    }
    var queue = Array.isArray(data) ? data.slice(0, 500) : [data];
    for (var cursor = 0; cursor < queue.length && cursor < 500; cursor++) {
      var node = queue[cursor];
      if (!node || typeof node !== "object") continue;
      if (Array.isArray(node["@graph"])) {
        queue = queue.concat(node["@graph"].slice(0, Math.max(0, 500 - queue.length)));
      }
      if (node.mainEntity && typeof node.mainEntity === "object" && queue.length < 500)
        queue.push(node.mainEntity);
      var types = [].concat(node["@type"] || []);
      if (!types.some(function(type) { return ["Article", "NewsArticle", "BlogPosting"].indexOf(type) >= 0; })) continue;
      var url = node.url || (typeof node.mainEntityOfPage === "string"
        ? node.mainEntityOfPage : node.mainEntityOfPage && node.mainEntityOfPage["@id"]);
      if (matchesUrl(url)) addText(node.articleBody, node.headline, authorName(node.author));
    }
  }

  // A single explicit articleBody inside the page's headline-bearing article is
  // useful when Readability discards a hidden subtree. Never unhide the live DOM.
  var bodies = doc.querySelectorAll('[itemprop="articleBody"]');
  var headline = doc.querySelector("h1");
  if (bodies.length === 1 && headline) {
    var container = bodies[0].closest("article");
    if (container && container.contains(headline) && bodies[0].innerHTML.length <= MAX_CONTENT) {
      var body = bodies[0].cloneNode(true);
      body.querySelectorAll("script,style,noscript,template,nav,aside,form,button").forEach(function(el) { el.remove(); });
      var text = normalize(body.textContent);
      if (text.length >= 200) candidates.push({
        title: headline.textContent.slice(0, 500), byline: "", content: body.innerHTML, text: text
      });
    }
  }

  // Conflicting bodies are not resolved by blindly picking the longest string.
  var unique = [];
  candidates.forEach(function(candidate) {
    if (!unique.some(function(other) { return other.text === candidate.text; })) unique.push(candidate);
  });
  var selected = unique.length === 1 ? unique[0] : null;
  if (selected && (!regular || selected.text.length >= regularText.length + 160)) {
    return {
      title: selected.title || (regular && regular.title) || doc.title,
      byline: selected.byline || (regular && regular.byline) || "",
      dir: (regular && regular.dir) || doc.documentElement.dir || "",
      content: selected.content
    };
  }
  return regular ? {
    title: regular.title, byline: regular.byline || "", dir: regular.dir || "", content: regular.content
  } : null;
}
