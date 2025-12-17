FROM ubuntu:noble

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
  cmake \
  libboost-dev \
  libboost-system-dev

WORKDIR /home/mosaic/bin/fed/ns3

COPY ./ns* ./

RUN ./ns3_installer.sh --yes 
RUN mkdir -p ns3config
RUN chmod -R 755 run.sh ns* && chmod -R 777 ns3config

VOLUME ["/home/mosaic/bin/fed/ns3/ns3config"]

EXPOSE 40001 40002

ENTRYPOINT ["./run.sh", "40001", "40002"]