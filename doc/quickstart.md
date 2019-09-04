## Quickstart

Let us assume the following - admitedly flawed - schema, for which we
will add gradual support:

![schema](https://i.imgur.com/DkBtyew.png)

All the following examples can be reproduced in the
`test/seql/readme_test.clj` integration test. To perform queries, an
*environment* must be supplied, which consists of a schema, and a JDBC
config.

For all schemas displayed below, we assume an env set up in the following
manner:

```clojure
(def env {:schema ... :jdbc your-database-config})
(require '[seql.core :refer [query mutate! add-listener!]])
(require '[seql.helpers :refer [schema ident field compound mutation
                                transform has-many condition entity]])
```

in `test/seql/fixtures.clj`, code is provided to experiment with an H2
database.

### Queries on accounts

At first, accounts need to be looked up. We can build a minimal schema:

```clojure
(schema
  (entity :account
          (field :id (ident))
		  (field :name)
		  (field :state)))
```

Let's unpack things here:

- We give a name our entity, by default it will be assumed that the
  SQL table it resides in is eponymous, when it is not the case, a
  tuple of `[entity-name table-name]` can be provided
- *Ident* fields are unique in the database and can be used to
  retrieve a single record.

With this, simple queries can be performed:

```clojure
(query env :account [:account/name :account/state])

;; =>

[#:account{:name "a0" :state "active"}
 #:account{:name "a1" :state "active"}
 #:account{:name "a2" :state "suspended"}]
```

Idents can also be looked up:

```clojure
(query env [:account/id 0] [:account/name :account/state])

;; =>

#:account{:name "a0" :state "active"}
```

Notice how the last query yielded a single value instead of a collection.
It is expected that idents will yield at most a single value (as a corollary,
idents should only be used for database fields which enforce this guarantee).

While this works, our schema can be improved in two ways:

- `name` is a good candidate for being an ident as well
- The `state` field would be better returned as a keyword if possible
- It could be interesting to be able to add conditions

```clojure
(schema
  (entity :account
    (field :id (ident))
    (field :name (ident))
    (field :state (transform :keyword))
    (condition :active :state :active)
    (condition :state)))
```

We can now perform the following query:

```clojure
(query env [:account/name "a0"] [:account/name :account/state])

;; =>

#:account{:name "a0" :state :active}

(query env :account [:account/name] [[:a/active]])

;; =>

[#:account{:name "a0"}
 #:account{:name "a1"}]
 
 
(query env :account [:account/name] [[:a/state :suspended]])

;; =>

[#:account{:name "a2"}]
```

### Adding a relation

For queries, **seql**'s strength lies in its ability to understand the
way entities are tied together. Let's start with a single relation
before building larger nested trees. Since no assumption is made on
schemas, the relations must specify foreign keys explictly:

```clojure
(schema
   (entity :account
     (field :id (ident))
     (field :name (ident))
     (field :state (transform :keyword))
     (has-many :users [:id :user/account-id])
     (condition :active :state :active)
     (condition :state))

   (entity :user
     (field :id (ident))
     (field :name (ident))
     (field :email))
```

This will allow doing tree lookups, fetching arbitrary fields from the
nested entity as well:

```clojure
(query env
       :account
       [:account/name
        :account/state
        {:account/users [:user/name :user/email]}])
		
;; =>

[#:account{:name  "a0"
           :state :active
           :users [#:user{:name "u0a0" :email "u0@a0"}
                   #:user{:name "u1a0" :email "u1@a0"}]}
 #:account{:name  "a1"
           :state :active
           :users [#:user{:name "u2a1" :email "u2@a1"}
                   #:user{:name "u3a1" :email "u3@a1"}]}
 #:account{:name "a2" :state :suspended :users []}]
```

### Compounds fields

SQL being less flexible than Clojure to represent value, compound
fields can help build more appropriate representation of data.
Compounds specify their source as a list of fields and a function
which provided with these fields in order should yield a proper output
value.

Looking at our schema, the `state` field of the `invoice` table can
easily be converted into a boolean:

```clojure
(schema
  (entity :invoice
          (field :id (ident))
          (field :state (transform :keyword))
          (field :total)
          (compound :paid? [state] (= state :paid))
          (condition :paid :state :paid)
          (condition :unpaid :state :unpaid)))
```

We can now assert that compounds are correctly realized:

```
(query env :invoice [:invoice/total :invoice/paid?])

;; =>

[#:invoice{:total 2, :paid? false}
 #:invoice{:total 2, :paid? true}
 #:invoice{:total 4, :paid? true}]
```

### Summary of query description

We've now covered full capabilities of the *query* part of the schema,
were we saw that:

- Each entity should at least have a *table*, list of *idents*, and
  *fields*.
- To provide more idiomatic output, *transforms* allow field mangling.
- Beyond *idents*, *conditions* allow for building filters on
  entities.
- To build arbitrarily nested entities, *relations* need to be used.
- For ad-hoc field buiding, *compounds* can receive database fields
  and yield new values.
  
With this in mind, here's a complete schema for the above database
schema:


```clojure
(schema
 (entity :account
         (field :id          (ident))
         (field :name        (ident))
         (field :state       (transform :keyword))
         (has-many :users    [:id :user/account-id])
         (has-many :invoices [:id :invoice/account-id])

         (condition :active  :state :active)
         (condition :state))

 (entity :user
         (field :id          (ident))
         (field :name        (ident))
         (field :email))

 (entity :invoice
         (field :id          (ident))
         (field :state       (transform :keyword))
         (field :total)
         (compound :paid?    [state] (= state :paid))
         (has-many :lines    [:id :line/invoice-id])

         (condition :unpaid  :state :unpaid)
         (condition :paid    :state :paid))

 (entity [:line :invoiceline]
         (field :id          (ident))
         (field :product)
         (field :quantity)))
```

### Mutations

With querying sorted, mutations need to be expressed. Here, **seql** takes the
approach of making mutations separate, explict, and validated. As with most other
**seql** features, mutations are implemented with a key inside the entity description.

At its core, mutations expect two things:

- A **spec** of their input
- A function of this input which must yield a proper **honeysql** query map, or collection
  of **honeysql** query map to be performed in a transaction.

```
(s/def :account/name string?)
(s/def :account/state keyword?)
(s/def ::account (s/keys :req [:account/name :account/state]))


;; We can now modify the :account entity:

(entity :account
         (field :id          (ident))
         (field :name        (ident))
         (field :state       (transform :keyword))
         (has-many :users    [:id :user/account-id])
         (has-many :invoices [:id :invoice/account-id])

         (condition :active  :state :active)
         (condition :state)
		 
         (mutation :account/create ::account [params]
                   (-> (h/insert-into :account)
                       (h/values [params])))

         (mutation :account/update ::account [{:keys [id] :as params}]
                   (-> (h/update :account)
                       ;; values are fed unqualified
                       (h/sset (dissoc params :id))
                       (h/where [:= :id id]))))
```

Adding new accounts can now be done through `mutate!`:

```clojure
(mutate! env :account/create {:account/name  "a3"
                              :account/state :active})
							  
(query env [:account/.name "a3"] [:account/state])

;; =>

#:account{:state :active}
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

(let [env (add-listener! env store-result)]
   (mutate! env :account/create {:account/name "a4"
                                 :account/state :active}))
								 
@last-result

;; => {:result [1] :mutation :account/create}
```
