# Docker image for Jenkins build

This docker image is used by the Jenkins job on https://ci.eclipse.org/mosaic/job/mosaic/ to execute maven 
builds and to provide a SUMO executable for integration tests.

The `Dockerfile` is kept very simple, which is sufficient for now. SUMO devs push pre-compiled executable to 
https://launchpad.net/~sumo/+archive/ubuntu/stable . The dockerfile grabs the latest stable one and installs it on top
of the `maven:3.9.9-eclipse-temurin-17` image. As a consequence we cannot define a specific version to be installed, but rather
build and tag the docker image with the latest SUMO version. This image should be built whenever a new major version of SUMO is released
and available in the PPA.

```shell script
docker build . -t eclipsemosaic/mosaic-ci:jdk17-sumo-1.24.0
docker login
docker push eclipsemosaic/mosaic-ci:jdk17-sumo-1.24.0
```  

Afterwards, the image should be available here: https://hub.docker.com/r/eclipsemosaic/mosaic-ci/tags

The image is referred in the `Jenkinsfile` in the root of this repository and should be updated too, in order to test if Eclipse MOSAIC 
is compatible with the latest SUMO version.