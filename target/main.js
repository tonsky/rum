if(typeof goog == "undefined") document.write('<script src="out/goog/base.js"></script>');
document.write('<script src="out/cljs_deps.js"></script>');
document.write('<script>if (typeof goog != "undefined") { goog.require("boot.cljs.main"); } else { console.warn("ClojureScript could not load :main, did you forget to specify :asset-path?"); };</script>');
