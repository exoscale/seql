## SEQL reference

Queries take the following arguments in **SEQL**, by way of the
[seql.core/query](seql.core.html#var-query) function.

**SEQL** takes a lot of inspiration from [EQL](https://edn-query-language.org),
and thus shares the following property:

> EQL is a declarative way to make hierarchical (and possibly nested)
> selections of information about data requirements.
>
> EQL doesn’t have its own language; it uses EDN to express the
> request, taking advantage of the rich set of primitives provided by
> it.

- An entity
- Fields to be queried
- Additional conditions if any

### Specifying entities

There are two ways to specify entities, by name or using an ident
to retrieve a specific record.

#### Retrieving lists of records

Retrieve all records of the **account** entity, with default fields,
unfiltered.

```clojure
(query :account)
```

This will always return a collection.

#### Retrieving records by ident

```clojure
(query [:account/id 3])
```

In this case, a single record or `nil` will be returned

### Specifying output fields

The best analogy for output field specification is to think of `select-keys`.

```
(query :account [:account/name :account/state])
```

With the above query, the output will be a collect of record, each
containing an `:account/name` and an `:account/state` key.

#### Nested entities

To denote nesting, maps must be used

```
(query :account [:account/name
                 :account/state
				 {:account/users [:user/email])])
```

Maps associate a relation name to a query field vector honoring the same syntax.
This allows nesting deep nesting of entities

```
(query :account [:account/name
                 :account/state
				 {:account/invoice [:invoice/total
				                    {:invoice/lines [:line/price]}]}])
```

### Conditions

When provided, conditions may filter results

