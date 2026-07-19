(function(){
  // Nebula Reader - Readability-like body extractor
  // 注入页面后通过 window.NebulaReadability.parse() 使用
  function parse(){
    const doc = document.cloneNode(true);
    // 移除脚本/样式/广告等
    const unwanted = "script,style,noscript,iframe,nav,header,footer,aside,form,button,svg,video,audio,canvas";
    doc.querySelectorAll(unwanted).forEach(el => el.remove());
    // 选择正文候选
    let best = null, bestScore = 0;
    doc.querySelectorAll("article,div,section,main").forEach(el => {
      const text = el.innerText || "";
      const length = text.length;
      const commas = (text.match(/[，,。.]/g) || []).length;
      const score = length + commas * 10;
      if (length > 200 && score > bestScore) { best = el; bestScore = score; }
    });
    if (!best) best = doc.body || doc.documentElement;
    const title = (doc.querySelector("h1")?.innerText) || document.title || "";
    const byline = (doc.querySelector("[rel=author]")?.innerText) ||
                   (doc.querySelector("meta[name=author]")?.content) || "";
    return {
      title: title.trim(),
      byline: byline.trim(),
      content: best.innerHTML,
      text: best.innerText,
      excerpt: (best.innerText || "").slice(0, 200),
      length: best.innerText.length
    };
  }
  window.NebulaReadability = { parse: parse };
})();
