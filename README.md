<a href="https://eclipse.dev/mosaic#gh-light-mode-only"><p align="center"><img width="50%" src="https://raw.githubusercontent.com/eclipse/mosaic.website/main/static/img/logos/mosaic/EclipseMOSAIC-Logo-RGB-positiv.svg#gh-light-mode-only"></p></a>
<a href="https://eclipse.dev/mosaic#gh-dark-mode-only"><p align="center"><img width="50%" src="https://raw.githubusercontent.com/eclipse/mosaic.website/main/static/img/logos/mosaic/EclipseMOSAIC-Logo-RGB-negativ.svg#gh-dark-mode-only"></p></a>

# Eclipse MOSAIC Essentials - <br> The Simulation Suite for Connected and Automated Mobility

[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](https://opensource.org/licenses/EPL-2.0)
![Made with java](https://img.shields.io/badge/Made%20with-Java-1f425f.svg) 
![Size](https://img.shields.io/github/repo-size/eclipse/mosaic.svg) <br>
[![Build](https://github.com/eclipse-mosaic/mosaic/workflows/Java%20CI%20with%20Maven/badge.svg)](https://github.com/eclipse-mosaic/mosaic/actions?query=branch%3Amain+workflow%3A%22Java+CI+with+Maven%22)
[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fci.eclipse.org%2Fmosaic%2Fjob%2Fmosaic%2Fjob%2Fmain&label=Jenkins%20build)](https://ci.eclipse.org/mosaic/job/mosaic/)


[**Eclipse MOSAIC**](https://eclipse.dev/mosaic) is a multi-scale simulation framework in the field of smart and connected mobility.
It allows coupling simulators from various domains towards a comprehensive simulation tool. 
Data exchange and time management is implemented by the Runtime Infrastructure (RTI), which is the heart of MOSAIC. Simulation models
are coupled to the RTI using HLA inspired interfaces; Each simulator is wrapped into a "Federate" object which is linked to an "Ambassador"
which is directly coupled with the RTI. Currently, the following simulators are coupled with the MOSAIC RTI:
  * MOSAIC Application (application simulation)
  * Eclipse SUMO (traffic simulation)
  * OMNeT++ (communication simulation)
  * ns-3 (communication simulation)
  * MOSAIC Cell Simulator (cellular communication simulation)
  * MOSAIC Simple Network Simulator (communication simulation)
  * MOSAIC Environment (environment and event simulation)  
  * MOSAIC Output Generator (evaluation and visualization)

The project is a Maven based multi-module project and has its child modules organized in three main categories:
  * `rti` - MOSAIC RTI: modules providing the runtime infrastructure API and implementation
  * `lib` - MOSAIC Libraries: mathematics, spatial, routing, utilities, communication models, and data exchange
  * `fed` - MOSAIC Ambassadors/Federates: simulators and couplings which integrate simulation models to Eclipse MOSAIC

## Attribution

If you use our simulation framework for your own research, we would be glad if the following reference is included in any published work for which Eclipse MOSAIC has been used:

> K. Schrab, M. Neubauer, R. Protzmann, I. Radusch, S. Manganiaris, P. Lytrivis, A. J. Amditis
_**“Modeling an ITS Management Solution for Mixed Highway Traffic with Eclipse MOSAIC."**_
IEEE Transactions on Intelligent Transportation Systems, pp. 1 - 11, Print ISSN: 1524-9050, Electronic ISSN: 1558-0016, DOI: 10.1109/TITS.2022.3204174

## Documentation

View our website at **[eclipse.dev/mosaic](https://eclipse.dev/mosaic)** for detailed documentation and many tutorials to get started with Eclipse MOSAIC. For a quick start on building and running the code in this repository, just jump to the bottom section of this README file.

## The Essential Edition of Eclipse MOSAIC

This repository contains the *Essential* edition of Eclipse MOSAIC, which includes the runtime infrastructure, 
the core libraries, and various implementations of simulators or couplings to existing ones. All features 
included in this version of Eclipse MOSAIC are sufficient for most use-cases in the field of smart and connected mobility.
Additional simulators and assessment features are provided by [Fraunhofer FOKUS](https://www.fokus.fraunhofer.de/go/asct) on a commercial basis.

## Related Repositories

* [Eclipse SUMO](https://github.com/eclipse/sumo) is coupled directly using the TraCI interface. We recommend using the SUMO release `1.24.0`.
* The coupling to [ns-3](https://www.nsnam.org) is realized by a federate implementation which can be found [in our MOSAIC Addons repository](https://github.com/mosaic-addons/ns3-federate). 
  We currently support ns-3 version `3.36.1`. 
* The coupling to [OMNeT++](https://omnetpp.org) is implemented in a very similar manner. The corresponding federate implementation can be found [in our MOSAIC Addons repository](https://github.com/mosaic-addons/omnetpp-federate). 
  We currently support OMNeT++ version `5.5` in combination with the INET framework in version `4.1`.  
* We created the [Berlin SUMO Traffic (BeST) scenario](https://github.com/mosaic-addons/best-scenario) which provides 2.2 million vehicle trips in 24h for Berlin, Germany. The scenario is fully compatible with the latest release of MOSAIC.
* In our research, we currently use Eclipse MOSAIC and the BeST scenario to evaluate traffic state estimation algorithms. For that, we provide [application models and tools for Traffic State Estimation](https://github.com/mosaic-addons/traffic-state-estimation) compatible with MOSAIC.

## Contact

Any questions regarding Eclipse MOSAIC can be asked, discussed, and found in the [Discussion section](https://github.com/eclipse-mosaic/mosaic/discussions) here at GitHub.

For further questions we are available via mosaic@fokus.fraunhofer.de

## Prerequisites

> [!IMPORTANT]  
> **In contrast to previous versions, Eclipse MOSAIC now requires at least Java 17 SDK.**

For a successful build you need the following software to be installed:

* **Maven 3.1.x** or higher.
* **Java 17**, or 21 - We recommend using the [Adoptium OpenJDK (aka Eclipse Temurin)](https://adoptium.net/de/temurin/releases?version=17).
* **SUMO 1.24.0** - Older versions > 1.2.0 are most probably supported, but not tested. The environment variable `SUMO_HOME` should be configured properly.

## Build

Eclipse MOSAIC is a Java base project using [Apache Maven](https://maven.apache.org/) for build and dependency management. 

Once installed, you can build Eclipse MOSAIC using the following command:

    mvn clean install
        
This command executes all tests as well. In order to skip test execution, the following command succeeds:

    mvn install -DskipTests
    
After building, a MOSAIC bundle including a start script and all necessary configurations is located in the `bundle\target` directory.

After extracting this bundle to an arbitrary path, Eclipse MOSAIC can be executed using:

    mosaic.sh -s HelloWorld
    mosaic.bat -s HelloWorld 

## IDE Setup

Apart from the aforementioned approach, the simulation can also be started in your IDE using the main method in `org.eclipse.mosaic.starter.MosaicStarter`. Please have a look at the setup page in the documentation [here](https://eclipse.dev/mosaic/docs/extending_mosaic/).

## License

Eclipse MOSAIC is licensed under the [Eclipse Public License Version 2](https://eclipse.dev/legal/epl-v20.html).

## Contributing

Before starting with contributions, please see [CONTRIBUTING information](CONTRIBUTING.md).
