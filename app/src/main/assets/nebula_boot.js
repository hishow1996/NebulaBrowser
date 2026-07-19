(function(){
  "use strict";
  const RUNTIME_VERSION = "1.0";
  function log(){ try { console.log("[NebulaUS]", ...arguments); } catch(e){} }
  function inject(scriptText, scriptId){
    try {
      const s = document.createElement("script");
      s.textContent = scriptText;
      s.dataset.nebulaUserScript = scriptId || "";
      (document.head || document.documentElement).appendChild(s);
      s.remove();
      log("injected", scriptId);
    } catch(e) { log("inject failed", e); }
  }
  function applyStyles(css){
    if(!css) return;
    const style = document.createElement("style");
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }
  // window.NebulaBridge 由 Java addJavascriptInterface 注入
  try {
    window.NebulaBoot = {
      RUNTIME_VERSION: RUNTIME_VERSION,
      inject: inject,
      applyStyles: applyStyles,
      ready: true
    };
    log("runtime ready v" + RUNTIME_VERSION);
  } catch(e) { log("runtime init failed", e); }
})();
