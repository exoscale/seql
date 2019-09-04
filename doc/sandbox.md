## Sandbox set-up

In `dataset/sandbox.clj`, you will find a namespace to quickly get
up and running. The set-up relies on three steps:

### Loading a dataset

`db.fixtures` is a namespace providing support for fixtures. This namespace
is only present under the **dev** leiningen profile:

```clojure
(load-fixtures :small)
```

### Creating a schema

The is ample documentation in [quickstart](quickstart.html) around creating
schemas. The one provided in `sandbox` is a minimal one:

```clojure
  (def env
    {:jdbc   jdbc-config
     :schema (make-schema
              (entity :account
                      (field :id (ident))
                      (field :name (ident))
                      (field :state (transform :keyword))))})
```

### Running queries

At this point, you are ready to go:

```
  (query env :account [:account/name :account/state])
```
