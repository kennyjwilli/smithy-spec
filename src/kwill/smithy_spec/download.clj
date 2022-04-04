(ns kwill.smithy-spec.download
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]))

(defn input-file
  [{:keys [api version]}]
  (io/file "input" "aws" api (format "%s-%s.normal.json" api version)))

(defn download-url
  [{:keys [api version]}]
  (format "https://raw.githubusercontent.com/aws/aws-sdk-js/master/apis/%s-%s.normal.json"
    api version))

(defn download!
  [{:keys [api version] :as argm}]
  (let [out-file (input-file {:api api :version version})]
    (.mkdirs (.getParentFile out-file))
    (io/copy
      (slurp (download-url argm))
      out-file)))

(defn read-input
  [{:keys [api version]}]
  {:kwill.smithy-spec/api-input (json/read-str (slurp (input-file {:api api :version version})))})

(defn get-input!
  [argm]
  {:kwill.smithy-spec/api-input (json/read-str (slurp (download-url argm)))})

(comment (download! {:api "ec2" :version "2016-11-15"})

  (def ec2-input (get-input! {:api "ec2" :version "2016-11-15"}))
  (tap> ec2-input)
  (tap> (ec2-input "shapes"))
  (sort (keys (get-in ec2-input ["operations"])))
  (tap> (get-in ec2-input ["operations" "DescribeInstances"]))
  (tap> (get-in ec2-input ["operations" "DescribeInstances" "output"]))
  (tap> (get-in ec2-input ["shapes" "DescribeInstancesResult"]))
  (tap> (get-in ec2-input ["shapes" "String"]))

  (get-in ec2-input ["shapes" "Instance"])
  (get-in ec2-input ["shapes" "String"])
  (keys (ec2-input "shapes"))

  (get-in ec2-input ["operations" "DescribeInstances" "output"])

  (first ec2-input)

  (distinct
    (for [[op {:strs [output]}] (get ec2-input "operations")
          :when output]
      (keys output)))
  ;; nil output
  (get-in ec2-input ["operations" "DeleteRoute" "output"])

  ;; Get shape types
  (let [*store (atom #{})]
    (walk/postwalk (fn [x]
                     (when-let [t (get x "type")]
                       (swap! *store conj t))
                     x) (get ec2-input "shapes"))
    @*store)

  )
