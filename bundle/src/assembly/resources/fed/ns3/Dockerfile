FROM ubuntu:jammy

LABEL \
    description="Docker image containing the MOSAIC adapted ns-3 federate" \
    version="${pom.version}" \
    maintainer="mosaic@fokus.fraunhofer.de"

RUN \
  apt-get update && \
  apt-get install -y --allow-unauthenticated \
  build-essential \
  gcc \
  g++ \
  pkg-config \
  lbzip2 \
  libprotobuf-dev \
  libsqlite3-dev \
  libxml2-dev \
  protobuf-compiler \
  patch \
  python3 \
  unzip \
  rsync \
  wget \
  cmake

WORKDIR /home/mosaic/bin/fed/ns3

COPY ./ns* ./

RUN \
    ./ns3_installer.sh --yes && \
    mkdir -p ns3/scratch && \
    chmod -R 755 run.sh ns-allinone-3* && \
    chmod -R 777 ns3

VOLUME ["/home/mosaic/bin/fed/ns3/scratch"]

EXPOSE 40001 40002

ENTRYPOINT \
    cp scratch/* ns3/scratch && \
    ./run.sh 40001 40002