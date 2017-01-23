FROM openjdk:8

MAINTAINER David Pollak <funcmaster-d@funcatron.org>

RUN mkdir /app

RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/local/bin && \
    export LEIN_ROOT=ok

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y maven python-pip vim emacs && \
    apt remove -y openjdk-7-jre-headless openjdk-7-jre

RUN pip install requests

ADD sbt /usr/bin/sbt

ADD sbt-launch.jar /usr/bin/sbt-launch.jar

RUN chmod +x /usr/bin/sbt

ADD tests_in_docker.py /usr/bin/run_tests.py

RUN chmod +x /usr/bin/run_tests.py

