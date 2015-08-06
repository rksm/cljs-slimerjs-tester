(defproject org.rksm/cljs-slimerjs-tester "0.1.1-SNAPSHOT"
  :description "cljs test runner, using slimerjs"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :url "https://github.com/rksm/cljs-slimerjs-tester"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [rksm/subprocess "0.1.4"]
                 [com.cemerick/clojurescript.test "0.3.3"]]
  :scm {:url "git@github.com:rksm/cljs-slimerjs-tester.git"}
  :pom-addition [:developers [:developer
                              [:name "Robert Krahn"]
                              [:url "http://robert.kra.hn"]
                              [:email "robert.krahn@gmail.com"]
                              [:timezone "-9"]]])
