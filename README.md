# clj-tumblr-summarizer

A command-line tool / AWS Lambda for backing up posts from a Tumblr blog and creating monthly summaries of the published posts, to publish on a blog.

Created to scratch my itch and to learn more about technilogies such as `core.async` and Datomic.

## Status

Alpha, work in progress.

* [x] Retrieve new posts, store them to the disk (minimal error handling)
* [ ] [WIP] Put the posts into a Datalog datastore
* [ ] Create monthly summaries of the newly published posts
* [x] ~Make it into an AWS Lambda, run monthly~ Make [it into a scheduled GitHub action](.github/workflows/archive-new-posts.yml)
* [ ] Error handling, robustness - see [Error handling in Clojure Core.Async (and its higher-level constructs)](https://blog.jakubholy.net/2019/core-async-error-handling/)

## Usage

### Execution

Assuming that you have the file `./.api-key` in this directory with the 
[Tumblr API key](https://www.tumblr.com/settings/apps) (the *OAuth Consumer Key*), you can run:

    clojure -M -m clj-tumblr-summarizer.main <blog name, e.g. holyjak>

## License

Copyright © 2020 Jakub Holý

Distributed under the Eclipse Public License either version 1.0. 
