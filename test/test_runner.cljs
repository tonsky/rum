(ns test-runner
  (:require [cljs.test]
            [react]
            [daiquiri.interpreter-test]
            [daiquiri.normalize-test]
            [daiquiri.util-test]))

(defmethod cljs.test/report [::cljs.test/default :error] [m]
  (cljs.test/inc-report-counter! :error)
  (println "\nERROR in" (cljs.test/testing-vars-str m))
  (when (seq (:testing-contexts (cljs.test/get-current-env)))
    (println (cljs.test/testing-contexts-str)))
  (when-let [message (:message m)] (println message))
  (let [formatter-fn (or (:formatter (cljs.test/get-current-env)) pr-str)]
    (println "expected:" (formatter-fn (:expected m)))
    (if (instance? js/Error (:actual m))
      (println "  actual:" (.-stack (:actual m)))
      (println "  actual:" (formatter-fn (:actual m))))))

(cljs.test/run-tests
  (cljs.test/empty-env)
  'daiquiri.interpreter-test
  'daiquiri.normalize-test
  'daiquiri.util-test)
