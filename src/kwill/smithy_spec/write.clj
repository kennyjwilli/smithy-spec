(ns kwill.smithy-spec.write
  (:require
    [clojure.pprint :as pprint]
    [clojure.walk :as walk]
    [clojure.spec.alpha :as s]))

(defn pretty-str
  [x]
  (with-out-str (pprint/pprint x)))

(defn- prep-specs
  [specs]
  (walk/postwalk
    (fn [x]
      (if (qualified-symbol? x)
        (cond
          (= "clojure.core" (namespace x))
          (symbol (name x))
          (= "clojure.spec.alpha" (namespace x))
          (symbol "s" (name x))
          :else x)
        x))
    specs))

(defn write-to-file!
  [specs {::keys [ns-sym file]}]
  (let [prepped-specs (prep-specs specs)
        appends (into [(list 'ns ns-sym
                         '(:require
                            [clojure.spec.alpha :as s]))]
                  prepped-specs)
        appends' (map pretty-str appends)]
    (run! (fn [line]
            (spit file (str line "\n") :append true))
      appends')))

(comment
  (require '[kwill.smithy-spec.write :as smithy-spec.write])
  (smithy-spec.write/write-to-file!
    '[(clojure.spec.alpha/def :kwill.smithy-spec.write/foo clojure.core/int?)]
    {::ns-sym 'test
     ::file   "test.clj"}))
