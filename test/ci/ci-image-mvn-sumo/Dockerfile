FROM maven:3.9.9-eclipse-temurin-17

ENV DEBIAN_FRONTEND=noninteractive
ENV USER_NAME=jenkins
ENV HOME=/home/jenkins
ENV SUMO_HOME=/usr/share/sumo
WORKDIR /home/jenkins

RUN apt-get update &&  \
    apt-get install -y --allow-unauthenticated software-properties-common && \
    # adjust this output string to bypass potential caches
    echo "Installing SUMO 1.24.0" && \
    add-apt-repository ppa:sumo/stable && \
    apt-get install -y sumo