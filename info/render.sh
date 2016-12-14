#!/bin/bash

docker run -it --rm -v $(pwd):/documents/ asciidoctor/docker-asciidoctor \
       asciidoctor -r asciidoctor-diagram -D /documents/rendered/ \
       /documents/*.adoc
