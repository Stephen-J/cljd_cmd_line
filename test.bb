(require '[babashka.process :refer [shell]]
         '[clojure.java.io :as io]
         '[babashka.fs :as fs])
(import 'java.io.PushbackReader)

(defn shell-config [{:keys [path]}]
  {:out :string
   :err :string
   :continue true
   :dir (fs/file path)})



(def cljd-init {:command :cljd/init
                :exec (fn [app]
                       (shell (shell-config app) "bb clojure -M:cljd init"))})

(def cljd-compile {:command :cljd/compile
                   :exec (fn [app]
                          (shell (shell-config app) "bb clojure -M:cljd compile"))})

(def cljd-test {:command :cljd/test
                :exec (fn [app]
                       (shell (shell-config app) "bb clojure -M:cljd test"))})

(def cljd-clean {:command :cljd/clean
                 :exec (fn [app]
                        (shell (shell-config app) "bb clojure -M:cljd clean"))})

(def dart-run {:command :dart/run
               :exec (fn [app]
                      (shell (shell-config app) "dart run"))})

(defmulti test-app (fn [app] (get-in app [:deps :cljd/opts :kind])))

(defmethod test-app :dart [app]
  [cljd-init cljd-compile cljd-test dart-run])

(defmethod test-app :flutter [app]
  [cljd-init])


(defn clean [testing-dir]
  (when (fs/exists? testing-dir)
    (fs/delete-tree testing-dir)))

(defn setup [testing-dir apps]
  (when-not (fs/exists? testing-dir)
    (fs/create-dir testing-dir))
  (doseq [app apps]
    (fs/create-dir (fs/path testing-dir (fs/file-name app)))
    (fs/copy-tree app (fs/path testing-dir (fs/file-name app)))))


(defn path->app [path]
  {:deps (edn/read (PushbackReader. (io/reader (fs/file path "deps.edn"))))
   :path path})

(defn construct-tests [testing-dir]
  (map (fn [app-path]
         (let [app (path->app app-path)]
           {:app app :tests (test-app app)}))
       (fs/list-dir testing-dir)))


(def app-testing-dir (fs/file "app_testing"))
(def apps-to-test (fs/list-dir (fs/file "apps")))

(clean app-testing-dir)
(setup app-testing-dir apps-to-test)
(def app-tests (construct-tests app-testing-dir))

(doseq [{:keys [app tests]} app-tests]
  (println "Testing app -> " (fs/file-name (:path app)))
  (doseq [{:keys [command exec]} tests]
    (print " " command " ")
    (-> (exec app) :exit (case 0 "success" "failure") println)))
