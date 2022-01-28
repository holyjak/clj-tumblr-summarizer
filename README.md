# clj-tumblr-summarizer

A tool / library that progressively backs up posts from a Tumblr.com micro-blog and can produce monthly summaries of the published posts in HTML. Can be run as a scheduled GitHub Action.

Created to scratch my itch and to learn more about technologies such as `core.async` and (perhaps, eventually) Datomic.

## Status

Beta - under testing

* [x] Retrieve new posts, store them to the disk (minimal error handling)
* [ ] [WIP] Put the posts into a Datalog datastore
* [x] Create monthly summaries of the newly published posts
* [x] ~Make it into an AWS Lambda, run monthly~ Make [it into a scheduled GitHub action](.github/workflows/archive-new-posts.yml)
* [x] Error handling, robustness - see [Error handling in Clojure Core.Async (and its higher-level constructs)](https://blog.jakubholy.net/2019/core-async-error-handling/)

## Usage

### Prerequisites

**Authorization**: You need to have the file `./.api-key` in this current directory with the 
[Tumblr API key](https://www.tumblr.com/settings/apps) (the *OAuth Consumer Key*).

**Configuration**: Create `config.edn` with the name of your Tumblr blog under `:blog`. Example: `{:blog "holyjak"}`.

### As a library

Currently only available as a git dependency for deps-tools:

```clojure
;; deps.edn
{:deps {io.github.holyjak/clj_tumblr_summarizer {:git/tag "v1.0.0" :git/sha "TODO"}}}}
```

```clojure
(require '[clj-tumblr-summarizer.main :as tumblr])
;; Download new posts
(tumblr/store-new-posts "<tumblr blog name>")
;; Create a summary of the previous month's posts:
(tumblr/summarize)
```

### Via Clojure CLI

Provided that you have the [Clojure CLI tools](https://clojure.org/guides/getting_started) and 
a local copy of this repo, inside its directory do:

```shell
# Download new posts
clojure -M -m clj-tumblr-summarizer.main
# Create a summary of the previous month's posts:
clojure -X:summarize 
```

### Via Babashka tasks

If you have both Clojure CLI and [Babashka](https://babashka.org) and a local copy of this repo:

```shell
# Download new posts
bb run
# Create a summary of the previous month's posts:
bb summarize 
```

### As a scheduled GitHub Action

See `.github/workflows/archive-new-posts.yml` for an work-in-progress example.
## License

Copyright © 2020 Jakub Holý

Distributed under the Eclipse Public License either version 1.0. 
