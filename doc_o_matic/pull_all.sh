#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ROOT="${DIR}/../.."

for x in "funcatron" "intf" "starter" "frontend" "devshim" "tron" "samples" "jvm_services"; do
    echo "Doing ${x}"
    cd "${ROOT}/${x}" || exit 1
    git pull || exit 1
done

echo "Yay! Done pulling all the repos"
