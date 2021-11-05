## Quickstart

Let us assume the following - admittedly flawed - schema, for which we
will add gradual support:

![schema](https://i.imgur.com/DkBtyew.png)

All the following examples can be reproduced in the
`test/seql/readme_test.clj` integration test. To perform queries, an
*environment* must be supplied, which consists of a schema, and a JDBC
config. In `test/seql/fixtures.clj`, code is provided to experiment with an H2
database.

For all schemas displayed below, we assume an env set up in the following
manner:

```clojure
(def env {:schema ... :jdbc your-database-config})

(require '[seql.query :as q])
(require '[seql.lister :as l])
(require '[seql.mutation :as m])
(require '[clojure.spec.alpha :as s])
(require '[seql.helpers :refer [make-schema ident idents field mutation
                                has-many condition entity-from-spec]])
```

### Specs for the schema

Seql assumes you are familiar with `clojure.spec` if that is not
the case, please refer to: https://clojure.org/guides/spec

We can start by providing specs for the individual fields in each
table:

``` clojure
(create-ns 'my.entities)
(create-ns 'my.entities.account)
(create-ns 'my.entities.user)
(create-ns 'my.entities.invoice)
(create-ns 'my.entities.invoice-line)
(create-ns 'my.entities.product)


(alias 'account 'my.entities.account)
(alias 'user 'my.entities.user)
(alias 'invoice 'my.entities.invoice)
(alias 'invoice-line 'my.entities.invoice-line)
(alias 'product 'my.entities.product)

(s/def ::account/name string?)
(s/def ::account/state #{:active :suspended :terminated})
(s/def ::account/account (s/keys :req [::account/name ::account/state]))

(s/def ::user/name string?)
(s/def ::user/email string?)
(s/def ::user/user (s/keys :req [::user/name ::user/email]))

(s/def ::invoice/state keyword?)
(s/def ::invoice/total nat-int?)
(s/def ::invoice/invoice (s/keys :req [::invoice/state ::invoice/total]))

(s/def ::invoice-line/quantity nat-int?)
(s/def ::invoice-line/invoice-line (s/keys :req [::invoice-line/quantity]))

(s/def ::product/name string?)
(s/def ::product/product (s/keys :req [::product/name]))
```

### Queries on accounts

At first, accounts need to be looked up. We can build a minimal schema:

```clojure
(make-schema
  (entity ::account/account
		  (field :name)
		  (field :state)))
```

Let's unpack things here:

- We give a name our entity, by default it will be assumed that the
  SQL table it resides in is eponymous, when it is not the case, a
  tuple of `[entity-name table-name]` can be provided
- We declare a list of fields known to exist in that table.

With this, simple queries can be performed:

```clojure
(query env ::account/account [::account/name ::account/state])
;; or to fetch all default fields:
(query env ::account/account)

;; =>

[#::account{:name "a0" :state :active}
 #::account{:name "a1" :state :active}
 #::account{:name "a2" :state :suspended}]
```

Idents can also be looked up:

```clojure
(query env [::account/id 0] [::account/name ::account/state])

;; =>

#::account{:name "a0" :state :active}
```

Notice how the last query yielded a single value instead of a collection.
It is expected that idents will yield at most a single value (as a corollary,
idents should only be used for database fields which enforce this guarantee).

Also notice how there was no prior mention of `::account/id`

### Infering schemas from specs

A first concrete improvement we can bring to the schema build step when
an `s/keys` spec is available for our entity is to infer most of the schema
from it:

``` clojure
(make-schema
  (entity-from-spec ::account/account))
```

We can now perform the following query:

``` clojure
(query env ::account/account [::account/name] [[::account/state :active]])

;; =>

[#::account{:name "a0"}
 #::account{:name "a1"}]


(query env ::account/account [::account/name] [[::account/state :suspended]])

;; =>

[#::account{:name "a2"}]
```

### Adding a relation

For queries, **seql**'s strength lies in its ability to understand the
way entities are tied together. **Seql** offers support for
one-to-many (*has many*), one-to-one (*has one*), and many-to-many
(*has many through*) relations.

Let's start with a single relation before building larger nested
trees. Since no assumption is made on schemas, the relations must
specify foreign keys explictly:

```clojure
(make-schema
  (entity-from-spec ::account/account
    (has-many ::users [:id ::user/account-id]))

  (entity-from-spec ::user/user))
```

This will allow doing tree lookups, fetching arbitrary fields from the
nested entity as well:

```clojure
(query env
       ::account/account
       [::account/name
        ::account/state
        {::account/users [::user/name ::user/email]}])

;; =>

[#::account{:name  "a0"
            :state :active
            :users [#::user{:name "u0a0" :email "u0@a0"}
                    #::user{:name "u1a0" :email "u1@a0"}]}
 #::account{:name  "a1"
            :state :active
            :users [#::user{:name "u2a1" :email "u2@a1"}
                    #::user{:name "u3a1" :email "u3@a1"}]}
 #::account{:name "a2" :state :suspended}]
```

### Summary of query description

We've now covered full capabilities of the *query* part of the schema,
were we saw that:

- Each entity should have a *table*.
- To provide more idiomatic output, spec based coercions are
  performed in and out of the database.
- *Conditions* allow for building advanced filters on entities.
- To build arbitrarily nested entities, *relations* need to be used.

With this in mind, here's a complete schema for the above database
schema:

```clojure
(make-schema
 (entity-from-spec ::account/account
            (has-many :users    [:id ::user/account-id])
            (has-many :invoices [:id ::invoice/account-id]))
 (entity-from-spec ::user/user)
 (entity-from-spec ::invoice/invoice
            (has-many :lines    [:id ::invoice-line/invoice-id]))
 (entity-from-spec ::product/product)
 (entity-from-spec [::invoice-line/invoice-line :invoiceline]
            (has-one :product [:product-id ::product/id])))
```

### Controlling the mapping betwen row and column names in the database

Specific table names can be provided by using a vector as the argument
for `entity` or `entity-from-spec`:

``` clojure
(make-schema
  (entity-from-spec [::invoice-line/invoice-line :invoiceline]
    ...))
```

Specific column names can be provided by using the `column-name` helper:

``` clojure
(make-schema
  (entity-from-spec ::network/network
    (column-name :ip6address :ip6)
    ...))
```

### Mutations

With querying sorted, mutations need to be expressed. Here, **seql**
takes the approach of making mutations separate, explictit, and
validated. As with most other **seql** features, mutations are
implemented with a key inside the entity description.

At its core, mutations expect two things:

- A **spec** of their input
- A function of this input which must yield a proper **honeysql** query map, or collection
  of **honeysql** query map to be performed in a transaction.

For the common case of inserting, updating, or deleting records from the database,
a couple of schema helpers are provided.


#### Inserting records with `add-create-mutation`

To allow record insertion, use the `add-create-mutation` helper:

```clojure
 (entity-from-spec ::account/account
            (has-many :users    [:id ::user/account-id])
            (has-many :invoices [:id ::invoice/account-id])
            (add-create-mutation))
```

The implicit mutation created by `add-create-mutation` will be
named: `::account/create`, a spec has to exist for it, as for all
mutations. Since `spec/valid?` runs on input parameters before handing
out to mutation functions it should always be present (otherwise mutations
will throw early).

```clojure
(s/def ::account/create ::account/account)
```

Adding new accounts can now be done through `mutate!`:

```clojure
(mutate! env ::account/create {::account/name  "a3"
                               ::account/state :active})

(query env [::account/name "a3"] [::account/state])

;; =>

#::account{:state :active}
```

#### Updating records with `add-update-by-id-mutation`

To allow record updates, use the `add-update-by-id-mutation` helper:

``` clojure
 (entity-from-spec ::account/account
            (has-many :users    [:id ::user/account-id])
            (has-many :invoices [:id ::invoice/account-id])
            (add-create-mutation)
            (add-update-by-id-mutation ::account/id))
```

This instructs the helper that the input map to the mutation function
will contain a `::account/id` field which should be used to determine
which row to update in the database. The rest of the map contents will
be treated as values to update in the database.

#### Deleting records with `add-delete-by-id-mutation`

To allow record deletes, use the `add-delete-by-id-mutation` helper:

``` clojure
 (entity-from-spec ::account/account
            (has-many :users    [:id ::user/account-id])
            (has-many :invoices [:id ::invoice/account-id])
            (add-create-mutation)
            (add-update-by-id-mutation ::account/id)
            (add-delete-by-id-mutation ::account/id))
```
This instructs the helper that the input map to the mutation function
will contain a `::account/id` field which should be used to determine
which row to delete from the database.

#### Arbitrary mutations with `mutation-fn`

It is hard to predict all types of mutations, and often times, any such attempt
results in worse ergonomics than what SQL provideds. To this end, `seql` allows
providing arbitrary SQL expressions as mutations through the help of `honeysql`

``` clojure
(entity-from-spec ::account/account
 (has-many :users    [:id ::user/account-id])
 (has-many :invoices [:id ::invoice/account-id])
 (mutation-fn :remove-users (s/keys :req [::account/id])
     (fn [params] {:delete-from [:users] :where [:= :account-id (::account/id params)]})))
```

#### Mutation preconditions

Mutations can be provided with preconditions: functions to run before affecting the actual
mutation. These run in the same transaction as the effective mutation.

``` clojure
(entity-from-spec ::account/account
 (add-create-mutation)
 (add-update-by-id-mutation ::account/id)
 (add-precondition :delete ::has-no-users?
   (fn [{::account/keys [id]}]
     ;; Needs to go through HoneySQL
     {:select [:id] :from [:users] :where [:= :account-id id]})
   ;; Ensure the result is empty
   empty?))
```

#### Transactions over several mutations

Mutations can be performed in a larger transaction cycle. To this effect, the
`seql.mutation/with-transaction` macro is provided:

``` clojure
(m/with-transaction env
  (m/mutate! env ::account/create account-a)
  (m/mutate! env ::user/create user1-in-account-a)
  (m/mutate! env ::user/create user2-in-account-a)
  (q/execute env [::account/id (::account/id account-a]]))
```

### Listeners

To provide for clean CQRS type workflows, listeners can be added to
mutations.  Each listener will subsequently be called on sucessful
transactions with a map of:

- `:mutation`: the name of the mutation called
- `:result`: the result of the transaction
- `:params`: input parameters given to the mutation
- `:metadata`: metadata supplied to the mutation, if any

```clojure
(def last-result (atom nil))

(defn store-result
  [details]
  (reset! last-result (select-keys details [:mutation :result])))

(let [env (l/add-listener env ::account/create store-result)]
   (mutate! env ::account/create {::account/name "a4"
                                  ::account/state :active}))

@last-result

;; => {:result [1] :mutation :account/create}
```
