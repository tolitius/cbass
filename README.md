# cbass

* Databases are for storing and finding data 
* HBase is great at that
* Clojure is great at "simple"

--
[![Clojars Project](http://clojars.org/cbass/latest-version.svg)](http://clojars.org/cbass)

## Show me

```clojure
(require '[cbass :refer [new-connection store find-by scan delete]])
```

## Connecting to HBase

```clojure
(def conf {"hbase.zookeeper.quorum" "127.0.0.1:2181" "zookeeper.session.timeout" 30000})
(def conn (new-connection conf))
```

## Storing data

```clojure 
;; args:      conn, table, row key, family, data

user=> (store conn "galaxy:planet" "earth" "galaxy" {:inhabited? true 
                                                     :population 7125000000 
                                                     :age "4.543 billion years"})
```

Depending on a key strategy/structure sometimes it makes sense to only store row-keys / families witout values:

```clojure
user=> (store conn "galaxy:planet" "pluto" "galaxy")
```

## Finding it

There are two primary ways data is found in HBase:

* by the row key: [HBase Get](http://hbase.apache.org/book.html#_get)
* by "anything": [HBase Scan](http://hbase.apache.org/book.html#scan)

### Finding by the row key

```clojure
;; args:        conn, table, row key, [family, columns]

user=> (find-by conn "galaxy:planet" "earth")
{:age "4.543 billion years", :inhabited? true, :population 7125000000}

user=> (find-by conn "galaxy:planet" "earth" "galaxy")
{:age "4.543 billion years", :inhabited? true, :population 7125000000}

user=> (find-by conn "galaxy:planet" "earth" "galaxy" #{:age :population})
{:age "4.543 billion years", :population 7125000000}
```

### Finding by "anything"

HBase calls them scanners, hence the `scan` function name.

Let's first look directly at HBase (shell) to see the data we are going to scan over:

```clojure
hbase(main):002:0> scan 'galaxy:planet'
ROW         COLUMN+CELL
 earth      column=galaxy:age, timestamp=1440880021543, value=NPY\x00i\x134.543 billion years
 earth      column=galaxy:inhabited?, timestamp=1440880021543, value=NPY\x00\x04\x01
 earth      column=galaxy:population, timestamp=1440880021543, value=NPY\x00+\x00\x00\x00\x01\xA8\xAE\xDF@
 mars       column=galaxy:age, timestamp=1440880028315, value=NPY\x00i\x134.503 billion years
 mars       column=galaxy:inhabited?, timestamp=1440880028315, value=NPY\x00\x04\x01
 mars       column=galaxy:population, timestamp=1440880028315, value=NPY\x00d\x03
 neptune    column=galaxy:age, timestamp=1440880036629, value=NPY\x00i\x134.503 billion years
 neptune    column=galaxy:inhabited?, timestamp=1440880036629, value=NPY\x00j\x07unknown
3 row(s) in 0.0230 seconds
```

HBase scanning is pretty flexible: by row key from/to prefixes, by time ranges, by families/columns, etc..

Here are some examples:

#### Scanning the whole table

```clojure
;; args:        conn, table, {:row-key-fn, :family, :columns, :from, :to, :time-range {:from-ms :to-ms}}

user=> (scan conn "galaxy:planet")

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

#### Scanning with a row key function

By default cbass will assume row keys are strings, but in practice keys are prefixed and/or hashed.
Hence to read a row key from HBase, a custom row key function may come handy:

```clojure
;; args:        conn, table, {:row-key-fn, :family, :columns, :from, :to, :time-range {:from-ms :to-ms}}

user=> (scan conn "galaxy:planet" :row-key-fn #(keyword (String. %)))

{:earth
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 :mars {:age "4.503 billion years", :inhabited? true, :population 3},
 :neptune {:age "4.503 billion years", :inhabited? :unknown}}
```

#### Scanning families and columns

by family

```clojure
user=> (scan conn "galaxy:planet" :family "galaxy")

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

specifying columns (qualifiers)

```clojure
user=> (scan conn "galaxy:planet" :family "galaxy" 
                                  :columns #{:age :inhabited?})

{"earth" {:age "4.543 billion years", :inhabited? true},
 "mars" {:age "4.503 billion years", :inhabited? true},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

#### Scanning by row key prefix

Data can be scanned by a row key prefix using `:from` and/or `:to` keys:

```clojure
user=> (scan conn "galaxy:planet" :from "ma")

{"mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

`:to` is exclusive:

```clojure
user=> (scan conn "galaxy:planet" :from "ea" 
                                  :to "ma")

{"earth" {:age "4.543 billion years", :inhabited? true, :population 7125000000}}
```

notice, no Neptune:

```clojure
user=> (scan conn "galaxy:planet" :to "nep")

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3}}
```

#### Scanning by time range

If you look at the data from HBase shell (above), you'll see that every row has a timestamp associated with it.

These timestamps can be used to scan data within a certain time range:

```clojure
user=> (scan conn "galaxy:planet" :time-range {:from-ms 1440880021544 
                                               :to-ms 1440880036630})

{"mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

in case `:from-ms` is missing, it defauts to `0`:

```clojure
user=> (scan conn "galaxy:planet" :time-range {:to-ms 1440880036629})

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3}}
```

same analogy with `:to-ms`, if it is mising, it defaults to `Long/MAX_VALUE`:

```clojure
user=> (scan conn "galaxy:planet" :time-range {:from-ms 1440880036629})

{"neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

#### Scanning by "anything"

Of course _all_ of the above can be combined together, and that's the beauty or scanners:

```clojure
user=> (scan conn "galaxy:planet" :family "galaxy" 
                                  :columns #{:age}
                                  :from "ma" 
                                  :to "z" 
                                  :time-range {:to-ms 1440880036630})

{"mars" {:age "4.503 billion years"},
 "neptune" {:age "4.503 billion years"}}
```

There are lots of other ways to "scan the cat", but for now here are several.

## Deleting it

Deleting specific columns:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "earth" "galaxy" #{:age :population})

user=> (find-by conn "galaxy:planet" "earth")
{:inhabited true}
```

Deleting a column family:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "earth" "galaxy")

user=> (find-by conn "galaxy:planet" "earth")
nil
```

Deleting a whole row:

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "mars")

user=> (find-by conn "galaxy:planet" "mars")
nil
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
