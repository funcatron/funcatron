#!/bin/sh
# render.sh

# if you have installed asciidoctor locally and prefer to use that
# instead of the docker container, then set
# export ASCIIDOCTOR_LOCAL=true

if [ -z "ASCIIDOCTOR_LOCAL" ]; then
    docker run -it --rm -v $(pwd):/documents/ asciidoctor/docker-asciidoctor \
           asciidoctor -r asciidoctor-diagram -D /documents/rendered/ \
           /documents/*.adoc
else
    if [ -z "$@" ]; then
        sources=*.adoc
    else
        sources=$@
    fi

    build="rendered"
    resources="./resources"
    imagesdir="$resources/images"
    stylesdir="$resources/css"

    echo "Making HTML5 in $build/"
    rm -rf "$build"/{*.html,css,images}

    if [ -d "$imagesdir" ]; then
        mkdir -p "$build/images"
        cp -a "$imagesdir" "$build/"
    fi
    if [ -d "$stylesdir" ]; then
        mkdir -p "$build/css"
        cp -a "$stylesdir" "$build/"
    fi

    attrs="--attribute imagesdir=images"
    attrs="$attrs --attribute sectanchors"
    attrs="$attrs --attribute sectlinks"
    attrs="$attrs --attribute linkcss"
    attrs="$attrs --attribute stylesdir=css"
    attrs="$attrs --attribute stylesheet=funcatron-adoc.css"

    asciidoctor --require=asciidoctor-diagram --destination-dir="$build" \
                $attrs $sources

    ls -1 "$build"/*.html
fi
