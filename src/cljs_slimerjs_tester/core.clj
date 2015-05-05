1(ns cljs-slimerjs-tester.core
  (:require [org.httpkit.server :as http]
            [rksm.subprocess :as subp]
            [clojure.test :as test]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [chan >! <! go alt!! timeout]]))

(defn- slimerjs-test-js
  [base-url timeout]
  (format "
var testTimeout = %s*1000; // ms
var fs = require('fs');
var page = require('webpage').create();

page.onConsoleMessage = function(msg, lineNum, sourceId) { console.log(msg); };

var url = '%s/run-tests.html';
page.open(url, function (status) {
  setInterval(function() {
    var result = page.evaluateJavaScript('window.cljs_tests_done');
    if (!result || result.test === 0) return;
    console.log('slimer thinks tests are done');
    var success = result.fail === 0 && result.error === 0;
    slimer.exit(success ? 0 : 1);
  }, 100);
});

setTimeout(function() {
  console.log('TIMEOUT RUNNING TESTS');
  slimer.exit(2);
}, testTimeout);
" timeout base-url))

(defn- test-html
  [bootstrap-js-scripts full-qualified-runner-func]
  (let [scripts (->> bootstrap-js-scripts
                  (map (partial format "<script src=\"%s\" type=\"text/javascript\"></script>"))
                  (interpose "\n")
                  (apply str))
        runner-ns (-> full-qualified-runner-func namespace munge)
        runner-call (-> full-qualified-runner-func
                      munge
                      (clojure.string/replace #"_SLASH_" "."))]
    (format "<html>
     <title>cljs tests run by slimerjs</title>
     %s
     <script>goog.require('%s');</script>
     <body><script>%s();</script></body>
     </html>" scripts runner-ns runner-call)))

(defn- serve-test-files
  [resource-dir bootstrap-js-scripts runner-func-name]
  (fn
    [{:keys [uri] :as req}]
    (let [[_ rel-path] (re-find #"^/(.*)" uri)
          [status type content]
          (cond
            (= rel-path "run-tests.html") [200 "text/html" (test-html bootstrap-js-scripts runner-func-name)]
            (->> rel-path (io/file resource-dir) .exists) [200
                                                      "application/javascript"
                                                      (->> rel-path (io/file resource-dir) slurp)]
            :default [404 "text/plain" (str "no such resource: " rel-path)])]
      {:status  status
       :headers {"Content-Type" type}
       :body content})))

(defmacro ^:private with-test-server
  [app port & body]
  `(let [stop-fn# (org.httpkit.server/run-server ~app {:port ~port})]
     (try
       ~@body
       (finally (stop-fn# :timeout 100)))))

(defn- run-slimer-tester
  [base-url timeout-ms]
  (let [file (java.io.File/createTempFile "slimerjs-test" ".js")
        _ (spit file (slimerjs-test-js base-url timeout-ms))
        cmd+args ["slimerjs" (str file)]
        cmd+args (case (System/getProperty "os.name")
                   "Mac OS X" (conj cmd+args :env {"SLIMERJSLAUNCHER" "/Applications/Firefox.app/Contents/MacOS/firefox"})
                   cmd+args)
        proc (apply subp/async-proc cmd+args)
        test-chan (let [c (chan)] (go (subp/wait-for proc) (>! c proc)) c)
        result (alt!!
                test-chan ([result] result)
                (timeout timeout-ms) :timeout)
        _ (when (= :timeout result)
            (subp/signal proc)
            (throw (Exception. (str "Timeout!"))))
        code (subp/exit-code proc)
        out (subp/stdout proc)
        [_ test pass fail error]
        (re-find #":test ([0-9]+), :pass ([0-9]+), :fail ([0-9]+), :error ([0-9]+)" out)
        [test pass fail error] (->> [test pass fail error]
                                 (map #(or % "nil"))
                                 (map read-string))]
    {:test test :pass pass :fail fail :error error}))

(defn run-tests
  [resource-dir bootstrap-js-scripts runner-func-name & [opts]]
  (let [{:keys [port timeout]} (merge {:port 8095 :timeout 3000} opts)
        test-url (str "http://localhost:" port)
        handler (serve-test-files
                 resource-dir
                 bootstrap-js-scripts
                 runner-func-name)]
    (with-test-server handler port
      (run-slimer-tester test-url timeout))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn abs-path [rel-path]
  (->> (clojure.java.io/file "." rel-path) .getCanonicalPath))

(defn server-for-manual-testing
  [& [port]]
  (let [app (serve-test-files (abs-path "cloxp-cljs-build/")
                              ["/cloxp-cljs.js"]
                              'rksm.cloxp-com.test-runner/runner)]
    (def stop (org.httpkit.server/run-server app  {:port (or port 8095)}))))

(comment

 (server-for-manual-testing)
 (stop)

 (run-tests (abs-path "cloxp-cljs-build/")
            ["/cloxp-cljs.js"]
            'rksm.cloxp-com.test-runner/runner
            {:port 8095 :timeout 10000})

 )