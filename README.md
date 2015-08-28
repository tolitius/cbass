# cbass

* Database for storing and finding data 
* HBase is great at that
* Clojure is great at "simple"

## Show me

```clojure
(require '[cbass :refer [new-connection store find-in delete]])
```

### Connecting to HBase

```clojure
(def conf {"hbase.zookeeper.quorum" "127.0.0.1:2181" "zookeeper.session.timeout" 30000})
(def conn (new-connection conf))
```

### Storing data

```clojure 
;; args:      conn, table, row key, family, data

user=> (store conn "galaxy:planet" 42 "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
```

### Finding it

There are two primary ways data is found in HBase:

* when the row key _is_ known: [HBase Get](http://hbase.apache.org/book.html#_get)
* when the row key _is not_ known: [HBase Scan](http://hbase.apache.org/book.html#scan)

#### Row is is unknown

```clojure
;; args:        conn, table, row key

user=> (find-in conn "galaxy:planet" 42)
{:age "4.543 billion years", :inhabited? true, :population 7125000000}
```

#### Row key is unknown

```clojure
user=> (store conn "galaxy:planet" "earth" "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
user=> (store conn "galaxy:planet" "mars" "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
```
```clojure
;; args:        conn, table, family, [:row-key-fn]

user=> (scan-in conn "galaxy:planet" "galaxy")
{"earth" {:age "4.543 billion years", :inhabited? true, :population 7125000000},
 "mars" {:age "4.543 billion years", :inhabited? true, :population 7125000000}}
```

by default cbass will assume row keys are strings, but in practice keys are prefixed and/or hashed.
hence to read a row key from HBase, a custom row key function may come handy:

```clojure
user=> (scan-in conn "galaxy:planet" "galaxy" :row-key-fn #(keyword (String. %)))
{:earth {:age "4.543 billion years", :inhabited? true, :population 7125000000},
 :mars {:age "4.543 billion years", :inhabited? true, :population 7125000000}}
```

of course there are lots of other ways to "scan the cat", but for now here is family scanning.

### Deleting it

Deleting specific columns:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" 42 "galaxy" #{:age :population})

user=> (find-in conn "galaxy:planet" 42)
{:inhabited true}
```

Deleting column family:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" 42 "galaxy")

user=> (find-in conn "galaxy:planet" 42)
{}
```

Deleting whole row:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" 42)

user=> (find-in conn "galaxy:planet" 42)
{}
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
