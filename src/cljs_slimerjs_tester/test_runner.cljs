(ns cljs-slimerjs-tester.test-runner
  (:require [cemerick.cljs.test :as test :refer-macros [run-tests] :refer [testing-complete?]]
            [cljs.core.async :refer [<! >! chan timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn start-tests [ns-sym]
  (let [done-chan (chan)
        test-env (cemerick.cljs.test/run-tests ns-sym)]
    (go-loop []
      (if (testing-complete? test-env)
        (>! done-chan test-env)
        (do (<! (timeout 100)) (recur))))
    done-chan))

(defn runner [ns-sym]
  (let [done-chan (chan)]
    (go
     (let [result (<! (start-tests ns-sym))
           result (apply merge
                    ((juxt #(select-keys % [:test :pass :fail :error])
                           (comp deref :async))
                     result))]
       (<! (timeout 100))
       (set! (.-cljs_tests_done js/window) (clj->js result))
       (>! done-chan result)))
    done-chan))
