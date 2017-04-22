FROM openjdk:8

MAINTAINER David Pollak <funcmaster-d@funcatron.org>

RUN mkdir /app && \
    wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/local/bin && \
    export LEIN_ROOT=ok

ADD project.clj /app/
ADD resources /app/resources/
ADD src /app/src/
ADD test /app/test/

RUN cd /app && \
    lein do clean, compile, test, uberjar && \
    cp target/uberjar/tron-*-standalone.jar /tron.jar && \
    cd / && \
    rm -rf app && \
    rm -rf ~/.m2

EXPOSE 3000 4000 54657

ENTRYPOINT ["/usr/bin/java", "-jar", "/tron.jar"]
