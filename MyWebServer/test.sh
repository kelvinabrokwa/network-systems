#!/usr/bin/env bash

set -x

curl -i http://localhost:8817/foobar
curl -i http://localhost:8817/foobar --head
curl -i http://localhost:8817/foobaradf
curl -i http://localhost:8817/foobaradf --head
curl -i http://localhost:8817/foobar -H "If-Modified-Since: Sat, 29 Oct 1994 00:00:01 GMT"
curl -i http://localhost:8817/foobar -H "If-Modified-Since: bad date"
curl -i http://localhost:8817/foobar -H "If-Modified-Since"
curl -i -X PUT http://localhost:8817/foobaradf
