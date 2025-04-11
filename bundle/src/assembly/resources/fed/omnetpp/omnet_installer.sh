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
# omnet_installer.sh - A utility script to install OMNeT++/INET for MOSAIC.
# Ensure this file is executable via chmod a+x omnet_installer.
#


# ----------------------------------------
#
# Pre Tests
#
# ----------------------------------------

check_shell() {
   if [ -z "$BASH_VERSION" ]; then
      fail "This script requires the BASH shell"
      exit 1
   fi
}

check_shell


# ----------------------------------------
#
# Global Variables
#
# ----------------------------------------

# Required programs and libraries
required_programs=( unzip tar bison flex protoc gcc python )
required_libraries=( "libprotobuf-dev >= 3.7.0" "libxml2-dev" )

omnet_federate_url="https://github.com/mosaic-addons/omnetpp-federate/archive/refs/tags/25.0.zip"
omnet_src_url="https://github.com/omnetpp/omnetpp/releases/download/omnetpp-5.5.1/omnetpp-5.5.1-src-linux.tgz"
inet_src_url="https://github.com/inet-framework/inet/releases/download/v4.1.1/inet-4.1.1-src.tgz"

premake5_url="https://github.com/premake/premake-core/releases/download/v5.0.0-alpha15/premake-5.0.0-alpha15-linux.tar.gz"
premake5_tar="$(basename "$premake5_url")"
premake5_autoconf_url="https://github.com/Blizzard/premake-autoconf/archive/master.zip"
premake5_autoconf_zip="$(basename "$premake5_autoconf_url")"

# User arguments
arg_installation_type=UNSET # USER or DEVELOPER. If not defined by program argument, user will be asked during installtion process.
arg_integration_testing=false
arg_quiet=false
arg_uninstall=false
arg_omnet_tar=""
arg_federate_src_file=""
arg_inet_src_file=""
arg_make_parallel="-j$(nproc)"
arg_force=false
arg_skip_inet_installation=false
arg_skip_omnetpp_installation=false

#paths and names
omnet_dir_name_default="omnetpp-x.x"
federate_path="bin/fed/omnetpp"
omnet_dir_name="${omnet_dir_name_default}"
omnet_federate_filename="$(basename "$omnet_federate_url")"
omnet_src_filename="$(basename "$omnet_src_url")"
inet_src_filename="$(basename "$inet_src_url")"
working_directory="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# INET configuration: Disabled features
disabled_inet_features=(TCP_lwIP TCP_NSC INET_examples IPv6 IPv6_examples xMIPv6 xMIPv6_examples WiseRoute RTP RTP_examples SCTP SCTP_examples DHCP DHCP_examples Ethernet Ethernet_examples PPP ExternalInterface ExternalInterface_examples MPLS MPLS_examples BGPv4 BGPv4_examples PIM PIM_examples DYMO AODV AODV_examples RIP RIP_examples mobility_examples physicalenvironment_examples Ieee802154 apskradio wireless_examples VoIPStream VoIPStream_examples SimpleVoIP SimpleVoIP_examples HttpTools HttpTools_examples_direct HttpTools_examples_socket DiffServ DiffServ_examples InternetCloud InternetCloud_examples Ieee8021d Ieee8021d_examples TUN BMAC LMAC TCP_common TCP_INET)
downloaded_files=""


# ----------------------------------------
#
# Helpers
#
# ----------------------------------------

cyan="\033[01;36m"
red="\033[01;31m"
bold="\033[1m"
restore="\033[0m"
style_info="\033[1;37;44m"
style_question="\033[1;30;47m"
style_progress="\033[0;30;47m"
style_warning="\033[1;31m"
style_error="\033[1;37;41m"
style_success="\033[1;37;42m"
style_option_positive="\033[1;37;42m"
style_option_negative="\033[1;37;41m"

log() {
   STRING_ARG=$1
   printf "${STRING_ARG//%/\\%%}\n" ${*:2}
   return $?
}
info() {
   log "${restore}${style_info}INFO${restore} ${bold}$1${restore}" ${*:2}
}
success() {
   log "${style_success}SUCCESS${restore} ${bold}$1${restore}" ${*:2}
}
warn() {
   log "${style_warning}WARNING${restore} ${bold}$1${restore}\n" ${*:2}
}
error() {
   log "${style_error}ERROR${restore} ${bold}${red}$1${restore}" ${*:2}
}
fail() {
   error "$1" ${*:2}
   exit 1
}
progress() {
   log "${restore}${style_progress}Progress: $1${restore}" ${*:2}
}
ask_user() {
  log "\n${restore}${style_question}$1${restore}"
}
option_positive() {
  log "${restore}${style_option_positive} $1 ${restore} $2"
}
option_negative() {
  log "${restore}${style_option_negative} $1 ${restore} $2"
}

has() {
  return $(command -v "$1" > /dev/null)
}


# ----------------------------------------
#
# Configuration
#
# ----------------------------------------

print_usage() {
   log "${bold}${cyan}[$(basename "$0")] -- An OMNeT++/INET installation script for MOSAIC${restore}"
   log "\nUsage: $0 -s path/to/omnetpp-5.5.x-src-linux.tgz [arguments]"
   log "\nArguments:"
   log "\n -o, --omnetpp path/to/omnetpp-5.5.x-src-linux.tgz"
   log "\n     provide the archive containing the OMNeT++ source"
   log "\n     You can obtain it from ${cyan}https://omnetpp.org/download/"
   log "\n -f, --federate path/to/<omnetpp-federate-archive.zip>"
   log "\n     provide the archive containing the OMNeT++-federate and patches for coupling OMNeT++ to MOSAIC."
   log "\n     If not given, the omnetpp-federate is downloaded by this installation script."
   log "\n -i, --inet_src path/to/inet-4.1.x-src.tgz"
   log "\n     provide the archive containing the inet source code"
   log "\n     You can obtain it from ${cyan}https://inet.omnetpp.org/Download.html${restore}"
   log "\n     If not given, the inet-source files are downloaded by this installation script."
   log "\n -so, --skip-omnetpp"
   log "\n     skip the installation of OMNeT++"
   log "\n -si, --skip-inet"
   log "\n     skip the installation of INET"
   log "\n -t, --installation-type <INSTALLATION_TYPE>"
   log "\n     either USER or DEVELOPER"
   log "\n -q, --quiet"
   log "\n     less output, no interaction required"
   log "\n -j, --parallel <number of threads>"
   log "\n     enables make to use the given number of compilation threads"
   log "\n -u, --uninstall"
   log "\n     uninstalls the OMNeT++ federate"
   log "\n -h, --help"
   log "\n     shows this usage screen"
   log "\n"
}

get_program_arguments() {
  if [ "$#" -ge "1" ]; then
    if [ "${1:-}" == "-h" ] || [ "${1:-}" == "--help" ]; then
      print_usage
      exit 0
    else
      # note: if this is set to > 0 the /etc/hosts part is not recognized ( may be a bug )
      while [[ $# -ge 1 ]]
      do
        key="$1"
        case $key in
          -q|--quiet)
              arg_quiet=true
              ;;
          -o|--omnetpp|-s|--simulator)
              arg_omnet_tar="$2"
              shift # past argument
              ;;
          -i|--inet)
              arg_inet_src_file="$2"
              shift # past argument
              ;;
          -f|--federate)
              arg_federate_src_file="$2"
              shift # past argument
              ;;
          -t|--installation-type)
              arg_installation_type="$2"
              if [ "$arg_installation_type" != "USER" ] && [ "$arg_installation_type" != "DEVELOPER" ]; then
                fail "Value of argument '--installation-type' (-t) needs either to be 'USER' or 'DEVELOPER'."
              fi
              shift # past argument
              ;;
          -it|--integration_testing)
              arg_integration_testing=true
              arg_quiet=true
              ;;
          -si|--skip-inet)
              arg_skip_inet_installation=true
              ;;
          -so|--skip-omnetpp)
              arg_skip_omnetpp_installation=true
              ;;
          -F|--force)
              arg_force=true
              ;;
          -j|--parallel)
              if [[ $2 =~ ^[0-9]+$ ]]; then
                arg_make_parallel="-j$2"
              else
                warn "Value of argument '--parallel' (-j) is not a positive integer. Continuing installation with '-j1' ..."
              fi
              shift # past argument
              ;;
          -u|--uninstall)
              arg_uninstall=true
              ;;
        esac
        shift
      done
    fi
  fi
}

# Ask user for installation type
user_configuration_installation_type() {
  while true; do
    ask_user "Please choose your installation type:"
    option_positive "U" "Installation Type: User"
    option_positive "D" "Installation Type: Developer"
    option_negative "X" "Abort installation"
    read -r answer
    case $answer in
      [uU]* )
        arg_installation_type="USER"
        break;;

      [dD]* )
        arg_installation_type="DEVELOPER"
        break;;

      [xX]* )
        info "Installation aborted by user."
        exit;;

      * )
        log "Allowed choices are 'U', 'D' or 'X'. Please try again...";;
    esac
  done;
}

user_configuration_extract_omnet_dir_name() {
  if [ "$arg_omnet_tar" != "" ]; then
    tar_filename="$(basename "${arg_omnet_tar}" )"
  else
    tar_filename="$(basename "${omnet_src_url}" )"
  fi

  tmp_dir_name="${tar_filename%-src*}"
  if [ "${tar_filename}" == "${tmp_dir_name}" ]; then
    log "Warning: falling back to ${omnet_dir_name_default} as name for installation directory"
    omnet_dir_name="${omnet_dir_name_default}"
  else
    omnet_dir_name="${tmp_dir_name}"
  fi
}

user_configuration() {
  # Set type of installation: Ask user if not set by program argument (option -t)
  if [ "$arg_installation_type" == "UNSET" ]; then
    user_configuration_installation_type
  fi

  if [ "$arg_installation_type" == "USER" ]; then
    # Check if path to omnetpp tar ball is provided as program argument (option -o)
    user_configuration_extract_omnet_dir_name
  fi
}

configure_paths() {
  # where the inet source tree reside
  inet_src_dir="${working_directory}/inet_src"

  # where the inet ned files reside after successful build inet
  inet_target_dir="${working_directory}/inet"

  # where the omnetpp source tree reside
  omnetpp_src_dir="${working_directory}/${omnet_dir_name}"

  # where the omnetpp-federate tree reside
  omnetpp_federate_src_dir="${working_directory}/omnetpp_federate_src"

  # where the omnetpp and omnetpp-federate ned files reside after successful build of omnetpp and omnetpp-federate
  omnetpp_federate_target_dir="${working_directory}/omnetpp-federate"

  # where the omnetpp and omnetpp-federate binary reside
  omnetpp_federate_target_dir_bin="${omnetpp_federate_target_dir}/bin"

  # where the omnetpp and omnetpp-federate libraries reside
  omnetpp_federate_target_dir_lib="${omnetpp_federate_target_dir}/lib"
}

# Checks if /path/to/omnetpp/bin is in PATH and /path/to/omnetpp/lib is in LD_LIBRARY_PATH
# and creates /path/to/omnetpp/include and checks if all directories really exist.
extract_path_to_omnetpp() {
  if [[ ! $PATH =~ .*\/omnetpp-5\.5\.[0-9]+\/bin.* ]]; then
    fail "omnetpp-5.5.[0-9]+/bin is not in the \$PATH environment variable.";
  fi
  if [[ ! $LD_LIBRARY_PATH =~ .*\/omnetpp-5\.5\.[0-9]+\/lib.* ]]; then
    fail "omnetpp-5.5.[0-9]+/lib is not in the \$LD_LIBRARY_PATH environment variable.";
  fi

  omnetpp_src_dir_bin="$(echo "$PATH" | grep -m 1 -o -P "[^:]*\/omnetpp-5\.5\.[0-9]/bin" | head -1)"
  omnetpp_src_dir="$(dirname "$omnetpp_src_dir_bin")"
  if [ ! -d "${omnetpp_src_dir}/include" ]; then
    fail "Could not find OMNeT++ 'include' directory in: ${omnetpp_src_dir} ${restore}"
  fi
  if [ ! -d "${omnetpp_src_dir}/bin" ]; then
    fail "Could not find OMNeT++ 'bin' directory in: ${omnetpp_src_dir} ${restore}"
  fi

  arg_skip_omnetpp_installation=true
}

# Extracts path to inet source directory from LD_LIBRARY_PATH
extract_path_to_inet() {
  inet_src_dir="$(echo "$LD_LIBRARY_PATH" | grep -m 1 -o -P "[^:]*\/inet4?" | head -1)";
  if [ ! -d "${inet_src_dir}" ]; then
    fail "Could not find INET 'src' directory in: ${inet_src_dir} ${restore}"
  fi
  arg_skip_inet_installation=true
}

ask_for_dependencies() {
  if [ "$arg_installation_type" == "DEVELOPER" ]; then
    # Ask & check if OMNeT++ is already preinstalled on the system
    if [ "$arg_quiet" == "false" ]; then
      ask_user "Do you already have OMNeT++ 5.5.* installed on your system and linked in PATH and LD_LIBRARY_PATH?"
      option_positive "y" "Yes."
      option_negative "X" "No, abort installation."
      read -r answer
      if [[ $answer =~ [xX]{1} ]]; then
        info "Installation aborted by user."
        exit
      fi
    else
      info "Not asking user if OMNeT++ is already installed and linked in PATH and LD_LIBRARY_PATH, due to program argument '--quiet'."
    fi
    extract_path_to_omnetpp

    info "Currently INET needs to be patched to work with the MOSAIC OMNeT++ Federate.\n     Therefore it will be installed and patched in: ${working_directory}"
    # DO NOT DELETE THIS CODE
    # It may be used again if INET doesn't need to be patched anymore.
    #
    # # Ask & check if INET is already preinstalled on the system
    # if [ "$arg_quiet" == "false" ]; then
    #   ask_user "Do you already have INET 4.1.* installed on your system?"
    #   option_positive "y" "Yes."
    #   option_negative "X" "No, abort installation."
    #   read answer
    #   if [[ $answer =~ [xX]{1} ]]; then
    #     info "Installation aborted by user."
    #     exit
    #   fi
    # else
    #   info "Not asking user if INET is already installed and linked in LD_LIBRARY_PATH, due to program argument '--quiet'."
    # fi
    # extract_path_to_inet
  fi

  if [ "$arg_quiet" == "false" ]; then
    ask_user "Are the following dependencies installed on the system?"
    log " ${bold}Libraries:${restore}"
    for lib in "${required_libraries[@]}"; do
      log "${bold}${cyan}  $lib ${restore}"
    done

    log " ${bold}Programs:${restore}"
    for prog in "${required_programs[@]}"; do
      log "${bold}${cyan}  $prog ${restore}"
    done

    option_positive "y" "Yes"
    option_negative "X" "No, abort installation"
    read -r answer
    if [[ $answer =~ [xX]{1} ]]; then
      info "Installation aborted by user."
      exit
    fi
  fi
}

umask 027
set -o nounset
set -o errtrace
set -o errexit
set -o pipefail
trap clean_up INT

get_program_arguments "$@"
user_configuration
configure_paths
ask_for_dependencies


# ----------------------------------------
#
# Installation Preparation
#
# ----------------------------------------

clean_up() {
  progress "Cleaning up ..."
  #Always remove temporary files
  if [ -d "${inet_src_dir}" ]; then
    rm -rf "${inet_src_dir}"
  fi
  if [ -d "${omnetpp_src_dir}" ]; then
    rm -rf "${omnetpp_src_dir}"
  fi
  if [ -d "${omnetpp_federate_src_dir}" ]; then
    rm -rf "${omnetpp_federate_src_dir}"
  fi

  #Remove the downloaded files if wanted
  if [ -z "$downloaded_files" ]; then
    return
  fi

  if [ "$arg_integration_testing" = false ]; then
    while true; do
      log "Do you want to remove the following files and folders? ${bold}${red} $downloaded_files ${restore} \n[y/n] "
      if $arg_quiet; then
        answer=Y
      else
        read -r answer
      fi
      case $answer in
        [Yy]* ) break;;
        [Nn]* ) return;;
        * ) echo "Allowed choices are yes or no";;
      esac
    done;
  fi

  cd "$working_directory"
  rm -rf $downloaded_files
}

uninstall() {
  if [ "$arg_uninstall" == "true" ]; then
    warn "Uninstalling..."
    cd "$working_directory"
    if [ "$arg_installation_type" == "USER" ]; then
      if [ -d "${inet_target_dir}" ]; then
        warn "Deleting INET directory..."
        rm -rf "${inet_target_dir}"
      fi
      if [ -d "${omnet_dir_name}" ]; then
        warn "Deleting OMNeT++ directory..."
        rm -rf "${omnet_dir_name}"
      fi
    fi

    if [ -d "${omnetpp_federate_target_dir}" ]; then
        warn "Deleting OMNeT++ federate directory..."
      rm -rf "${omnetpp_federate_target_dir}"
    fi
    find . -maxdepth 1 -type d -name "omnetpp-*.*" -exec rm -rf {} \;
    #call normal cleanup to remove temporary and downloaded files
    clean_up
    exit 0
  fi
}

# Workaround for integration testing
set_environment_variables() {
  if [ "$arg_installation_type" == "USER" ]; then
    export PATH="$PATH:${omnetpp_federate_target_dir_bin}"
    export LD_LIBRARY_PATH="${omnetpp_federate_target_dir_lib}"
  fi
}

omnetpp_install_ok=true
inet_install_ok=true
federate_install_ok=true
check_install () {
  if [ "${arg_force}" == "false" ]; then
    if [ -d "${omnetpp_federate_target_dir}" ]; then
      if [ -d "${omnetpp_federate_target_dir}/omnetpp_federate" ]; then
        if [ ! -f "${omnetpp_federate_target_dir}/omnetpp_federate/package.ned" ]; then
          federate_install_ok=false
        fi
      else
        federate_install_ok=false
      fi
    else
      federate_install_ok=false
    fi

    if [ -d "${omnetpp_federate_target_dir_bin}" ]; then
      if [ ! -f "${omnetpp_federate_target_dir_bin}/opp_msgc" ]; then
        omnetpp_install_ok=false
      fi
      if [ ! -f "${omnetpp_federate_target_dir_bin}/omnetpp-federate" ]; then
        federate_install_ok=false
      fi
    else
      omnetpp_install_ok=false
      federate_install_ok=false
    fi

    if [ -d "${omnetpp_federate_target_dir_lib}" ]; then
      if [ ! -f "${omnetpp_federate_target_dir_lib}/liboppenvir_dbg.so" ]; then
        omnetpp_install_ok=false
      fi
      if [ ! -f "${omnetpp_federate_target_dir_lib}/libomnetpp-federate.so" ]; then
        federate_install_ok=false
      fi
      if [ ! -f "${omnetpp_federate_target_dir_lib}/libINET_dbg.so" ]; then
        inet_install_ok=false
      fi
    else
      omnetpp_install_ok=false
      federate_install_ok=false
    fi

    if [ -d "${inet_target_dir}" ]; then
      if [ ! -f "${inet_target_dir}/libINET_dbg.so" ]; then
        inet_install_ok=false
      fi
      if [ -d "${inet_target_dir}/inet" ]; then
        if [ ! -f "${inet_target_dir}/inet/package.ned" ]; then
          inet_install_ok=false
        fi
      else
        inet_install_ok=false
      fi

    else
      inet_install_ok=false
    fi

    if [ "${omnetpp_install_ok}" == "true" ] && [ "${inet_install_ok}" == "true" ] && [ "${federate_install_ok}" == "true" ]; then
      info "OMNeT++ federate already installed. Use -F or --force to overwrite existing installation."
      exit 0
    fi
  fi
}

check_required_programs() {
  for package in $1; do
    if ! has "$package"; then
      fail "'$package' required, but it's not installed. Please install the package (sudo apt-get install for Ubuntu/Debian) and try again."
    fi
  done
}

check_directory() {
  cd "$working_directory"
  federate_working_directory="$(echo "$working_directory" | rev | cut -c -${#federate_path} | rev)"
  if [ "$federate_working_directory" == "$federate_path" ]; then
    return
  else
    fail "This doesn't look like a MOSAIC directory. Please make sure this script is started from '$federate_path'."
  fi
}


uninstall # Uninstall and exit if porgram argument says so
#print_info
set_environment_variables
check_install
check_required_programs "${required_programs[*]}"
check_directory

# ----------------------------------------
#
# Installation
#
# ----------------------------------------

download() {
  title="$1"
  url="$2"
  error_message=""
  if [ $# -ge 3 ]; then
    error_message="$3"
  fi
  progress "Downloading '$title' from: $url"
  if [ ! -f "$(basename "$url")" ]; then
    if has wget; then
      if !   wget -q "$url"; then
        error "The download URL seems to have changed. File not found: '$url'";
        if [ "$error_message" != "" ]; then
          info "$error_message"
        fi
        exit 1
      fi
    elif has curl; then
      curl -s -O "$url" || fail "The download URL seems to have changed. File not found: '$url'";
    else
      error "Can't download '$url'."
      if [ "$error_message" != "" ]; then
        info "$error_message"
      fi
    fi
  else
    warn "File $(basename "$url") already exists. Skipping download."
    if [ "$error_message" != "" ]; then
      info "$error_message"
    fi
  fi
}

# Premake
# ----------------------------------------
extract_premake() {
  download "premake5" "$premake5_url"
  download "premake-autoconf" "$premake5_autoconf_url"
  progress "Extracting premake and premake-autoconf ..."
  if [ ! -d "${omnetpp_federate_src_dir}" ]; then
    fail "Directory ${omnetpp_federate_src_dir} doesn't exists. Abort!"
  fi
  oldpwd=$(pwd)
  cd "${omnetpp_federate_src_dir}"
  tar xvf "../$premake5_tar"
  unzip "../$premake5_autoconf_zip"
  cp premake-autoconf-master/api.lua .
  cp premake-autoconf-master/autoconf.lua .
  cp premake-autoconf-master/clang.lua .
  cp premake-autoconf-master/gcc.lua .
  cp premake-autoconf-master/msc.lua .
  rm -fr premake-autoconf-master
  cd "$oldpwd"
}

# OMNeT++
# ----------------------------------------
extract_omnet() {
   progress "Extracting OMNeT++ from '$1'..."
   cd "$working_directory"
   arg1="$1" #omnet archive
   if [ -f "$1" ]; then
      if [ -d "${omnetpp_src_dir}" ]; then
         fail "${omnetpp_src_dir} exists, please uninstall the existing installation before proceeding (-u or --uninstall)"
         exit 1;
      fi
      tar -xf "$arg1"
   else
      fail "${1} not found! Abort!";
   fi
}

configure_omnet() {
  progress "Configuring OMNeT++..."
  cd "${omnetpp_src_dir}"
  PATH=$(pwd -L)/bin:$PATH
  export PATH
  sed -i -e "s/PREFER_CLANG=yes/PREFER_CLANG=no/" configure.user
  ./configure WITH_OSG=no WITH_TKENV=no WITH_QTENV=no WITH_OSGEARTH=no WITH_PARSIM=no
}

build_omnet() {
  progress "Building OMNeT++ ($arg_make_parallel) ..."
  cd "${omnetpp_src_dir}"
  make $arg_make_parallel MODE=debug base
  export PATH="${omnetpp_src_dir}/bin":$PATH
  mkdir -p "${omnetpp_federate_target_dir_bin}"
  cp -r bin "${omnetpp_federate_target_dir}"
  cp -r lib "${omnetpp_federate_target_dir}"
}

# INET
# ----------------------------------------
extract_inet() {
  progress "Extracting INET from: $1 ..."
  cd "$working_directory"
  if [ -f "$1" ]; then
    if [ -d "${inet_src_dir}" ]; then
      fail "${inet_src_dir} exists, please uninstall the existing installation before proceeding (-u or --uninstall)"
      exit 1;
    fi
    tar -xf "$1"
    cd "$working_directory"
    mv inet4 "${inet_src_dir}"
    mkdir -p "${inet_target_dir}" # same name
  else
    fail "${1} not found! Abort!";
  fi
}

configure_inet() {
  progress "Configuring INET ..."
  cd "${inet_src_dir}"

  # Patch inet feature tool if python >= 3.0.0
  python_version_current="$(python --version 2>&1)"
  python_version_3="Python 3."
  if [[ "$python_version_current" == "$python_version_3"* ]]; then
    sed -i -e "s|raw_input|input|" ./bin/inet_featuretool
  fi

  # Disable unneeded features
  for feat in "${disabled_inet_features[@]}"; do
    echo "Disabling INET feature: $feat"
    echo "yes" | ./bin/inet_featuretool disable $feat > /dev/null
  done
  make makefiles
}

build_inet() {
  progress "Building INET framework ..."
  mkdir -p "${omnetpp_federate_target_dir_lib}"
  cd "${inet_src_dir}"
  make $arg_make_parallel MODE=debug
  if [ -f "out/gcc-debug/src/libINET_dbg.so" ]; then
    cp "out/gcc-debug/src/libINET_dbg.so" "${inet_target_dir}"
    cp "out/gcc-debug/src/libINET_dbg.so" "${omnetpp_federate_target_dir_lib}"
  else
    fail "Shared library \"libINET_dbg.so\" not found. Something went wrong while building INET."
  fi
  if [ -d "src" ]; then
    (cd "src"; tar -cf - $(find . -name "*.ned" -print) | ( cd "${inet_target_dir}" && tar xBf - ))
  else
    fail "Directory \"src\" not found. Something went wrong while building INET."
  fi
  cd "${working_directory}"
}

# OMNeT++ Federate
# ----------------------------------------
extract_federate() {
  cd "${working_directory}"
  if [ ! -f "$1" ]; then
    info "File at provided path to federate (-f) does not exist: $1"
    download "MOSAIC OMNeT++ Federate" "$omnet_federate_url" "Please try using option '-f' to provide the path to your local OMNeT++ federate tar ball."
    downloaded_files="$downloaded_files $omnet_federate_filename"

    progress "Extracting MOSAIC OMNeT++ Federate from: ${omnet_federate_filename} ..."
    unzip --qq -o "$omnet_federate_filename"
    mv omnetpp-federate-* "${omnetpp_federate_src_dir}"
  else
    progress "Extracting MOSAIC OMNeT++ Federate from: '$1' ..."
    unzip --qq -o "$1"
    mv omnetpp-federate-* "${omnetpp_federate_src_dir}"
  fi
  chmod 755 -R "${omnetpp_federate_src_dir}"
}

build_omnet_federate() {
  progress "Building MOSAIC OMNeT++ Federate ..."

  mkdir -p "$omnetpp_federate_target_dir"
  mkdir -p "$omnetpp_federate_target_dir_bin"
  mkdir -p "$omnetpp_federate_target_dir_lib"

  cd "$omnetpp_federate_src_dir"

  if [ -f ClientServerChannelMessages.pb.h ]; then
    rm ClientServerChannelMessages.pb.h
  fi
  if [ -f ClientServerChannelMessages.pb.cc ]; then
    rm ClientServerChannelMessages.pb.cc
  fi

  sed -i -e "s|/usr/local|.|" premake5.lua
  sed -i -e "s|\"/usr/include\"|\"${omnetpp_src_dir}/include\", \"${inet_src_dir}/src\"|" premake5.lua
  sed -i -e "s|\"/usr/lib\"|\"${omnetpp_src_dir}/lib\", \"${inet_src_dir}/src\"|" premake5.lua
  sed -i -e "s|\$\$ORIGIN\/lib'|\$\$ORIGIN\/lib',-rpath,'\$\$ORIGIN\/..\/inet',-rpath,'${omnetpp_src_dir}\/lib'|" premake5.lua
  sed -i -e "s|\" --msg6 -I /usr/lib\"|\" --msg6 -I ${inet_src_dir}/src\"|" premake5.lua
  sed -i -e "s|'opp_msgc'|'${omnetpp_src_dir}/bin/opp_msgc'|" premake5.lua
  sed -i -e "s|/share/ned||" premake5.lua
  sed -i -e "s|local PROTO_CC_PATH = \"\.\"|local PROTO_CC_PATH = \"src/util\"|" premake5.lua

  ./premake5 gmake --generate-opp-messages --generate-protobuf --install

  make config=debug clean
  make -j1 config=debug # The federate can only be build with a single process

  cp bin/Debug/omnetpp-federate "$omnetpp_federate_target_dir_bin"
  ln -s "${omnetpp_federate_target_dir_bin}/omnetpp-federate" "$omnetpp_federate_target_dir"
  cp lib/libomnetpp-federate.so "$omnetpp_federate_target_dir_lib"
  cp -r src "$omnetpp_federate_target_dir"
  cd "$working_directory"
}

# Install OMNeT++
if [ "$omnetpp_install_ok" == "false" ] && [ "$arg_skip_omnetpp_installation" == "false" ]; then
  if [ ! -f "$arg_omnet_tar" ]; then
    download "OMNeT++" "$omnet_src_url" "Please try using option '-o' to provide the path to your local OMNeT++ tar ball."
    downloaded_files="$downloaded_files $omnet_src_filename"
    extract_omnet "$omnet_src_filename"
  else
    extract_omnet "$arg_omnet_tar"
  fi
  configure_omnet
  build_omnet
fi

# Extract OMNeT++ Federate
# (INET patch is included in federate source)
if [ "$federate_install_ok" == "false" ] || [ "$inet_install_ok" == "false" ]; then
  extract_federate "$arg_federate_src_file"
fi

# Install INET
if [ "$inet_install_ok" == "false" ] && [ "$arg_skip_inet_installation" == "false" ]; then
  if [ ! -f "$arg_inet_src_file" ]; then
    download "INET" "$inet_src_url" "Please try using option '-i' to provide the path to your local INET tar ball."
    downloaded_files="$downloaded_files $inet_src_filename"
    extract_inet "$inet_src_filename"
  else
    extract_inet "$arg_inet_src_file"
  fi
  configure_inet
  build_inet
fi

# Install OMNeT++ Federate
if [ "$federate_install_ok" == "false" ]; then
  extract_premake
  build_omnet_federate
fi

success "The MOSAIC OMNeT++ Federate was successfully installed."
