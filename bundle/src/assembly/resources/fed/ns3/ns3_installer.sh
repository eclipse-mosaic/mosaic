#!/usr/bin/env bash
#
# Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
# Contact: mosaic@fokus.fraunhofer.de
#
################################################################################
#
# ns3_installer.sh - A utility script to install ns-3 for MOSAIC.
# Ensure this file is executable via chmod a+x ns3_installer.
#

clear

umask 027

set -o nounset
set -o errtrace
set -o errexit
set -o pipefail

trap clean_fail_files INT

cyan="\033[01;36m"
red="\033[01;31m"
bold="\033[1m"
restore="\033[0m"

arg_yes=false
arg_ns3_file=""
arg_federate_file=""
arg_regen_protobuf=true
arg_dev=false
arg_fail_clean=true
arg_uninstall=false
arg_integration_testing=false

required_programs_display=( python3 gcc unzip tar protobuf-compiler )
required_programs_test=( python3 gcc unzip tar protoc )
required_libraries=( "libprotobuf-dev >= 3.7.0" "libxml2-dev" "libsqlite3-dev" )

ns3_version="3.45"

premake5_url="https://github.com/premake/premake-core/releases/download/v5.0.0-beta1/premake-5.0.0-beta1-linux.tar.gz"
premake5_tar="$(basename "$premake5_url")"
ns3_long_affix="ns-allinone-$ns3_version"
ns3_short_affix="ns-$ns3_version"
federate_path="bin/fed/ns3"
working_directory="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ns3_installation_path=${working_directory}
ns3_simulator_path="${working_directory}/$ns3_long_affix/$ns3_short_affix" #due to the ns3 tarball structure

ns3_federate_url="https://github.com/mosaic-addons/ns3-federate/archive/refs/tags/25.2.zip"
ns3_url="https://www.nsnam.org/releases/$ns3_long_affix.tar.bz2"
ns3_federate_filename="ns3-federate-$(basename "$ns3_federate_url")"
ns3_filename="$(basename "$ns3_url")"

temporary_files=""
uninstall_files="license_gplv2.txt run.sh $ns3_short_affix $ns3_long_affix"

print_help() {
    log "\nUsage: ns3_installer.sh [options]\n"
    log "Options:\n"
    log "   -h --help\t\t\t\tPrint this help"
    log "   -y --yes\t\t\t\tThe script will not require any input."
    log "   -s --simulator <ns3 archive>\t\tThe script will not attempt to download NS3 but use the given argument."
    log "   -f --federate <federate archive>\tThe script will not attempt to download the federate archive but use the given argument."
    log "   -s --skip-gen-protobuf\t\tDo not regenerate Protobuf c++ source."
    log "   -k --keep-src\t\t\tSource code is not removed after installation."
    log "   -c --no-clean-on-failure\t\tDo not remove installation files when install fails."
    log "   -u --uninstall       Remove the ns-3 federate"
    log "\n"
}

get_arguments() {
    while [[ $# -ge 1 ]]
    do
        key="$1"
        case $key in
            -h|--help)
                arg_yes=true
                print_info
                print_help
                exit 1
                ;;
            -y|--yes)
                arg_yes=true
                ;;
            -s|--simulator)
                arg_ns3_file="$2"
                ns3_filename="$2"
                shift # past argument
                ;;
            -f|--federate)
                arg_federate_file="$2"
                ns3_federate_filename="$2"
                shift # past argument
                ;;
            -s|--skip-gen-protobuf)
                arg_regen_protobuf=false
                ;;
            -k|--keep-src)
                arg_dev=true
                ;;
            -c|--no-clean-on-failure)
                arg_fail_clean=false
                ;;
            -u|--uninstall)
                arg_uninstall=true
                ;;
            # non-advertised options
            -it|--integration-testing)
                arg_integration_testing=true
                arg_yes=true
                ;;
        esac
    shift
    done
}

#################### Printing functions ##################

log() {
    STRING_ARG=$1
    printf "${STRING_ARG//%/\\%%}\n" ${*:2}
    return $?
}

warn() {
    log "${bold}${red}\nWARNING: $1\n${restore}" ${*:2}
}

fail() {
    log "${bold}${red}\nERROR: $1\n${restore}" ${*:2}
    clean_fail_files
    exit 1
}

check_uninstall() {
    if $arg_uninstall; then
        log "Removing ns-3 federate"
        cd "$working_directory"
        rm -rf $uninstall_files federate
        exit 0
    fi
}

print_usage() {
    log "${bold}${cyan}[$(basename "$0")] -- A ns-3 installation script for MOSAIC${restore}"
    log "\nUsage: $0 [arguments]"
    fail "Argument \""$1"\" not known."
}

print_info() {
    log "${bold}${cyan}[$(basename "$0")] -- A ns-3 installation script for MOSAIC${restore}"
    log "\nMOSAIC developer team <mosaic@fokus.fraunhofer.de>"
    log "\nThis shell script will download and install the NS3 network simulator version $ns3_version."
    log "\nPlease make sure you have installed the packages g++ libsqlite3-dev libxml2-dev libprotobuf-dev >= 3.7.0 ."
    log "\nIf there is an error (like a missing package) during the installation, the output may give hints about what went wrong.\n"
    if [ "$arg_yes" = false ]; then
        read -p "Press any key to continue..." -n1 -s
        log "\n"
    fi
}

print_success() {
    log "${bold}\nDone! ns-3 was successfully installed.${restore}"
}

################## Checking functions #################

has() {
    return $( which $1 >/dev/null )
}

check_shell() {
    if [ -z "$BASH_VERSION" ]; then
        fail "This script requires the BASH shell"
        exit 1
    fi
}

check_required_programs()
{
    for package in $1; do
        if ! has $package; then
            fail ""$package" required, but it's not installed. Please install the package (sudo apt-get install for Ubuntu/Debian) and try again.";
        fi
    done
}

check_directory() {
    cd "$working_directory"
    federate_working_directory=`echo "$working_directory" | rev | cut -c -${#federate_path} | rev`
    if [ "$federate_working_directory" == "$federate_path" ]; then
        return
    else
        fail "This doesn't look like a MOSAIC directory. Please make sure this script is started from "$federate_path"."
    fi
}

check_nslog() {
    if [[ ! $NS_LOG =~ .*level.* ]]; then
        log "Logging probably not correctly initialized"
    fi
}

ask_dependencies()
{
    if $arg_integration_testing || $arg_yes; then
        return
    fi

    while  [ true ]; do
        log "Are the following dependencies installed on the system? \n"
        log "${bold}Libraries:${restore}"
        for lib in "${required_libraries[@]}"; do
        log "${bold}${cyan} $lib ${restore}"
        done
        log "\n${bold}Programs:${restore}"
        for prog in "${required_programs_display[@]}"; do
        log "${bold}${cyan} $prog ${restore}"
        done
        printf "\n[y/n] "
        read answer
        case $answer in
            [Yy]* ) break;;
            [Nn]* )
                log "\n${red}Please install the required dependencies before proceeding with the installation process${restore}\n"
                exit;;
            * ) echo "Allowed choices are yes or no";;
        esac
    done;
}

################### Downloading and installing ##########

download() {
    if [ "$#" -eq 1 ]; then
        # basename of url and downloaded file have to be identical
        filename=$(basename "$1")
        url=$1
    fi
    if [ "$#" -eq 2 ]; then
        filename=$(basename "$1")
        url=$2
    fi

    if [ ! -f "$filename" ]; then
        if has wget; then
            wget --no-check-certificate -q "$url" || fail "The server is not reachable or the download URL has changed. File not found: "$url"";
            temporary_files="$temporary_files $filename"
        elif has curl; then
            curl -s -O "$url" || fail "The server is not reachable or the download URL has changed. File not found: "$url"";
            temporary_files="$temporary_files $filename"
        else
            fail "Can't download "$url".";
        fi
    else
        warn "File $filename already exists. Skipping download."
    fi
}

download_premake5() {
    log "Downloading premake5 from ${premake5_url}..."
    download "$premake5_url"
}

download_ns3() {
    if [ ! -z "$arg_ns3_file" ]; then
        log "NS3 given as argument"
        return
    fi
    log "Downloading NS3 from $ns3_url..."
    download "$ns3_url"
}

download_federate() {
    if [ ! -z "$arg_federate_file" ]; then
        log "federate given as argument"
        return
    fi
    log "Downloading federate from "$ns3_federate_url"..."
    download "$ns3_federate_url"
}

extract_ns3()
{
    if [ ! -d "$2/ns3_long_affix" ]; then
        arg1="$1"
        arg2="$2"
        tar --ignore-command-error -C "$arg2" -xf "$arg1"
    else
        fail "Directory in "$2" already exists."
    fi
}

extract_ns3_federate()
{
    if [ -d "./federate" ]; then
        fail "Directory federate in "." already exists.";
    fi

    if [ "$arg_dev" == "true" ]; then
        log "Keep federate source files"
    else
        temporary_files="$temporary_files federate"
    fi

    unzip --qq -o "$(basename "$ns3_federate_url")"
    # The archive should have contained the folder "ns3-federate-xxx".
    # Rename it to "federate":
    mv $(basename -s .zip $ns3_federate_filename) federate
}

extract_premake() {
    if [ ! -d "./federate" ]; then
        fail "Directory federate doesn't exists."
    fi
    oldpwd=`pwd`
    cd federate
    tar xvf ../$premake5_tar
    cd "$oldpwd"
}

build_ns3()
{
    log "Patch ns3"
    # create patch with: `git diff master 1057-lte-in-ns3.36-integration --output=../ns3-federate/patches/ns3-lte.patch`
    cd ${ns3_installation_path}/${ns3_long_affix}/${ns3_short_affix}
    patch --strip=1 --input=${ns3_installation_path}/federate/patches/ns3.45-lte.patch

    log "Build ns3 version ${ns3_version}"
    cd "${ns3_installation_path}/${ns3_long_affix}/${ns3_short_affix}"
    ./ns3 configure
    ./ns3 build
}

build_ns3_federate()
{
    log "Build ns3-federate"
    cd ${ns3_installation_path}/federate

    if [ "${arg_regen_protobuf}" == "true" ]; then
        if [ -f src/ClientServerChannelMessages.pb.h ]; then
            rm src/ClientServerChannelMessages.pb.h
        fi
        if [ -f src/ClientServerChannelMessages.pb.cc ]; then
            rm src/ClientServerChannelMessages.pb.cc
        fi
            ./premake5 gmake --generate-protobuf
        else
            ./premake5 gmake
    fi
    make config=debug clean
    # make is running targets in parallel, but we have to build 'prebuild'-target, target, and 'postbuild'-target sequentially
    make -j1 config=debug 
    mv ./bin/Debug/ns3-federate ./bin
}

deploy_ns3()
{
    log "Deploy ns3" # aka remove what we don't need
    if [ "$arg_dev" == "true" ]; then
        # will not delete ns3 source files in order to recompile depending on your needs
        # this now will copy 1.8GB (instead of 470MB) at beginning of each simulation run!
        log "Keep ns3 source files"
    else
        # delete everything but the compiled files inside of `long/short/build/lib`
        log "Delete ns3 source files"
        cd $ns3_installation_path
        cd $ns3_long_affix
        find . .* -maxdepth 0 -not -name . -not -name .. | xargs rm -r
        find .  * -maxdepth 0 -not -name . -not -name .. -not -name $ns3_short_affix | xargs rm -r
        cd $ns3_short_affix
        find . .* -maxdepth 0 -not -name . -not -name .. | xargs rm -r
        find .  * -maxdepth 0 -not -name . -not -name .. -not -name build | xargs rm -r
        cd build
        find .  * -maxdepth 0 -not -name . -not -name .. -not -name lib | xargs rm -r
    fi
}

deploy_ns3_federate()
{
    log "Deploy ns3-federate"
    cd $ns3_installation_path
    mv ./federate/bin/ns3-federate .
    cp -f ./federate/run_from_mosaic.sh ./run.sh
    chmod +x ./run.sh
}

uninstall()
{
    cd "$working_directory"
    warn "Uninstalling all ns-3 files"
    rm -rf $uninstall_files
}

clean_fail_files()
{
    if [ "$arg_fail_clean" = "true" ]; then
        cd "$working_directory"
        rm -rf $uninstall_files #2>/dev/null
        clean_up
    fi
}

clean_up()
{
    cd "$working_directory"

    #remove temporary files if wanted
    if [ -z "$temporary_files" ]; then
        return
    fi
    if [ "$arg_integration_testing" = false ]; then
        while  [ true ]; do
            log "Do you want to remove the following files and folders? ${bold}${red} $temporary_files ${restore} \n[y/n] "
        if $arg_yes; then
                answer=Y
            else
                read answer
            fi
            case $answer in
                [Yy]* ) rm -rf $temporary_files 2>/dev/null
                        break;;
                [Nn]* ) break;;
                * ) echo "Allowed choices are yes or no";;
            esac
        done;
    fi
}


# Workaround for integration testing
set_nslog() {
    export NS_LOG="'*=level_all|prefix'"
}

##################                   #################
################## Begin script flow #################

check_shell

get_arguments $*

check_uninstall

print_info

ask_dependencies

log "Preparing installation..."
check_required_programs "${required_programs_test[*]}"
check_directory

download_ns3

download_federate

download_premake5

log "Extracting "$ns3_filename"..."
extract_ns3 "$ns3_filename" .

log "Extracting "$(basename "$ns3_federate_url")"..."
extract_ns3_federate

extract_premake

log "Building ns-3..."
build_ns3
build_ns3_federate

log "Deploying ns-3..."
deploy_ns3
deploy_ns3_federate

log "Set ns-3 debug-levels..."
set_nslog
check_nslog

log "Cleaning up..."
clean_up

print_success
