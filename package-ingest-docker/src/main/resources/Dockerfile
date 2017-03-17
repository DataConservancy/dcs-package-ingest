FROM openjdk:8-jre-alpine

ADD ${jar.file} /package-ingest.jar

COPY entrypoint.sh /entrypoint.sh

RUN chmod 700 /entrypoint.sh

ENTRYPOINT [ "/entrypoint.sh" ]
