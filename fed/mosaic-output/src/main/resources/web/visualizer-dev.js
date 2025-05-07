import {Feature, Map, View} from 'ol'
import {Tile as TileLayer} from 'ol/layer'
import {OSM} from 'ol/source'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Point from 'ol/geom/Point'
import {fromLonLat} from 'ol/proj'
import {Icon, Style} from 'ol/style'

/**
 * Represents an RSU.
 */
const Rsu = {
    name: 'unnamed_rsu',
    latitude: 0,
    longitude: 0,
    marker: null,
    timeStateChange: 0,
    state: {},

    init(name, latitude, longitude) {
        this.name = name
        this.latitude = latitude
        this.longitude = longitude
        this.marker = new Feature({
            type: 'rsu',
            geometry: new Point(fromLonLat([longitude, latitude]))
        })
        this.marker.setProperties(['name'])
        this.marker.set('name', name)
        this.state = {
            sending: false,
            receiving: false,
        };
    },

    getMarker() {
        return this.marker
    },

    setIsEquipped(isEquipped) {
        this.state.equipped = isEquipped
    },

    setLocation(latitude, longitude) {
        this.latitude = latitude
        this.longitude = longitude
    },

    setState(stateName) {
        if (this.state[stateName] !== undefined) {
            this.state[stateName] = true
            this.timeStateChange = Date.now();
        }
    },

    /**
     * Updates the marker of the RSU.
     */
    updateView() {
        // Location
        this.marker.setGeometry(new Point(fromLonLat([this.longitude, this.latitude])))

        // Update style
        const style = this.createStyle()
        this.marker.setStyle(style)

        if ((Date.now() - this.timeStateChange) > 500) {
            // Clear sending/receiving states
            this.state.sending = false
            this.state.receiving = false
        }

    },

    /**
     * Creates the style of the RSU marker based on the RSUs state.
     */
    createStyle() {
        let style = 'roadside-unit'
        if (this.state.equipped) {
            style = 'roadside-unit-equipped'
        }
        if (this.state.sending) {
            style = 'roadside-unit-sending'
        }
        if (this.state.receiving) {
            style = 'roadside-unit-receiving'
        }
        return new Style({
            image: new Icon({
                anchor: [0.5, 1],
                src: `markers/${style}.png`
            })
        })
    }
}

/**
 * Represents a vehicle.
 */
const Vehicle = {
    name: 'unnamed_vehicle',
    latitude: 0,
    longitude: 0,
    marker: null,
    timeStateChange: 0,
    vehicleClass: null,
    state: {},

    init(name, vClass) {
        this.name = name
        this.marker = new Feature({
            type: 'vehicle',
            geometry: undefined
        })
        this.marker.setProperties(['name', 'unit'])
        this.marker.set('name', name)
        this.marker.set('unit', this)
        this.vehicleClass = vClass
        this.state = {
            equipped: false,
            sending: false,
            receiving: false,
            parking: false,
            charging: false,
        };
    },

    getMarker() {
        return this.marker
    },

    setIsEquipped(isEquipped) {
        this.state.equipped = isEquipped
    },

    setLocation(latitude, longitude) {
        this.latitude = latitude
        this.longitude = longitude
    },

    setState(stateName) {
        if (this.state[stateName] !== undefined) {
            this.state[stateName] = true
            this.timeStateChange = Date.now();
        }
    },

    /**
     * Updates the marker of the vehicle.
     */
    updateView() {
        // Location
        if (this.latitude !== undefined && this.longitude !== undefined) {
            this.marker.setGeometry(new Point(fromLonLat([this.longitude, this.latitude])))
        }

        // Update style
        const style = this.createStyle()
        this.marker.setStyle(style)

        if ((Date.now() - this.timeStateChange) > 500) {
            // Clear sending/receiving states
            this.state.sending = false
            this.state.receiving = false
        }

    },

    /**
     * Creates the style of the vehicle marker based on the vehicles state.
     */
    createStyle() {
        let style;
        if (this.vehicleClass === 'PublicTransportVehicle') {
            style = 'bus'
        } else if (this.vehicleClass === 'Bicycle') {
            style = 'bicycle'
        } else if (this.vehicleClass === 'Taxi') {
            style = 'taxi'
        } else if (this.vehicleClass === 'HeavyGoodsVehicle' || this.vehicleClass === 'ExceptionalSizeVehicle' || this.vehicleClass === 'VehicleWithTrailer') {
            style = 'truck'
        } else if (this.vehicleClass === 'EmergencyVehicle') {
            style = 'car-emergency'
        } else if (this.vehicleClass === 'WorksVehicle') {
            style = 'car-works'
        } else {
            style = 'car'
        }
        if (this.state.sending) {
            style += '-sending'
        } else if (this.state.receiving) {
            style += '-receiving'
        } else if (this.state.charging) {
            style += '-charging'
        } else if (this.state.parking) {
            style += '-parking'
        } else if (this.state.equipped) {
            style += '-equipped'
        }
        return new Style({
            image: new Icon({
                anchor: [0.5, 1],
                src: `markers/${style}.png`
            })
        })
    }
}

/**
 * Represents a vehicle.
 */
const Agent = {
    name: 'unnamed_agent',
    latitude: 0,
    longitude: 0,
    marker: null,
    timeStateChange: 0,
    agentState: 'WAITING',

    init(name) {
        this.name = name
        this.marker = new Feature({
            type: 'agent',
            geometry: undefined
        })
        this.marker.setProperties(['name', 'unit'])
        this.marker.set('name', name)
        this.marker.set('unit', this)
        this.agentState = 'WAITING'
    },

    getMarker() {
        return this.marker
    },

    setLocation(latitude, longitude) {
        this.latitude = latitude
        this.longitude = longitude
    },

    setAgentState(stateName) {
        this.agentState = stateName;
    },

    /**
     * Updates the marker of the agent.
     */
    updateView() {
        // Location
        if (this.latitude !== undefined && this.longitude !== undefined) {
            this.marker.setGeometry(new Point(fromLonLat([this.longitude, this.latitude])))
        }

        // Update style
        const style = this.createStyle()
        this.marker.setStyle(style)
    },

    /**
     * Creates the style of the vehicle marker based on the vehicles state.
     */
    createStyle() {
        let style = 'agent-waiting'
        if (this.agentState === "WAITING") {
            style = 'agent-waiting'
        }
        if (this.agentState === "WALKING") {
            style = 'agent-walking'
        }
        if (this.agentState === "IN_SHARED_VEHICLE") {
            style = 'agent-in-shared-vehicle'
        }
        if (this.agentState === "IN_PRIVATE_VEHICLE") {
            style = 'agent-in-private-vehicle'
        }
        if (this.agentState === "IN_PT_VEHICLE") {
            style = 'agent-in-pt-vehicle'
        }
        if (this.agentState === "IN_PT_VEHICLE_AT_STOP") {
            style = 'agent-in-pt-vehicle-at-stop'
        }
        return new Style({
            image: new Icon({
                anchor: [0.5, 1],
                src: `markers/${style}.png`
            })
        })
    }
}

/**
 * Represents a traffic light.
 */
const TrafficLight = {
    name: 'unnamed_traffic_light',
    latitude: 0,
    longitude: 0,
    marker: null,
    timeStateChange: 0,
    state: {},

    init(name, latitude, longitude) {
        this.name = name
        this.latitude = latitude
        this.longitude = longitude
        this.marker = new Feature({
            type: 'trafficLight',
            geometry: new Point(fromLonLat([longitude, latitude]))
        })
        this.marker.setProperties(['name'])
        this.marker.set('name', name)
        this.state = {
            sending: false,
            receiving: false,
        };
    },

    getMarker() {
        return this.marker
    },

    setIsEquipped(isEquipped) {
        this.state.equipped = isEquipped
    },

    setLocation(latitude, longitude) {
        this.latitude = latitude
        this.longitude = longitude
    },

    setState(stateName) {
        if (this.state[stateName] !== undefined) {
            this.state[stateName] = true
            this.timeStateChange = Date.now();
        }
    },

    /**
     * Updates the marker of the RSU.
     */
    updateView() {
        // Location
        this.marker.setGeometry(new Point(fromLonLat([this.longitude, this.latitude])))

        // Update style
        const style = this.createStyle()
        this.marker.setStyle(style)

        if ((Date.now() - this.timeStateChange) > 500) {
            // Clear sending/receiving states
            this.state.sending = false
            this.state.receiving = false
        }
    },

    /**
     * Creates the style of the RSU marker based on the RSUs state.
     */
    createStyle() {
        let style = 'traffic-light'
        if (this.state.equipped) {
            style = 'traffic-light-equipped'
        }
        if (this.state.sending) {
            style = 'traffic-light-sending'
        }
        if (this.state.receiving) {
            style = 'traffic-light-receiving'
        }
        return new Style({
            image: new Icon({
                anchor: [0.5, 1],
                src: `markers/${style}.png`
            })
        })
    }
}

/**
 * Represents a charging station.
 */
const ChargingStation = {
    name: 'unnamed_charging_station',
    latitude: 0,
    longitude: 0,
    marker: null,
    timeStateChange: 0,
    state: {},

    init(name, latitude, longitude) {
        this.name = name
        this.latitude = latitude
        this.longitude = longitude
        this.marker = new Feature({
            type: 'charging-station',
            geometry: new Point(fromLonLat([longitude, latitude]))
        })
        this.marker.setProperties(['name'])
        this.marker.set('name', name)
        this.state = {
            sending: false,
            receiving: false,
        };
    },

    getMarker() {
        return this.marker
    },

    setIsEquipped(isEquipped) {
        this.state.equipped = isEquipped
    },

    setLocation(latitude, longitude) {
        this.latitude = latitude
        this.longitude = longitude
    },

    setState(stateName) {
        if (this.state[stateName] !== undefined) {
            this.state[stateName] = true
            this.timeStateChange = Date.now();
        }
    },

    /**
     * Updates the marker of the Charging Station.
     */
    updateView() {
        // Location
        this.marker.setGeometry(new Point(fromLonLat([this.longitude, this.latitude])))

        // Update style
        const style = this.createStyle()
        this.marker.setStyle(style)

        if ((Date.now() - this.timeStateChange) > 500) {
            // Clear sending/receiving states
            this.state.sending = false
            this.state.receiving = false
        }
    },

    /**
     * Creates the style of the RSU marker based on the Charging Station's state.
     */
    createStyle() {
        let style = 'charging-station'
        if (this.state.equipped) {
            style = 'charging-station-equipped'
        }
        if (this.state.sending) {
            style = 'charging-station-sending'
        }
        if (this.state.receiving) {
            style = 'charging-station-receiving'
        }
        return new Style({
            image: new Icon({
                anchor: [0.5, 1],
                src: `markers/${style}.png`
            })
        })
    }
}


/**
 * Controls the map and its unit markers.
 */
const map = (function () {

    var isCentered = new Boolean(false);
    /**
     * Stores all vehicles.
     * Map<vehicleName: string, vehicle: Vehicle>
     */
    let vehicles = {}

    /**
     * Stores all agents.
     * Map<agentName: string, agent: Agent>
     */
    let agents = {}

    /**
     * Stores all RSUs.
     * Map<rsuName: string, rsu: Rsu>
     */
    let rsus = {}

    /**
     * Stores all traffic lights (equipped)
     * Map<name: string, traffic-light: TrafficLight>
     */
    let trafficLights = {}

    /**
     * Stores all charging stations (equipped)
     * Map<name: string, charging-station: ChargingStation>
     */
    let chargingStations = {}

    /**
     * An additional layer beside the map itself.
     * This layer contains all unit markers.
     */
    const vectorLayer = new VectorLayer({
        source: new VectorSource({
            features: [],
            attributions: [
                '<a href="https://mapicons.mapsmarker.com/"><img src="markers/map-icons-collection.gif"/></a>'
            ]
        })
    })

    /**
     * The OpenLayers Map itself.
     */
    const ol_map = new Map({
        target: 'map',
        loadTilesWhileAnimating: true,
        layers: [
            new TileLayer({
                source: new OSM()
            }),
            vectorLayer
        ],
        view: new View({
            center: fromLonLat([
                window.centerLocation.longitude,
                window.centerLocation.latitude
            ]),
            zoom: window.zoomLevel
        })
    })


    /**
     * Adds a new marker to the map.
     * @param {Feature} marker The marker to be added.
     */
    function addMarker(marker) {
        vectorLayer.getSource().addFeature(marker)
    }

    /**
     * Creates a vehicle and adds its marker to the map.
     * @param {string} vehicleName The name of the vehicle
     * @param {string} vehicleClass the class of the vehicle
     * @param {boolean} equipped if the vehicle is equipped with an application
     */
    function addVehicle(vehicleName, vehicleClass, equipped) {
        if (!vehicles[vehicleName]) {
            vehicles[vehicleName] = Object.assign({}, Vehicle)
            vehicles[vehicleName].init(vehicleName, vehicleClass)
            vehicles[vehicleName].setIsEquipped(equipped)
            addMarker(vehicles[vehicleName].getMarker())
        }
    }

    /**
     * Creates an agents and adds its marker to the map.
     * @param {string} agentName The name of the agent
     * @param {number} latitude Latitude value of the start position of the agent
     * @param {number} longitude Longitude value of the start position of the agent
     */
    function addAgent(agentName, latitude, longitude) {
        if (!agents[agentName]) {
            agents[agentName] = Object.assign({}, Agent)
            agents[agentName].init(agentName)
            agents[agentName].setLocation(latitude, longitude)
            addMarker(agents[agentName].getMarker())
        }
    }

    /**
     * Creates an RSU and adds it to the map.
     * @param {string} rsuName Name of the RSU
     * @param {number} latitude Latitude value of the geo position
     * @param {number} longitude Longitude value of the geo position
     * @param {boolean} equipped true if vehicle is equipped with an application
     */
    function addRsu(rsuName, latitude, longitude, equipped) {
        if (!rsus[rsuName]) {
            rsus[rsuName] = Object.assign({}, Rsu)
            rsus[rsuName].init(rsuName, latitude, longitude)
            rsus[rsuName].setIsEquipped(equipped)
            addMarker(rsus[rsuName].getMarker())
        }
    }

    /**
     * Creates an traffic light and adds it to the map.
     * @param {string} name Name of the traffic light
     * @param {number} latitude Latitude value of the geo position
     * @param {number} longitude Longitude value of the geo position
     * @param {boolean} equipped true if vehicle is equipped with an application
     */
    function addTrafficLight(name, latitude, longitude, equipped) {
        if (!trafficLights[name]) {
            trafficLights[name] = Object.assign({}, TrafficLight)
            trafficLights[name].init(name, latitude, longitude)
            trafficLights[name].setIsEquipped(equipped)
            addMarker(trafficLights[name].getMarker())
        }
    }

    /**
     * Creates a charging station and adds it to the map.
     * @param {string} name Name of the charging station
     * @param {number} latitude Latitude value of the geo position
     * @param {number} longitude Longitude value of the geo position
     * @param {boolean} equipped true if charging station is equipped with an application
     */
    function addChargingStation(name, latitude, longitude, equipped) {
        if (!chargingStations[name]) {
            chargingStations[name] = Object.assign({}, ChargingStation)
            chargingStations[name].init(name, latitude, longitude)
            chargingStations[name].setIsEquipped(equipped)
            addMarker(chargingStations[name].getMarker())
        }
    }

    /**
     * Updates the state of a unit.
     * @param {string} unitName Name of the unit to update
     * @param {string} state Name of the state
     */
    function setUnitState(unitName, state) {
        if (vehicles[unitName]) {
            vehicles[unitName].setState(state)
        } else if (rsus[unitName]) {
            rsus[unitName].setState(state)
        } else if (trafficLights[unitName]) {
            trafficLights[unitName].setState(state);
        } else if (chargingStations[unitName]) {
            chargingStations[unitName].setState(state);
        }
    }

    /**
     * Updates the position of a vehicle.
     * If the vehicle doesn't exist yet, an error is logged.
     * @param {string} vehicleName Name of the vehicle
     * @param {number} latitude Latitude value of the geo position
     * @param {number} longitude Longitude value of the geo position
     * @param {boolean} is_parking Defines if the vehicle is parking
     */
    function setVehiclePosition(vehicleName, latitude, longitude, is_parking) {
        if (vehicles[vehicleName]) {
            vehicles[vehicleName].setLocation(latitude, longitude)
            // we don't use setState here, as this would influence the timeOfChange
            vehicles[vehicleName].state['parking'] = is_parking
        } else {
            console.error("Try to set location for non-existing vehicle", vehicleName)
        }
        if (isCentered == false) {
            ol_map.getView().setCenter(fromLonLat([longitude, latitude]));
            ol_map.getView().setZoom(18);
            isCentered = true;
        }
    }

    /**
     * Updates the position of an agent.
     * If the agent doesn't exist yet, an error is logged.
     * @param {string} agentName Name of the agent
     * @param {string} agentState Movement state of the agent, e.g., WAITING or WALKING
     * @param {number} latitude Latitude value of the geo position
     * @param {number} longitude Longitude value of the geo position
     */
    function setAgentPosition(agentName, agentState, latitude, longitude) {
        if (agents[agentName]) {
            agents[agentName].setLocation(latitude, longitude)
            agents[agentName].setAgentState(agentState)
        } else {
            console.error("Try to set location for non-existing agent", agentName)
        }
        if (isCentered == false) {
            ol_map.getView().setCenter(fromLonLat([longitude, latitude]));
            ol_map.getView().setZoom(18);
            isCentered = true;
        }
    }

    /**
     * Updates all unit markers on the map with the given names.
     * @param {string[]} unitNames Names of units to update.
     */
    function updateViews(unitNames) {
        for (const unitName of unitNames) {
            if (vehicles[unitName]) {
                vehicles[unitName].updateView()
            } else if (agents[unitName]) {
                agents[unitName].updateView()
            } else if (rsus[unitName]) {
                rsus[unitName].updateView()
            } else if (trafficLights[unitName]) {
                trafficLights[unitName].updateView()
            } else if (chargingStations[unitName]) {
                chargingStations[unitName].updateView()
            }
        }
    }

    /**
     * Removes a unit from the map.
     * @param {string} unitName Name of the unit to delete.
     */
    function removeUnit(unitName) {
        var marker
        if (vehicles[unitName]) {
            marker = vehicles[unitName].getMarker()
            vectorLayer.getSource().removeFeature(marker)
            delete vehicles[unitName]
        } else if (agents[unitName]) {
            marker = agents[unitName].getMarker()
            vectorLayer.getSource().removeFeature(marker)
            delete agents[unitName]
        } else if (rsus[unitName]) {
            marker = rsus[unitName].getMarker()
            vectorLayer.getSource().removeFeature(marker)
            delete rsus[unitName]
        } else if (trafficLights[unitName]) {
            marker = trafficLights[unitName].getMarker()
            vectorLayer.getSource().removeFeature(marker)
            delete trafficLights[unitName]
        } else if (chargingStations[unitName]) {
            marker = chargingStations[unitName].getMarker()
            vectorLayer.getSource().removeFeature(marker)
            delete chargingStations[unitName]
        }
    }

    /**
     * Deletes all markers from the map.
     */
    function removeAllUnits() {
        vehicles = {}
        agents = {}
        rsus = {}
        trafficLights = {}
        chargingStations = {}
        vectorLayer.getSource().clear()
    }

    // Public functions
    return {
        setUnitState,
        setVehiclePosition,
        setAgentPosition,
        addVehicle,
        addAgent,
        addRsu,
        addTrafficLight,
        addChargingStation,
        updateViews,
        removeUnit,
        removeAllUnits,
    }
})()

/**
 * Websocket
 */
const WebSocketClient = (function () {
    let websocketEstablishedConnection = false
    let webSocket
    let tries = 0
    const maxRetries = 30
    let simulated = false
    // eslint-disable-next-line no-undef
    const $status = $('#status')

    /**
     * Sets the current state.
     * @param {string} status Status name
     */
    function setStatus(status) {
        $status.removeClass()
        $status.addClass(status)
        $status.children('#tries').text(tries + "/" + maxRetries)
    }

    /**
     * Connection establishment to MOSAIC
     */
    function createWebSocket() {
        if (websocketEstablishedConnection) {
            return
        }
        if (simulated) {
            setStatus('closed')
            return
        }

        simulated = false
        tries++
        setStatus('connecting')

        webSocket = new WebSocket("ws://localhost:" + window.port)
        webSocket.onopen = socketOnOpen
        webSocket.onmessage = socketOnMessage
        webSocket.onclose = socketOnClose
        //webSocket.onerror = socketOnError

        if (tries <= maxRetries) {
            setTimeout(() => {
                if (!websocketEstablishedConnection) {
                    createWebSocket()
                }
            }, 3000)
        } else {
            setStatus('error')
            alert("ERROR: Stopped trying to connect to MOSAIC due to timeout.")
        }
    }

    /**
     * Called as soon as an error occurred.
     * @param  {...any} args All parameters delivered by the error call.
     */
    function socketOnError(...args) {
        if (websocketEstablishedConnection) {
            // eslint-disable-next-line no-console
            console.error('WebSocket Error:', ...args)
            websocketEstablishedConnection = false
            setStatus('error')
            alert("ERROR: An WebSocket error occured.\n(more info in console output)")
        }
    }

    /**
     * Called as soon as the connection has been established.
     */
    function socketOnOpen() {
        setStatus('connected')
        websocketEstablishedConnection = true
        setInterval(function () {
            if (websocketEstablishedConnection) {
                webSocket.send("pull")
            }
        }, window.updateInterval)
    }

    /**
     * Called after receiving a new message.
     * @param evt Event containing received data.
     */
    function socketOnMessage(evt) {
        websocketEstablishedConnection = true
        simulated = true
        const data = JSON.parse(evt.data)
        if (data != null) {
            const updatedUnits = []
            var unitName

            if (data.VehicleUpdates) {
                if (data.VehicleUpdates.updated) {
                    // Update vehicle locations
                    data.VehicleUpdates.updated.forEach(vehicle => {
                        let is_parking = vehicle.vehicleStopMode === 'PARK_ON_ROADSIDE'
                        map.setVehiclePosition(vehicle.name, vehicle.position.latitude, vehicle.position.longitude, is_parking)
                        updatedUnits.push(vehicle.name)
                    })
                }
            } else if (data.AgentUpdates) {
                if (data.AgentUpdates.updated) {
                    // Update agent locations
                    data.AgentUpdates.updated.forEach(agent => {
                        map.setAgentPosition(agent.name, agent.state, agent.position.latitude, agent.position.longitude)
                        updatedUnits.push(agent.name)
                    })
                }
            } else if (data.UnitsRemove) {
                data.UnitsRemove.forEach(map.removeUnit)
            } else if (data.VehicleRegistration) {
                console.log(JSON.stringify(data.VehicleRegistration))
                // determine if vehicle is equipped with an application
                let equipped = data.VehicleRegistration.vehicleMapping.applications.length > 0;
                let vClass = data.VehicleRegistration.vehicleMapping.vehicleType.vehicleClass;
                map.addVehicle(data.VehicleRegistration.vehicleMapping.name, vClass, equipped)
            } else if (data.AgentRegistration) {
                console.log(JSON.stringify(data.AgentRegistration))
                var agentOrigin = data.AgentRegistration.origin
                map.addAgent(data.AgentRegistration.agentMapping.name, agentOrigin.latitude, agentOrigin.longitude)
            } else if (data.V2xMessageTransmission) {
                // Mark vehicles that are sending right now
                unitName = data.V2xMessageTransmission.sourceName
                map.setUnitState(unitName, 'sending')
                updatedUnits.push(unitName)
            } else if (data.V2xMessageReception) {
                // Mark vehicles that are receiving right now
                unitName = data.V2xMessageReception.receiverName
                map.setUnitState(unitName, 'receiving')
                updatedUnits.push(unitName)
            } else if (data.RsuRegistration) {
                // Add RSU to map
                unitName = data.RsuRegistration.rsuMapping.name
                var position = data.RsuRegistration.rsuMapping.position
                let equipped = data.RsuRegistration.rsuMapping.applications.length > 0;
                map.addRsu(unitName, position.latitude, position.longitude, equipped)
                updatedUnits.push(unitName)
            } else if (data.TrafficLightRegistration) {
                // Add traffic light to map
                unitName = data.TrafficLightRegistration.trafficLightMapping.name
                let position = data.TrafficLightRegistration.trafficLightMapping.position
                let equipped = data.TrafficLightRegistration.trafficLightMapping.applications.length > 0;
                if (equipped) {
                    map.addTrafficLight(unitName, position.latitude, position.longitude, equipped)
                    updatedUnits.push(unitName)
                }
            } else if (data.ChargingStationRegistration) {
                // Add traffic light to map
                unitName = data.ChargingStationRegistration.chargingStationMapping.name
                let position = data.ChargingStationRegistration.chargingStationMapping.position
                let equipped = data.ChargingStationRegistration.chargingStationMapping.applications.length > 0;
                map.addChargingStation(unitName, position.latitude, position.longitude, equipped)
                updatedUnits.push(unitName)
            }

            // Update markers of updated units
            map.updateViews(updatedUnits)
        }
    }

    /**
     * Called as soon as the socket was closed.
     */
    function socketOnClose() {
        if (websocketEstablishedConnection) {
            websocketEstablishedConnection = false
            map.removeAllUnits()
            setStatus('closed')
        }
    }

    /**
     * Initializes the WebSocket.
     */
    function initialize() {
        if (!("WebSocket" in window)) {
            var error = "Sorry, your Browser does not support WebSocket"
            alert(error)
            throw new Error(error)
        } else {
            $status.children('button#reconnect').on('click', () => {
                simulated = false
                websocketEstablishedConnection = false
                tries = 0
                createWebSocket()
            })
            createWebSocket()
        }
    }

    return {
        initialize
    }
})()

// eslint-disable-next-line no-undef
$(function () {
    // At this point all necessary data of the whole page has been loaded.
    // Now the WebSocket can be initialized.
    WebSocketClient.initialize()
})
