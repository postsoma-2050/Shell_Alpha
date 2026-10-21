// NODE_PATH must point to a Playwright installation; uses a fresh browser profile.
const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const assets = path.resolve(__dirname, '../../main/assets/reader');
const script = fs.readFileSync(path.join(assets, 'Readability.js'), 'utf8') + '\n' +
  fs.readFileSync(path.join(assets, 'ExtractArticle.js'), 'utf8');
const url = 'https://reader.example.test/current-story';
const introduction = 'The first paragraph explains the subject of the current article in detail. Readers should see the text already sent by the website, without changing their session or navigating elsewhere. ';
const full = introduction + '\n\n' + 'Additional delivered paragraph discusses the evidence, describes the findings, and presents the conclusion for readers. '.repeat(12) + 'FINAL_DELIVERED_PARAGRAPH';
const visible = '<article><h1>Current story</h1><p>' + introduction.repeat(2) + '</p></article>';
const json = data => '<script type="application/ld+json">' + JSON.stringify(data) + '</script>';
const article = extra => ({'@type':'NewsArticle',url,headline:'Current story',articleBody:full,...extra});
(async()=>{
  const browser=await chromium.launch({headless:true, ...(process.env.CHROME_BIN ? {executablePath:process.env.CHROME_BIN} : {})});
  let passed=0;
  try {
    const page=await browser.newPage();
    await page.route('**/*',route=>route.fulfill({status:204,body:''}));
    const extract=async html=>{
      await page.setContent('<title>Current story</title>'+html);
      return page.evaluate(({script,url})=>{
        const before=document.documentElement.outerHTML;
        const result=(0,eval)('(function(){'+script+';return shellAlphaExtractArticle(document,'+JSON.stringify(url)+');})()');
        return {result,unchanged:before===document.documentElement.outerHTML};
      },{script,url});
    };
    async function check(label,html,test) {
      const value=await extract(html);
      assert.ok(value.unchanged,'original DOM changed: '+label);
      test(value.result);passed++;console.log('PASS '+label);
    }
    const hasTail = value=>assert.ok(value?.content.includes('FINAL_DELIVERED_PARAGRAPH'));
    const noTail = value=>assert.ok(!value?.content.includes('FINAL_DELIVERED_PARAGRAPH'));
    await check('JSON-LD body extends visible preview',visible+json(article()),hasTail);
    await check('JSON-LD works without a usable visible article',json(article()),hasTail);
    await check('graph/mainEntity article body',visible+json({'@graph':[{'@type':'WebPage',mainEntity:article()}]}),hasTail);
    await check('hash identifies same article',visible+json(article({url:url+'#body'})),hasTail);
    await check('another article is ignored',visible+json(article({url:url+'/related'})),noTail);
    await check('unrelated origin is ignored',visible+json(article({url:'https://other.example.test/current-story'})),noTail);
    await check('missing article identity is ignored',visible+json(article({url:null})),noTail);
    await check('description is never treated as body',visible+json(article({articleBody:null,description:full})),noTail);
    await check('malformed JSON preserves normal reader',visible+'<script type="application/ld+json">{broken}</script>',v=>assert.ok(v?.content.includes('first paragraph')));
    await check('explicit NEXT_DATA article path',visible+'<script id="__NEXT_DATA__" type="application/json">'+JSON.stringify({props:{pageProps:{article:{url,title:'Current story',body:full}}}})+'</script>',hasTail);
    await check('related NEXT_DATA article is ignored',visible+'<script id="__NEXT_DATA__" type="application/json">'+JSON.stringify({props:{pageProps:{article:{url:url+'/related',body:full}}}})+'</script>',noTail);
    await check('hidden semantic article body',visible.replace('</article>','<div hidden itemprop="articleBody"><p>'+full+'</p></div></article>'),hasTail);
    await check('multiple semantic bodies are not guessed',visible+'<article><div itemprop="articleBody" hidden>'+full+'</div><div itemprop="articleBody" hidden>'+full+'</div></article>',noTail);
    await check('conflicting structured bodies preserve normal reader',visible+json([article(),article({articleBody:full+' Conflicting edition.'})]),noTail);
    await check('a richer regular article retains links and formatting','<article><h1>Current story</h1><p>'+full+'</p><p>'+full+'</p><a href="https://example.test/reference">Reference link</a></article>'+json(article()),v=>assert.ok(v?.content.includes('href="https://example.test/reference"')));
    await check('HTML-valued structured body is not evaluated',json(article({articleBody:full+' <img src=x onerror=alert(1)>'})),v=>assert.equal(v,null));
    await check('ampersands remain literal reader text',json(article({articleBody:full+' A & B < 5.'})),v=>assert.ok(v?.content.includes('A &amp; B &lt; 5.')));
    await check('oversized structured data is skipped',visible+json(article({articleBody:'x'.repeat(510000)})),v=>assert.ok(v?.content.includes('first paragraph')));
    await check('large hydration scripts do not reject a readable article',visible+'<script type="application/json">'+JSON.stringify({hydration:'x'.repeat(1600000)})+'</script>',v=>assert.ok(v?.content.includes('first paragraph')));
    console.log(JSON.stringify({passed,originalDomUnchanged:true,realWebsitesTested:false}));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
