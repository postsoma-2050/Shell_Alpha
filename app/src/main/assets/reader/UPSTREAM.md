# Mozilla Readability

Source: https://github.com/mozilla/readability
Revision: ab4027a8b37669745016869a37a504727992b2ba
License: Apache-2.0 (LICENSE.md and NOTICE included).
Readability.js is unmodified. Executed only on user request against a cloned page DOM.

ExtractArticle.js is ShellAlpha's wrapper. It also checks already-delivered,
URL-matched JSON-LD articleBody, the explicit NEXT_DATA pageProps.article.body
path (plain strings only), and a single semantic articleBody in the main
headline-bearing article. Conflicting candidates fall back to Readability;
longer content is a candidate, not proof of a complete article. No requests,
Cookie changes, observers, or source-document mutations are introduced.
