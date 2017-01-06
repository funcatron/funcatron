#!/bin/bash

echo "Building test running container and running tests"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${DIR}/../scripts/start_rabbit.sh

${DIR}/../scripts/start_frontend.sh

docker build -t funcatron/holistic:latest .

docker run -ti --rm --net=host -v $(cd ${DIR}/../.. && pwd):/data funcatron/holistic:latest /usr/bin/run_tests.py

echo $?
