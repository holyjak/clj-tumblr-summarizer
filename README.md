# clj-tumblr-summarizer

A command-line tool for backing up posts from a Tumblr blog and creating monthly summaries of the published posts.

## Status

Alpha, work in progress.

* [x] Retrieve new posts, store them to the disk (minimal error handling)
* [ ] Put the posts into a Datalog datastore
* [ ] Create monthly summaries of the newly published posts
* [ ] Make it into an AWS Lambda, run monthly
* [ ] Error handling, robustness

## Usage

### Execution

Assuming that you have the file `./.api-key` with the 
Tumblr API key, you can run:

    clojure -M -m clj-tumblr-summarizer.main <blog name, e.g. holyjak>

## License

Copyright © 2020 Jakub Holý

Distributed under the Eclipse Public License either version 1.0. 
