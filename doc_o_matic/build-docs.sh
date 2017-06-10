#!/usr/bin/env bash


echo "Building Doc-o-Matic container"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t funcatron/doc-o-matic:latest . || exit 1

DOCKER_PATH=$(cd ${DIR}/../.. && pwd)
DOCKER_PATH=$(realpath ${DOCKER_PATH} | sed -r 's$^/mnt(/[a-z]/)$\1$'| sed -r 's$^/cygdrive(/[a-z]/)$\1$')


docker run -ti --rm --net=host -v ${DOCKER_PATH}:/data funcatron/doc-o-matic:latest /usr/bin/doc_it.py || exit 1

if [[  "$TRAVIS_BRANCH" -eq "master" ]]; then
    if [[ $TRAVIS_SECURE_ENV_VARS ]]; then
        echo "Copying ssh deploy key"
        mkdir ~/.ssh
        cp $DIR/../id_ed25519 ~/.ssh
        chmod -R og-rwx ~/.ssh
    fi

    THE_DATE=$(date +"%Y%m%d%H%M%S")
    NEW_PLACE="funcatron_${THE_DATE}"
    cd $DIR
    echo "Copying docs"
    cat ../../doc.tgz | \
        ssh -oStrictHostKeyChecking=no ubuntu@funcatron.org \
            "tar -xzf - ;  mkdir ${NEW_PLACE} ; mv docout ${NEW_PLACE}/ ; rm funcatron ; ln -s ${NEW_PLACE} funcatron" || exit 1
    echo "Deposited docs"
fi


echo $?
