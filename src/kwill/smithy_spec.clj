(ns kwill.smithy-spec
  (:require
    [clojure.set :as sets]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :as walk]))

(comment

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

(defn list-shape-names
  [input]
  (-> (::api-input input) (get "shapes") keys sort))

(defn- length-trait?
  [m]
  (or (m "min") (m "max")))

(defn ->specs1
  [ctx {::keys [input-shape base-ns]}]
  (let [{::keys [api-input]} ctx
        name->shape (api-input "shapes")
        get-shape #(get name->shape %)
        mk-key (fn [path key]
                 (keyword (str/join "." path) key))
        convert
        (fn convert [path shape-name]
          (let [shape (get-shape shape-name)
                shape-spec-k (mk-key path shape-name)]
            (case (get shape "type")
              "structure"
              (let [members (shape "members")
                    req-ks (set (shape "required"))
                    opt-ks (sets/difference (set (keys members)) req-ks)
                    req-un (mapv #(mk-key path %) (sort req-ks))
                    opt-un (mapv #(mk-key path %) (sort opt-ks))
                    keys-args (cond-> []
                                (seq req-un)
                                (conj :req-un req-un)
                                (seq opt-un)
                                (conj :opt-un opt-un))
                    keys-spec `(s/def ~shape-spec-k (clojure.spec.alpha/keys ~@keys-args))
                    child-specs (into []
                                  (mapcat (fn [[k {child-shape-name "shape"}]]
                                            (let [next-path (conj path k)]
                                              (into [`(s/def ~(mk-key path k) ~(mk-key next-path child-shape-name))]
                                                (convert next-path child-shape-name)))))
                                  members)]
                (conj child-specs keys-spec))
              "list"
              (let [next-path (conj path shape-name)
                    member-shape (get-in shape ["member" "shape"])]
                (conj (convert next-path member-shape)
                  `(s/def ~shape-spec-k (s/coll-of ~(mk-key next-path member-shape)))))

              "boolean"
              [`(s/def ~shape-spec-k boolean?)]

              ("double" "float")
              [`(s/def ~shape-spec-k ~(if (length-trait? shape)
                                        (let [args (cond-> []
                                                     (shape "min")
                                                     (conj :min (shape "min"))
                                                     (shape "max")
                                                     (conj :max (shape "max")))]
                                          `(s/double-in ~@args
                                             :infinite? false :NaN? false))
                                        `double?))]

              ("integer" "long")
              [`(s/def ~shape-spec-k ~(if (length-trait? shape)
                                        (let [args [(shape "min" Integer/MIN_VALUE)
                                                    (shape "max" Integer/MAX_VALUE)]]
                                          `(s/int-in ~@args))
                                        `int?))]

              "string"
              [`(s/def ~shape-spec-k ~(if-let [enum (shape "enum")]
                                        (set enum)
                                        `string?))]

              "timestamp"
              [`(s/def ~shape-spec-k inst?)])))]
    (convert [base-ns] input-shape)))

(defn- simplify-dag
  [specs]
  (let [spec->form (into {} (map (fn [[_ k :as s]] [k s])) specs)
        spec->rank (into {} (map (fn [[[_ k] n]] [k n])) (map vector specs (range)))
        ;; any spec refs with 1 parent can be flattened.
        useless-refs (->> specs
                       (keep (fn [[_ k spec-form]]
                               (when (keyword? spec-form)
                                 [spec-form k])))
                       (reduce (fn [acc [k1 k2]]
                                 (update acc k1 (fnil conj []) k2)) {})
                       (filter (fn [[_ vs]] (= (count vs) 1))))]
    (->> useless-refs
      (reduce
        (fn [spec->form [useless-k [real-k]]]
          (-> spec->form
            (dissoc useless-k)
            (assoc real-k `(s/def ~real-k ~(nth (get spec->form useless-k) 2)))))
        spec->form)
      (sort-by (fn [[k]] (get spec->rank k)))
      (vals))))

(comment
  (simplify-dag
    `[(s/def ::foo int?)
      (s/def ::foo2 ::foo)
      (s/def ::foo3 ::foo)

      (s/def ::bar int?)
      (s/def ::bar2 ::bar)]))

(defn ->specs
  "Returns a list of spec forms. Takes an input map generated from the
  `kwill.smithy-spec.download` ns and an argument map taking the following keys:
    - ::input-shape - The name of the input shape to convert to specs
    - ::base-ns - The base spec namespace to use when generating Clojure specs."
  [input argm]
  (let [specs (->specs1 input argm)]
    (simplify-dag specs)))

(comment

  (def shape-name (get-in ec2-input ["operations" "DescribeInstances" "output" "shape"]))
  (def shape-name (get-in ec2-input ["shapes" "Instance"]))
  (list-shape-names ec2-input)


  (require
    'kwill.smithy-spec.download
    'kwill.smithy-spec.write)
  (def ec2-input (kwill.smithy-spec.download/get-input! {:api "ec2" :version "2016-11-15"}))

  (def ss (->specs ec2-input
            {::input-shape "Instance"
             ::base-ns     "aws.ec2"}))

  (kwill.smithy-spec.write/write-to-file! ss
    {:kwill.smithy-spec.write/ns-sym 'ec2
     :kwill.smithy-spec.write/file   "local/ec2.clj"})

  (first ss)
  (eval (cons `do (seq ss)))
  (s/explain :aws.ec2.Tags.TagList/Tag
    {:aws.ec2.Tags.TagList/Key   1
     :aws.ec2.Tags.TagList/Value 2})

  (->specs {::api-input ec2-input}
    {::input-shape "TagList"
     ::base-ns     "aws.ec2"})

  )

