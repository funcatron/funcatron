#!/bin/bash

echo "Building test running container and running tests"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${DIR}/../scripts/start_rabbit.sh

${DIR}/../frontend/build-container.sh

${DIR}/../scripts/start_working_frontend.sh

docker build -t funcatron/holistic:latest .

if [[ "--shell" == "$1" ]]; then
    docker run -ti --rm \
           -e TRAVIS_BRANCH="$TRAVIS_BRANCH" \
           -e TRAVIS_SECURE_ENV_VARS="$TRAVIS_SECURE_ENV_VARS" \
           -e SONATYPE_USERNAME="$SONATYPE_USERNAME" \
           -e SONATYPE_PASSWORD="$SONATYPE_PASSWORD" \
           --net=host -v $(cd ${DIR}/../.. && pwd):/data funcatron/holistic:latest || (docker rm -f working-func-resty ; exit 1)
else
    docker run -ti --rm  \
           -e TRAVIS_BRANCH="$TRAVIS_BRANCH" \
           -e TRAVIS_SECURE_ENV_VARS="$TRAVIS_SECURE_ENV_VARS" \
           -e SONATYPE_USERNAME="$SONATYPE_USERNAME" \
           -e SONATYPE_PASSWORD="$SONATYPE_PASSWORD" \
           --net=host -v $(cd ${DIR}/../.. && pwd):/data funcatron/holistic:latest /usr/bin/run_tests.py $@ || (docker rm -f working-func-resty ; exit 1)
fi

docker rm -f working-func-resty
