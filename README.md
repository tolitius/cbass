# cbass

* Databases are for storing and finding data 
* HBase is great at that
* Clojure is great at "simple"

---

[![Clojars Project](http://clojars.org/cbass/latest-version.svg)](http://clojars.org/cbass)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Show me](#show-me)
- [Connecting to HBase](#connecting-to-hbase)
  - [Custom Serializers](#custom-serializers)
- [Storing data](#storing-data)
  - [Storing a single row](#storing-a-single-row)
  - [Storing multiple rows](#storing-multiple-rows)
- [Finding it](#finding-it)
  - [Finding by the row key](#finding-by-the-row-key)
  - [Finding by "anything"](#finding-by-anything)
    - [Scanning the whole table](#scanning-the-whole-table)
    - [Scanning with a row key function](#scanning-with-a-row-key-function)
    - [Scanning families and columns](#scanning-families-and-columns)
    - [Scanning by row key prefix](#scanning-by-row-key-prefix)
      - [:starts-with](#starts-with)
    - [Scanning by time range](#scanning-by-time-range)
    - [Scanning in reverse](#scanning-in-reverse)
    - [Scanning with the limit](#scanning-with-the-limit)
    - [Scanning with filter](#scanning-with-filter)
    - [Scanning with the last updated](#scanning-with-the-last-updated)
    - [Getting Lazy](#getting-lazy)
    - [Scanning by "anything"](#scanning-by-anything)
- [Deleting it](#deleting-it)
    - [Deleting specific columns](#deleting-specific-columns)
    - [Deleting a column family](#deleting-a-column-family)
    - [Deleting a whole row](#deleting-a-whole-row)
  - [Deleting by anything](#deleting-by-anything)
  - [Delete row key function](#delete-row-key-function)
- [Serialization](#serialization)
  - [When Connecting](#when-connecting)
- [Using increment mutations](#using-increment-mutations)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Show me

```clojure
(require '[cbass :refer [new-connection store find-by scan delete]])
```

## Connecting to HBase

```clojure
(def conf {"hbase.zookeeper.quorum" "127.0.0.1:2181" "zookeeper.session.timeout" 30000})
(def conn (new-connection conf))
```

### Custom Serializers

By default `cbass` uses [nippy](https://github.com/ptaoussanis/nippy) for serialization / deserialization. There are more details about it in the [Serialization](#serialization) section. This can be changed by providing your own, optional, `pack` / `unpack` functions when creating an HBase connection:

```clojure
(def conn (new-connection conf :pack identity 
                               :unpack identity))
```

In this example we are just _muting_ "packing" and "unpacking" relying on the custom serialization being done _prior_ to calling `cbass`, so the data is a byte array, and deserialization is done _after_ the value is returned from cbass, since it will just return a byte array back in this case (i.e. `identity` function for both).

## Storing data

### Storing a single row

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

### Storing multiple rows

In case there are multiple rows to store in the same table, `store-batch` can help out:

```clojure
(store-batch conn "galaxy:planet" 
             [["mars" "galaxy" {:inhabited? true :population 3 :age "4.503 billion years"}]
              ["earth" "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"}]
              ["pluto" "galaxy"]
              ["neptune" "galaxy" {:inhabited? :unknown :age "4.503 billion years"}]]))
```

notice the "pluto", it has no columns, which is also fine.

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

##### :starts-with

Starting from hbase-client `0.99.1`, cbass can just do `:starts-with`, in case no `:to` is needed.

Notice, we added `saturday` and `saturn` for a better example:

```clojure
user=> (scan conn "galaxy:planet")

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "pluto" {},
 "saturday" {:age "24 hours", :inhabited? :sometimes},
 "saturn" {:age "4.503 billion years", :inhabited? :unknown}}
```

using `:starts-with`:

```clojure
user=> (scan conn "galaxy:planet" :starts-with "sa")

{"saturday" {:age "24 hours", :inhabited? :sometimes},
 "saturn" {:age "4.503 billion years", :inhabited? :unknown}}
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

#### Scanning in reverse

Here is a regular table scan with all the defaults:

```clojure
user=> (scan conn "galaxy:planet")

{"earth" {:age "4.543 billion years", :inhabited? true, :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown}}
```

many times it makes sense to scan table in reverse order 
to have access to the latest updates first without scanning the whole search space:

```clojure
user=> (scan conn "galaxy:planet" :reverse? true)

{"neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "earth" {:age "4.543 billion years", :inhabited? true, :population 7125000000}}
```

#### Scanning with the limit

Since scanning partially gets its name from a "table scan", in many cases it may return quite large result sets. 
Often we'd like to limit the number of rows returned, but HBase does not make it simple for [various reasons](http://www.dotkam.com/2015/10/08/hbase-scan-let-me-cache-it-for-you/).

cbass makes it quite simple to limit the number of rows returned by using a `:limit` key:

```clojure
user=> (scan conn "galaxy:planet" :limit 2)

{"earth" {:age "4.543 billion years", :inhabited? true, :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3}}
```

For example to get the latest 3 planets added, we can scan in reverse (latest) with a limit of 3:

```clojure
user=> (scan conn "galaxy:planet" :limit 3 :reverse? true)
```

#### Scanning with filter

For a maximum flexibility an HBase [Filter](https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/filter/Filter.html) can be passed directly to `scan` via a `:filter` param.

Here is an example of [ColumnPrefixFilter](https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/filter/ColumnPrefixFilter.html), all other HBase filters will work the same.

The data we work with:

```clojure
user=> (scan conn "galaxy:planet")

{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "pluto" {},
 "saturday" {:age "24 hours", :inhabited? :sometimes},
 "saturn" {:age "4.503 billion years", :inhabited? :unknown}}
```

Creating a filter that would only look the rows where columns start with "ag", and scanning with it:

```clojure
user=> (def f (ColumnPrefixFilter. (.getBytes "ag")))
#'user/f
user=> (scan conn "galaxy:planet" :filter f)

{"earth" {:age "4.543 billion years"},
 "mars" {:age "4.503 billion years"},
 "neptune" {:age "4.503 billion years"},
 "saturday" {:age "24 hours"},
 "saturn" {:age "4.503 billion years"}}

```

Similarly creating a filter that would only look the rows where columns start with "pop", and scanning with it:

```clojure

user=> (def f (ColumnPrefixFilter. (.getBytes "pop")))
#'user/f
user=> (scan conn "galaxy:planet" :filter f)

{"earth" {:population 7125000000}, 
 "mars" {:population 3}}
```

#### Scanning with the last updated

In order to get more intel on _when_ the results were updated last, you can add `:with-ts? true` to scan.
It will look at _all_ the cells in the result row, and will return the latest timestamp.

```clojure
user=> (scan conn "galaxy:planet")
{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars" {:age "4.503 billion years", :inhabited? true, :population 3},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "pluto" {:one 1, :three 3, :two 2},
 "saturday" {:age "24 hours", :inhabited? :sometimes},
 "saturn" {:age "4.503 billion years", :inhabited? :unknown}}
```

and this is what the result of `:with-ts? true` will look like:

```clojure
user=> (scan conn "galaxy:planet" :with-ts? true)
{"earth"
 {:last-updated 1449681589719,
  :age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars"
 {:last-updated 1449681589719,
  :age "4.503 billion years",
  :inhabited? true,
  :population 3},
 "neptune"
 {:last-updated 1449681589719,
  :age "4.503 billion years",
  :inhabited? :unknown},
 "pluto" {:last-updated 1449681589719, :one 1, :three 3, :two 2},
 "saturday"
 {:last-updated 1449681589719,
  :age "24 hours",
  :inhabited? :sometimes},
 "saturn"
 {:last-updated 1449681589719,
  :age "4.503 billion years",
  :inhabited? :unknown}}
```

not exactly interesting, since all the rows were stored in batch at the same exact millisecond. Let's spice it up.

Have you heard the latest news about life at Saturn? Let's record it:

```clojure
user=> (store conn "galaxy:planet" "saturn" "galaxy" {:inhabited? true})
```

and scan again:

```clojure
user=> (scan conn "galaxy:planet" :with-ts? true)
{"earth"
 {:last-updated 1449681589719,
  :age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "mars"
 {:last-updated 1449681589719,
  :age "4.503 billion years",
  :inhabited? true,
  :population 3},
 "neptune"
 {:last-updated 1449681589719,
  :age "4.503 billion years",
  :inhabited? :unknown},
 "pluto" {:last-updated 1449681589719, :one 1, :three 3, :two 2},
 "saturday"
 {:last-updated 1449681589719,
  :age "24 hours",
  :inhabited? :sometimes},
 "saturn"
 {:last-updated 1449682282217,
  :age "4.503 billion years",
  :inhabited? true}}
```

notice the Saturn's last update timestamp: it is now `1449682282217`.

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

#### Getting Lazy

By default `scan` will return a realized (not lazy) result as a map. In case too much data is expected to
come back or the problem is best solved in batches, `scan` can be asked to return a lazy sequence of result 
maps instead via a `:lazy? true` option:

```clojure
user=> (scan conn "galaxy:planet" :lazy? true)
(["earth"
  {:age "4.543 billion years",
   :inhabited? true,
   :population 7125000000}]
 ["mars" {:age "4.503 billion years", :inhabited? true, :population 3}]
 ["neptune" {:age "4.503 billion years", :inhabited? :unknown}]
 ["pluto" {}]
 ["saturday" {:age "24 hours", :inhabited? :sometimes}]
 ["saturn" {:age "4.503 billion years", :inhabited? true}])
```

it is really a LazySeq:

```clojure
user=> (type (scan conn "galaxy:planet" :lazy? true))
clojure.lang.LazySeq
```

whereas by default it is a map:

```clojure
user=> (type (scan conn "galaxy:planet"))
clojure.lang.PersistentArrayMap
```

## Deleting it

#### Deleting specific columns

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "earth" "galaxy" #{:age :population})

user=> (find-by conn "galaxy:planet" "earth")
{:inhabited true}
```

#### Deleting a column family

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "earth" "galaxy")

user=> (find-by conn "galaxy:planet" "earth")
nil
```

#### Deleting a whole row

```clojure
;; args:       conn, table, row key, [family, columns]

user=> (delete conn "galaxy:planet" "mars")

user=> (find-by conn "galaxy:planet" "mars")
nil
```

### Deleting by anything

There is often a case where rows need to be deleted by a filter, that is similar to the one used in [scan](https://github.com/tolitius/cbass#scanning-by-anything) (i.e. by row key prefix, time range, etc.)
HBase does not really help there besides providing a [BulkDeleteEndpoint](http://archive.cloudera.com/cdh5/cdh/5/hbase/apidocs/org/apache/hadoop/hbase/coprocessor/example/BulkDeleteEndpoint.html) coprocessor.

This is not ideal as it delegates work to HBase "stored procedures" (effectively that is what coprocessors are).
It really pays off during massive data manipulation since it does happen _directly_ on the server,
but in simpler cases, which are many, coprocessors are less than ideal.

**cbass** achives "deleting by anything" by a trivial flow: "scan + multi delete" packed in a "delete-by" function
which preserves the "scan"'s syntax:

```clojure
user=> (scan conn "galaxy:planet")
{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "pluto" {},
 "saturday" {:age "24 hours", :inhabited? :sometimes},
 "saturn" {:age "4.503 billion years", :inhabited? :unknown}}

user=> (delete-by conn "galaxy:planet" :from "sat" :to "saz")
;; deleting [saturday saturn], since they both match the 'from/to' criteria
```

look ma, no saturn, no saturday:

```clojure
user=> (scan conn "galaxy:planet")
{"earth"
 {:age "4.543 billion years",
  :inhabited? true,
  :population 7125000000},
 "neptune" {:age "4.503 billion years", :inhabited? :unknown},
 "pluto" {}}
```

and of course any other criteria that is available in "scan" is available in "delete-by".

### Delete row key function

Most of the time HBase keys are prefixed (salted with a prefix).
This is done to avoid ["RegionServer hotspotting"](http://hbase.apache.org/book.html#rowkey.design).

"delete-by" internally does a "scan" and returns keys that matched. Hence in order to delete these keys
they have to be "re-salt-ed" according to the custom key design.

**cbass** addresses this by taking an optional `delete-key-fn`, which allows to "put some salt back" on those keys.

Here is a real world example:

```clojure
;; HBase data

user=> (scan conn "table:name")
{"���|8276345793754387439|transfer" {...},
 "���|8276345793754387439|match" {...},
 "���|8276345793754387439|trade" {...},
 "�d\k^|28768787578329|transfer" {...},
 "�d\k^|28768787578329|match" {...},
 "�d\k^|28768787578329|trade" {...}}
```

a couple observations about the key:

* it is prefixed with salt
* it is piped delimited 

In order to delete, say, all keys that start with `8276345793754387439`,
besides providing `:from` and `:to`, we would need to provide a `:row-key-fn` 
that would _de_ salt and split, and then a `delete-key-fn` that can reassemble it back:

```clojure
(delete-by conn progress :row-key-fn (comp split-key without-salt)
                         :delete-key-fn (fn [[x p]] (with-salt x p))
                         :from (-> "8276345793754387439" salt-pipe)
                         :to   (-> "8276345793754387439" salt-pipe+))))
```

`*salt`, `*split` and `*pipe` functions are not from **cbass**, 
they are here to illustrate the point of how "delete-by" can be used to take on the real world.

```clojure
;; HBase data after the "delete-by"

user=> (scan conn "table:name")
{"�d\k^|28768787578329|transfer" {...},
 "�d\k^|28768787578329|match" {...},
 "�d\k^|28768787578329|trade" {...}}
```

## Serialization

HBase requires all data to be stored as bytes, i.e. byte arrays. Hence some serialization / deserialzation _defaults_ are good to have.

### Defaults

cbass uses a great [nippy](https://github.com/ptaoussanis/nippy) serialization library by default, but of course not everyone uses nippy, plus there are cases where the work needs to be on a pre existing dataset.

### Plug it in

Serialization in cbass is pluggable via `pack-up-pack` function that takes two functions, the one to pack and the one to unpack:

```clojure
(pack-un-pack {:p identity :u identity})
```

In the case above we are just muting packing unpacking relying on the custom serialization being done _prior_ to calling cbass, so the data is a byte array, and deserialization is done on the return value from cbass, since it will just return a byte array back in this case (i.e. `identity` for both).

But of course any other pack/unpack fuctions can be provided to let cbass know how to serialize and deserialize.

cbass keeps an internal state of pack/unpack functions, so `pack-un-pack` would usually be called just once when an application starts.

### When Connecting

While calling `pack-un-pack` works great, in the future, it would be better to specify serializers locally per connection. A `new-connection` function takes `pack` and `unpack` as optional arguments, and this would be a _prefered way_ to plug in serializers vs. `pack-un-pack`:

```clojure
(def conn (new-connection conf :pack identity 
                               :unpack identity))
```

## Using increment mutations

HBase offers counters in the form of the mutation API. One caveat is that the data isn't serialized with nippy so we have to 
manage deserialization ourselves:

```clojure
=> (cbass/pack-un-pack {:p #(cbass.tools/to-bytes %) :u identity})
=> (require '[cbass.mutate :as cmut])
```

```clojure
=> (cmut/increment conn "galaxy:planet" "mars" "galaxy" :landers 7)

#object[org.apache.hadoop.hbase.client.Result
        0x7017e957
        "keyvalues={mars/galaxy:landers/1543441160950/Put/vlen=8/seqid=0}"
```
```clojure
=> (find-by conn "galaxy:planet" "mars" "galaxy")

{:last-updated 1543441160950,
 :age #object["[B" 0x2207b2e6 "[B@2207b2e6"],
 :inhabited? #object["[B" 0x618e78f7 "[B@618e78f7"],
 :landers #object["[B" 0xd63e8e6 "[B@d63e8e6"],
 :population #object["[B" 0x644599bb "[B@644599bb"]}
 
=> (cbass.tools/bytes->num (:landers (find-by conn "galaxy:planet" "mars" "galaxy")))
7
```

There's support for batch processing of increments as well as for using the async BufferedMutator for high throughput. See the [source](src/cbass/mutate.clj) for more info.

## License

Copyright © 2018 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
