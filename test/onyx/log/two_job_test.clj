(ns onyx.log.two-job-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [onyx.extensions :as extensions]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.api :as api]
            [onyx.test-helper :refer [playback-log get-counts load-config]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.test :refer [deftest is testing]]))

(def my-inc identity)

(def a-chan (chan 100))

(def c-chan (chan (sliding-buffer 100)))

(def d-chan (chan 100))

(def f-chan (chan (sliding-buffer 100)))

(defn inject-a-ch [event lifecycle]
  {:core.async/chan a-chan})

(defn inject-c-ch [event lifecycle]
  {:core.async/chan c-chan})

(defn inject-d-ch [event lifecycle]
  {:core.async/chan d-chan})

(defn inject-f-ch [event lifecycle]
  {:core.async/chan f-chan})

(def a-calls
  {:lifecycle/before-task-start inject-a-ch})

(def c-calls
  {:lifecycle/before-task-start inject-c-ch})

(def d-calls
  {:lifecycle/before-task-start inject-d-ch})

(def f-calls
  {:lifecycle/before-task-start inject-f-ch})

(deftest log-two-job
  (let [onyx-id (java.util.UUID/randomUUID)
        config (load-config)
        env-config (assoc (:env-config config) :onyx/id onyx-id)
        peer-config (assoc (:peer-config config)
                           :onyx/id onyx-id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/balanced)
        env (onyx.api/start-env env-config)
        peer-group (onyx.api/start-peer-group peer-config)
        n-peers 12
        v-peers (onyx.api/start-peers n-peers peer-group)
        catalog-1 [{:onyx/name :a
                    :onyx/plugin :onyx.plugin.core-async/input
                    :onyx/type :input
                    :onyx/medium :core.async
                    :onyx/batch-size 20
                    :onyx/doc "Reads segments from a core.async channel"}

                   {:onyx/name :b
                    :onyx/fn :onyx.log.two-job-test/my-inc
                    :onyx/type :function
                    :onyx/batch-size 20}

                   {:onyx/name :c
                    :onyx/plugin :onyx.plugin.core-async/output
                    :onyx/type :output
                    :onyx/medium :core.async
                    :onyx/batch-size 20
                    :onyx/doc "Writes segments to a core.async channel"}]

        catalog-2 [{:onyx/name :d
                    :onyx/plugin :onyx.plugin.core-async/input
                    :onyx/type :input
                    :onyx/medium :core.async
                    :onyx/batch-size 20
                    :onyx/doc "Reads segments from a core.async channel"}

                   {:onyx/name :e
                    :onyx/fn :onyx.log.two-job-test/my-inc
                    :onyx/type :function
                    :onyx/batch-size 20}

                   {:onyx/name :f
                    :onyx/plugin :onyx.plugin.core-async/output
                    :onyx/type :output
                    :onyx/medium :core.async
                    :onyx/batch-size 20
                    :onyx/doc "Writes segments to a core.async channel"}]

        lifecycles-1 [{:lifecycle/task :a
                       :lifecycle/calls :onyx.log.two-job-test/a-calls}
                      {:lifecycle/task :a
                       :lifecycle/calls :onyx.plugin.core-async/reader-calls}
                      {:lifecycle/task :c
                       :lifecycle/calls :onyx.log.two-job-test/c-calls}
                      {:lifecycle/task :c
                       :lifecycle/calls :onyx.plugin.core-async/writer-calls}]

        lifecycles-2 [{:lifecycle/task :d
                       :lifecycle/calls :onyx.log.two-job-test/d-calls}
                      {:lifecycle/task :d
                       :lifecycle/calls :onyx.plugin.core-async/reader-calls}
                      {:lifecycle/task :f
                       :lifecycle/calls :onyx.log.two-job-test/f-calls}
                      {:lifecycle/task :f
                       :lifecycle/calls :onyx.plugin.core-async/writer-calls}]

        j1 (onyx.api/submit-job peer-config
                                {:workflow [[:a :b] [:b :c]]
                                 :catalog catalog-1
                                 :lifecycles lifecycles-1
                                 :task-scheduler :onyx.task-scheduler/balanced})

        j2 (onyx.api/submit-job peer-config
                                {:workflow [[:d :e] [:e :f]]
                                 :catalog catalog-2
                                 :lifecycles lifecycles-2
                                 :task-scheduler :onyx.task-scheduler/balanced})
        ch (chan n-peers)
        replica (playback-log (:log env) (extensions/subscribe-to-log (:log env) ch) ch 2000)]

    (testing "peers balanced on 2 jobs" 
      (is (= (get-counts replica [j1 j2]) [[2 2 2] [2 2 2]])))

    (doseq [v-peer v-peers]
      (onyx.api/shutdown-peer v-peer))

    (onyx.api/shutdown-peer-group peer-group)

    (onyx.api/shutdown-env env)))
