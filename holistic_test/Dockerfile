FROM openjdk:8

MAINTAINER David Pollak <funcmaster-d@funcatron.org>

RUN mkdir /app

RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/local/bin && \
    export LEIN_ROOT=ok

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y maven python-pip vim && \
    apt remove -y openjdk-7-jre-headless openjdk-7-jre


# RUN update-java-alternatives --set java-1.8.0-openjdk-amd64

RUN mkdir /m2 && \
    chmod og+rwx /m2

RUN pip install requests

ADD tests_in_docker.py /usr/bin/run_tests.py

RUN chmod +x /usr/bin/run_tests.py

