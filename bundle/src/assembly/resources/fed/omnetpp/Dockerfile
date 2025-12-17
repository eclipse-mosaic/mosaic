FROM ubuntu:jammy

LABEL \
    description="Docker image containing the MOSAIC adapted OMNeT++ federate" \
    version="${pom.version}" \
    maintainer="mosaic@fokus.fraunhofer.de"

RUN \
    apt-get update && \
    apt-get install -y --allow-unauthenticated \
    build-essential \
    bison \
    flex \
    libprotobuf-dev \
    protobuf-compiler \
    libxml2-dev \
    python3 \
    python3-pip \
    python3.10-venv \
    python-is-python3 \
    unzip \
    wget \
    pkg-config

WORKDIR /home/mosaic/bin/fed/omnetpp

COPY ./*.tgz omnet_installer.sh ./

RUN \
    ls -all && \
    ./omnet_installer.sh -q -t USER && \
    mkdir -p omnetpp-federate/simulations && \
    chmod -R 755 inet omnetpp-6.1 && \
    chmod -R 777 omnetpp-federate

EXPOSE 40001 40002

ENTRYPOINT \
    omnetpp-federate/omnetpp-federate -u Cmdenv \
    -f omnetpp-federate/simulations/omnetpp.ini \
    -n "inet:omnetpp-federate/src:simu5g" \
    --mosaiceventscheduler-host=0.0.0.0 \
    --mosaiceventscheduler-port=40001 \
    --mosaiccmd-port=40002
