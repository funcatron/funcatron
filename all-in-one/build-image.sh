#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd "${DIR}/.."

docker build -f all-in-one/Dockerfile -t funcatron/allinone:latest .
