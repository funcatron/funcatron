#!/usr/bin/env bash


echo "Building Doc-o-Matic container"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t funcatron/doc-o-matic:latest . || exit 1

DOCKER_PATH=$(cd ${DIR}/../.. && pwd)
if [[ $(which realpath) ]]; then
    DOCKER_PATH=$(realpath ${DOCKER_PATH} | sed -r 's$^/mnt(/[a-z]/)$\1$'| sed -r 's$^/cygdrive(/[a-z]/)$\1$')
fi


docker run -ti --rm --net=host -v ${DOCKER_PATH}:/data funcatron/doc-o-matic:latest /usr/bin/doc_it.py || exit 1

if [[  "$TRAVIS_BRANCH" -eq "master" ]]; then
    THE_DATE=$(date +"%Y%m%d%H%M%S")
    NEW_PLACE="funcatron_${THE_DATE}"
    cd $DIR
    echo "Copying docs"
    cd ..
    chmod +x push_to_telegram.sh
    cp ../doc.tgz mysite.tar.gz
    ./push_to_telegram.sh
    echo "Deposited docs"
fi


echo $?
