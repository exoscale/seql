## Seql Mandated Conventions

As much as is feasible, **seql** tries to enforce as few conventions as possible
on the way the database is built.

That being said a few things are enforced:

### Row columns naming

Each returned row is returned as map as per the specification in the schema.
Each key in the returned map will be qualified with the table's namespace.

Column names are translated to and from the database with the help
of [`camel-snake-kebab`](https://github.com/clj-commons/camel-snake-kebab):

- Map keys are expected to be in *kebab case*
- SQL column names are expected to in *snake case*

Not all names allow for camel-snake-kebab functions to be bijective,
notably when using numbers in names. When this is the case,
the SQL name of a column can be provided to avoid adding too much
logic to codebases. See `seql.helpers/column-name` for details.

### Left joins only

To build trees of values, an SQL `LEFT JOIN` operation is performed.
If anything more elaborate is needed to build trees, seql will not
be the right way to go.
