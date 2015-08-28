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

```clojure
;; args:        conn, table, row key

user=> (find-in conn "galaxy:planet" 42)
{:age "4.543 billion years", :inhabited? true, :population 7125000000}
```

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
