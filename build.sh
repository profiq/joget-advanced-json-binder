#!/bin/sh
docker run -i --rm -v ${PWD}:/root/.m2 -v ${PWD}:/usr/src/mymaven -w /usr/src/mymaven jogetworkflow/docker-maven-joget:8.0-SNAPSHOT mvn -B clean install
