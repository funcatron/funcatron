#!/usr/bin/env bash

echo "Building Doc-o-Matic container"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t funcatron/doc-o-matic:latest . || exit 1

docker run -ti --rm --net=host -v $(cd ${DIR}/../.. && pwd):/data funcatron/doc-o-matic:latest

echo $?
