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

The is ample documentation in [README](../README.html) around creating
schemas. The one provided in `sandbox` is a minimal one:

```clojure
  (s/def :account/name string?)
  (s/def :account/state #{:active :suspended :terminated})
  (s/def :account/id nat-int?)
  (s/def :account/account (s/keys :req [:account/name :account/state] :opt [:account/id]))

  (def env
    {:jdbc   jdbc-config
     :schema (make-schema (entity-from-spec :account/account))})
```

### Running queries

At this point, you are ready to go:

```
  (q/execute env :account)
```
