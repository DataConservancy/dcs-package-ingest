#!/bin/sh
echo '' > /etc/hosts
exec java -jar /package-ingest.jar
