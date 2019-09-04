seql: Simplified EDN Query Language
===================================

**seql** intends to provide a simplified
[EQL](https://edn-query-language.org/) inspired query language to
access entities stored in traditional SQL databases.

## Introduction

Accessing SQL entities is often done based on a pre-existing
schema. In most designs, applications strive to limit the number of
ways mutations should happen on SQL. However, queries often need to be
very flexible in the type of data they return as well in the number of
joins performed.

With this rationale in mind, **seql** was built to provide:

- A data-based schema syntax to describe entities stored in SQL, as
  well as there relations to each other, making no assumptions on the
  database layout
- A subset of the schema dedicated to expressing mutations and their
  input to allow for validation at the edge
- A query builder allowing ad-hoc relations to be expressed
- A mutation handler

On top of this, the schema syntax support creating compound fields as
well as normalizing data to provide for more idiomatic Clojure data in
query results

```clojure
(query env
       :account
       [:account/name
	    :account/state
		{:account/users [:user/email]}])
		
;; =>

[#:account{:name "org1"
           :state :active
		   :users [#:user{:email "first@example.com"}
		           #:user{:email "second@example.com"}]}
 #:account{:name "org2"
           :state :suspended
		   :users [#:user{:email "bad@example.com"}
		           #:user{:email "worst@example.com"}]}]
				   
(mutate! env
         :account/new-organization
		 #:account{:name "org3"
		           :users [#:user{:email "hello@example.com"}]})
```

**seql** is built on top of
[honeysql](https://github.com/jkk/honeysql) and makes that dependency
apparent in some case, particularly for mutations.

## Documentation

**seql** provides two introductory documents to get started with
designing schemas and queries incrementally, as well as full API
documentation. All are available on [the documentation website](https://exoscale.github.io/seql).
