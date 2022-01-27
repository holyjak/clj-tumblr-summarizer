(ns clj-tumblr-summarizer.storage.db
  "EXPERIMENTAL: Storage of posts into a DB"
  (:require
    [datomic.client.api :as d]))

(def client (d/client {:server-type :dev-local ; requires https://docs.datomic.com/cloud/dev-local.html
                       :system "tumblr-posts"
                       :storage-dir (str (System/getProperty "user.dir") "datomic-data")}))

(d/create-database client {:db-name "posts"})

(def schema ; FIXME Finish the schema; how to handle the varying content of posts?!
  [{:db/ident :post/id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/tags
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident :post/timestamp
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Seconds of epoch"}
   #_{:db/ident :post/content
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/many}
   ; e.g. {:type "link" :display_url .., :title
   ; :type "text" :text ...
   {:db/ident :post/tile
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/timestamp
    :db/valueType :db.type/int
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/raw ; OBS: Limit 4k!
    :db/valueType :db.type/string ; FIXME enum
    :db/cardinality :db.cardinality/one}

   {:db/ident :content/type
    :db/valueType :db.type/string ; FIXME enum
    :db/cardinality :db.cardinality/one}
   {:db/ident
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])
