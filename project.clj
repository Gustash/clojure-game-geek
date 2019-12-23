(defproject clojure-game-geek "0.1.0-SNAPSHOT"
  :description "A tiny BoardGameGeek clone written in Clojure with Lacinia"
  :url "https://github.com/walmartlabs/clojure-game-geek"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.walmartlabs/lacinia-pedestal "0.5.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [io.aviso/logging "0.2.0"]]
  :repl-options {:init-ns clojure-game-geek.core}
  :profiles {:dev {:resource-paths ["dev-resources"]}})

