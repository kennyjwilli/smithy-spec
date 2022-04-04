# smithy-spec

Convert Smithy JSON files into Clojure specs.

## Usage

```clojure
(require '[kwill.smithy-spec.download :as download])
=> nil

(def ec2-input (download/get-input! {:api "ec2" :version "2016-11-15"}))
=> #'user/ec2-input

(require '[kwill.smithy-spec :as smithy-spec])
=> nil

(smithy-spec/->specs ec2-input
  {::smithy-spec/input-shape "TagList"
   ::smithy-spec/base-ns     "aws.ec2"})
=>
((clojure.spec.alpha/def :aws.ec2/TagList (clojure.spec.alpha/coll-of :aws.ec2.TagList/Tag))
 (clojure.spec.alpha/def :aws.ec2.TagList/Key clojure.core/string?)
 (clojure.spec.alpha/def
   :aws.ec2.TagList/Tag
   (clojure.spec.alpha/keys :opt-un [:aws.ec2.TagList/Key :aws.ec2.TagList/Value]))
 (clojure.spec.alpha/def :aws.ec2.TagList/Value clojure.core/string?))
```

### Write to file

```clojure
(require '[kwill.smithy-spec.write :as smithy-spec.write])
=> nil

(smithy-spec.write/write-to-file!
  '[(clojure.spec.alpha/def :kwill.smithy-spec.write/foo clojure.core/int?)]
  {::smithy-spec.write/ns-sym 'test
   ::smithy-spec.write/file   "test.clj"})
=> nil
```

And the content of `test.clj`...

```clojure
(ns test (:require [clojure.spec.alpha :as s]))

(s/def :kwill.smithy-spec.write/foo int?)

(ns test (:require [clojure.spec.alpha :as s]))

(s/def :kwill.smithy-spec.write/foo int?)

```
