# cbass

* Database is mostly for storing and finding data. 
* HBase is great at that.
* Clojure is great at "simple".

## Show me

```clojure
(require '[cbass :refer [new-connection store-in find-in]])
```

### Connecting to HBase

```clojure
(def conf {"hbase.zookeeper.quorum" "127.0.0.1:2181" "zookeeper.session.timeout" 30000})
(def conn (new-connection conf))
```

### Storing data

```clojure 
;; args:         conn, table, row-key, column-family, data

user=> (store-in conn "galaxy:planet" 42 "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
```

### Finding it

```clojure
;; args:        conn, table, row-key

user=> (find-in conn "galaxy:planet" 42)
{:age "4.543 billion years", :inhabited? true, :population 7125000000}
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
