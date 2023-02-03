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
{:deps {io.github.holyjak/clj-tumblr-summarizer {:git/tag "v1.0.4" :git/sha "bec65d3"}}}}
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
# Create a summary of the *given* month's posts:
clojure -X:summarize :data '"2022-01"'
```

### Via Babashka tasks

If you have both Clojure CLI and [Babashka](https://babashka.org) and a local copy of this repo:

```shell
# Download new posts
bb run
# Create a summary of the previous month's posts:
bb summarize
# Create a summary of the *given* month's posts:
bb summarize-m 2022-12
```

### As a scheduled GitHub Action

See `.github/workflows/archive-new-posts.yml`.

## Testing the generated summary against the original posts

To compare the look of the summarized posts with the original posts:

1. Run `bb run` to fetch the posts
2. Run `./fetch-posts-html.bb` to fetch the HTML of all the posts
3. Run `(clj-tumblr-summarizer.output/compare-summary-and-original-visuals)` in the REPL and open the produced comparison.html in a browser

## License

Copyright © 2020-2022 Jakub Holý

Distributed under the MIT License
