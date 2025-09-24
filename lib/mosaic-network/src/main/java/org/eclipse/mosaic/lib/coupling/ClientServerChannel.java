/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.lib.coupling;

import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.CommandMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.CommandMessage.CommandType;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.InitMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.PortExchange;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.TimeMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.AddNode;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.UpdateNode;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.RemoveNode;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.UpdateNode.NodeData;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.ConfigureWifiRadio;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.SendWifiMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.ReceiveWifiMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.ConfigureCellRadio;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.SendCellMessage;
import org.eclipse.mosaic.lib.coupling.ClientServerChannelProtos.ReceiveCellMessage;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.CartesianCircle;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.objects.addressing.DestinationAddressContainer;
import org.eclipse.mosaic.lib.objects.communication.AdHocConfiguration;
import org.eclipse.mosaic.lib.objects.communication.CellConfiguration;
import org.eclipse.mosaic.lib.objects.communication.InterfaceConfiguration;
import org.eclipse.mosaic.lib.objects.v2x.V2xReceiverInformation;
import org.eclipse.mosaic.lib.util.objects.IdTransformer;

import com.google.protobuf.CodedInputStream;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Abstraction of Ambassador->Federate Byte Protocol
 * for coupling of a network federate to MOSAIC.
 */
public class ClientServerChannel {

    final static int PROTOCOL_VERSION = 2;

    /**
     * Socket connected to the network federate.
     */
    public Socket socket;

    /**
     * Input stream from network federate.
     */
    private final CodedInputStream cin;

    /**
     * Output stream to network federate.
     */
    final private OutputStream out;

    /**
     * Constructor.
     *
     * @param host the remote host address as an InetAddress
     * @param port the remote port number
     * @param log  logger to log on
     * @throws IOException if the streams cannot be opened.
     */
    public ClientServerChannel(InetAddress host, int port, Logger log) throws IOException {
        this.socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        // Buffer the socket stream to reduce tiny reads and allocations
        BufferedInputStream bin = new BufferedInputStream(socket.getInputStream(), 64 * 1024);
        this.cin = CodedInputStream.newInstance(bin);
        this.cin.setSizeLimit(1 * 1024 * 1024); // guardrail: 1MB per message, adjust as needed
        this.out = new DataOutputStream(socket.getOutputStream());
        // TODO: use logger
    }

    /**
     * Closes the channel.
     */
    public void close() throws IOException {
        this.socket.close();
    }

    //####################################################################
    //                  Reading methods
    //####################################################################

    /*
     * Helper function to only use one single CodedInputStream (instead of building a CodedInputStream for every new read*Message call)
     */
    private <T> T parseDelimited(com.google.protobuf.Parser<T> parser) throws IOException {
        int size = cin.readRawVarint32();
        int oldLimit = cin.pushLimit(size);
        try {
            return parser.parseFrom(cin);
        } finally {
            cin.popLimit(oldLimit);
            cin.resetSizeCounter(); // avoids cumulative sizeLimit hits
        }
    }

    /**
     * Reads a wifi message from the incoming channel.
     * TODO: ChannelID (and length) not yet treated
     *
     * @return The read message.
     */
    public ReceiveWifiMessageRecord readReceiveWifiMessage(IdTransformer<Integer, String> idTransformer) throws IOException {
        ReceiveWifiMessage m = parseDelimited(ReceiveWifiMessage.parser());
        V2xReceiverInformation recInfo = new V2xReceiverInformation(m.getTime()).signalStrength(m.getRssi());
        return new ReceiveWifiMessageRecord(
                m.getTime(),
                idTransformer.fromExternalId(m.getNodeId()),
                m.getMessageId(),
                recInfo
        );
    }


    /**
     * Reads a cell message from the incoming channel.
     *
     * @return The read message.
     */
    public ReceiveCellMessageRecord readReceiveCellMessage(IdTransformer<Integer, String> idTransformer) throws IOException {
        ReceiveCellMessage m = parseDelimited(ReceiveCellMessage.parser());
        return new ReceiveCellMessageRecord(
                m.getTime(),
                idTransformer.fromExternalId(m.getNodeId()),
                m.getMessageId()
        );
    }

    /**
     * Reads a port from the incoming stream.
     *
     * @return the read port as int
     */
    public int readPortBody() throws IOException {
        PortExchange m = parseDelimited(PortExchange.parser());
        return m.getPortNumber();
    }

    /**
     * Reads a time from the incoming stream.
     *
     * @return the read time as long
     */
    public long readTimeBody() throws IOException {
        TimeMessage m = parseDelimited(TimeMessage.parser());
        return m.getTime();
    }

    /**
     * Reads a single command from the input stream blocking.
     *
     * @return the read command
     */
    public CommandType readCommand() throws IOException {
        CommandMessage m = parseDelimited(CommandMessage.parser());
        return m.getCommandType();
    }

    //####################################################################
    //               Writing methods
    //####################################################################

    /**
     * Initialize scheduler with given times.
     *
     * @param startTime the first point in time simulated by the simulator
     * @param endTime   the last timestep simulated by the simulator
     * @return command returned by the federate
     */
    public CommandType writeInitBody(long startTime, long endTime) throws IOException {
        writeCommand(CommandType.INIT);
        InitMessage.Builder initMessage = InitMessage.newBuilder();
        initMessage.setSimulationStartTime(startTime);
        initMessage.setSimulationEndTime(endTime);
        initMessage.setProtocolVersion(PROTOCOL_VERSION);
        initMessage.build().writeDelimitedTo(out);
        return readCommand();
    }

    /**
     * Command: Add node.
     *
     * @param time time at which the node is added
     * @param node id and position
     * @return command returned by the federate
     */
    public CommandType writeAddNodeMessage(long time, AddNode.NodeType type, NodeDataContainer node) throws IOException {
        writeCommand(CommandType.ADD_NODE);
        AddNode.Builder msg = AddNode.newBuilder();
        msg.setType(type);
        msg.setTime(time);
        msg.setNodeId(node.id);
        msg.setX(node.pos.getX());
        msg.setY(node.pos.getY());
        msg.setZ(node.pos.getZ());
        msg.build().writeDelimitedTo(out);
        return readCommand();
    }

    /**
     * Command: Update nodes.
     *
     * @param time  time at which the positions are updated
     * @param nodes a list of ids and positions
     * @return command returned by the federate
     */
    public CommandType writeUpdatePositionsMessage(long time, List<NodeDataContainer> nodes) throws IOException {
        writeCommand(CommandType.UPDATE_NODE);
        UpdateNode.Builder msg = UpdateNode.newBuilder();
        msg.setTime(time);
        for (NodeDataContainer node : nodes) {
            NodeData.Builder tmpBuilder = NodeData.newBuilder();
            tmpBuilder.setId(node.id);
            tmpBuilder.setX(node.pos.getX());
            tmpBuilder.setY(node.pos.getY());
            tmpBuilder.setZ(node.pos.getZ());
            msg.addProperties(tmpBuilder.build());
        }
        msg.build().writeDelimitedTo(out);
        return readCommand();
    }

    /**
     * Command: Remove nodes.
     *
     * @param time time at which the nodes are removed
     * @param id   ID to remove
     * @return command returned by the federate
     */
    public CommandType writeRemoveNodeMessage(long time, Integer id) throws IOException {
        writeCommand(CommandType.REMOVE_NODE);
        RemoveNode.Builder msg = RemoveNode.newBuilder();
        msg.setTime(time);
        msg.setNodeId(id);
        msg.build().writeDelimitedTo(out);
        return readCommand();
    }

    /**
     * Write send wifi message header to channel.
     * Not used in eWorld visualizer.
     *
     * @param time      simulation time at which the message is sent
     * @param srcNodeId ID of the sending node      //TODO: maybe make this an IP? -> nodes would have a bilinear mapping to IP addresses
     * @param msgId     the ID of the message
     * @param msgLength length of the message
     * @param dac       DestinationAddressContainer with the destination address of the sender and additional information
     * @return command returned by the federate
     */
    public CommandType writeSendWifiMessage(long time, int srcNodeId,
                                            int msgId, long msgLength, DestinationAddressContainer dac) throws IOException {
        writeCommand(CommandType.SEND_WIFI_MSG);

        //Add message details to the builder
        SendWifiMessage.Builder sendMess = SendWifiMessage.newBuilder()
                .setTime(time)
                .setNodeId(srcNodeId)
                .setChannelId(translateChannel(dac.getAdhocChannelId()))
                .setMessageId(msgId)
                .setLength(msgLength);

        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(dac.getAddress().getIPv4Address().getAddress()); //make an int32 out of the byte array
        buffer.position(0);

        if (dac.getRoutingType().isGeocast()) {
            if (dac.getGeoArea() instanceof GeoRectangle geoRectangle) {   //Rectangular area
                SendWifiMessage.GeoRectangleAddress.Builder rectangleAddress = SendWifiMessage.GeoRectangleAddress.newBuilder();
                //builder for rectangular addresses
                rectangleAddress.setIpAddress(buffer.getInt()); //write the ip address as flat integer into the builder
                //convert coordinates etc.
                CartesianRectangle projectedRectangle = geoRectangle.toCartesian();
                //write the coordinates of the area into the builder
                rectangleAddress.setAX(projectedRectangle.getA().getX());
                rectangleAddress.setAY(projectedRectangle.getA().getY());
                rectangleAddress.setBX(projectedRectangle.getB().getX());
                rectangleAddress.setBY(projectedRectangle.getB().getY());
                //add address to the message
                sendMess.setRectangleAddress(rectangleAddress);
            } else if (dac.getGeoArea() instanceof GeoCircle geoCircle) {
                SendWifiMessage.GeoCircleAddress.Builder circleAddress = SendWifiMessage.GeoCircleAddress.newBuilder();
                circleAddress.setIpAddress(buffer.getInt());

                CartesianCircle projectedCircle = geoCircle.toCartesian();
                //write area into the address object
                circleAddress.setCenterX(projectedCircle.getCenter().getX());
                circleAddress.setCenterY(projectedCircle.getCenter().getY());
                circleAddress.setRadius(projectedCircle.getRadius());
                //add address to the message
                sendMess.setCircleAddress(circleAddress);
            } else {
                throw new IllegalArgumentException("Addressing does support GeoCircle and GeoRectangle only.");
            }
        } else if (dac.getRoutingType().isTopocast()) {
            SendWifiMessage.TopologicalAddress.Builder adr = SendWifiMessage.TopologicalAddress.newBuilder();
            adr.setIpAddress(buffer.getInt());
            if (!(dac.getTimeToLive() > -1)) {
                throw new IllegalArgumentException("Require TimeToLive for topocast ad-hoc communciation.");
            }
            adr.setTtl(dac.getTimeToLive());
            sendMess.setTopologicalAddress(adr);
        }
        sendMess.build().writeDelimitedTo(out); //write message onto channel
        return readCommand();
    }

    /**
     * Takes a configuration message and inserts it via the wrapped protobuf channel
     * Configuration is then sent to the federate.
     *
     * @param time          the logical time at which the configuration happens
     * @param msgID         the ID of the configuration message
     * @param nodeId        the external (federate-internal) ID of the node
     * @param configuration the actual configuration
     * @return command returned by the federate
     */
    public CommandType writeConfigureWifiRadio(long time, int msgID, int nodeId, AdHocConfiguration configuration) throws IOException {
        writeCommand(CommandType.CONF_WIFI_RADIO);
        ConfigureWifiRadio.Builder configRadio = ConfigureWifiRadio.newBuilder();
        configRadio.setTime(time);
        configRadio.setMessageId(msgID);
        configRadio.setNodeId(nodeId);
        configRadio.setRadioNumber(switch (configuration.getRadioMode()) {
            case OFF -> ConfigureWifiRadio.RadioNumber.NO_RADIO;
            case SINGLE -> ConfigureWifiRadio.RadioNumber.SINGLE_RADIO;
            case DUAL -> ConfigureWifiRadio.RadioNumber.DUAL_RADIO;
            default -> throw new RuntimeException("Illegal number of radios in configuration: " + configuration.getRadioMode().toString());
        });
        if (configuration.getRadioMode() == AdHocConfiguration.RadioMode.SINGLE
                || configuration.getRadioMode() == AdHocConfiguration.RadioMode.DUAL) {
            ConfigureWifiRadio.RadioConfiguration.Builder radioConfig1 = ConfigureWifiRadio.RadioConfiguration.newBuilder();
            radioConfig1.setReceivingMessages(false);                                     //!!Semantic in Java: true -> only routing
            radioConfig1.setIpAddress(inet4ToInt(configuration.getConf0().getIp()));   //Semantic in federates: false -> only routing
            radioConfig1.setSubnetAddress(inet4ToInt(configuration.getConf0().getSubnet()));
            radioConfig1.setTransmissionPower(configuration.getConf0().getPower());
            radioConfig1.setPrimaryRadioChannel(translateChannel(configuration.getConf0().getChannel0()));
            if (configuration.getConf0().getMode() == InterfaceConfiguration.MultiChannelMode.ALTERNATING) {
                radioConfig1.setSecondaryRadioChannel(translateChannel(configuration.getConf0().getChannel1()));
                radioConfig1.setRadioMode(ConfigureWifiRadio.RadioConfiguration.RadioMode.DUAL_CHANNEL);
            } else {
                radioConfig1.setRadioMode(ConfigureWifiRadio.RadioConfiguration.RadioMode.SINGLE_CHANNEL);
            }
            configRadio.setPrimaryRadioConfiguration(radioConfig1);
        }
        if (configuration.getRadioMode() == AdHocConfiguration.RadioMode.DUAL) {
            ConfigureWifiRadio.RadioConfiguration.Builder radioConfig2 = ConfigureWifiRadio.RadioConfiguration.newBuilder();
            radioConfig2.setReceivingMessages(false); //!!Semantic in Java: true -> only routing
            radioConfig2.setIpAddress(inet4ToInt(configuration.getConf1().getIp()));   //Semantic in federates: false -> only routing
            radioConfig2.setSubnetAddress(inet4ToInt(configuration.getConf1().getSubnet()));
            radioConfig2.setTransmissionPower(configuration.getConf1().getPower());
            radioConfig2.setPrimaryRadioChannel(translateChannel(configuration.getConf1().getChannel0()));
            if (configuration.getConf1().getMode() == InterfaceConfiguration.MultiChannelMode.ALTERNATING) {
                radioConfig2.setSecondaryRadioChannel(translateChannel(configuration.getConf1().getChannel1()));
                radioConfig2.setRadioMode(ConfigureWifiRadio.RadioConfiguration.RadioMode.DUAL_CHANNEL);
            } else {
                radioConfig2.setRadioMode(ConfigureWifiRadio.RadioConfiguration.RadioMode.SINGLE_CHANNEL);
            }
            configRadio.setSecondaryRadioConfiguration(radioConfig2);
        }
        configRadio.build().writeDelimitedTo(out);
        return readCommand();
    }


    /**
     * Takes a configuration message and inserts it via the wrapped protobuf channel
     * Configuration is then sent to the federate.
     *
     * @param time          the logical time at which the configuration happens
     * @param nodeId        the external (federate-internal) ID of the node
     * @param configuration the actual configuration
     * @return command returned by the federate
     */
    public CommandType writeConfigureCellRadio(long time, int nodeId, CellConfiguration configuration, Inet4Address ip) throws IOException {
        // CellConfiguration unused
        writeCommand(CommandType.CONF_CELL_RADIO);
        ConfigureCellRadio.Builder message = ConfigureCellRadio.newBuilder();
        message.setTime(time);
        message.setNodeId(nodeId);
        message.setIpAddress(inet4ToInt(ip));
        message.setSubnetAddress(0);
        message.build().writeDelimitedTo(out);
        return readCommand();
    }


    /**
     * Write send cell message header to channel.
     *
     * @param time      simulation time at which the message is sent
     * @param srcNodeId ID of the sending node
     * @param msgId     the ID of the message
     * @param msgLength length of the message
     * @param dac       DestinationAddressContainer with the destination address of the sender and additional information
     * @return command returned by the federate
     */
    public CommandType writeSendCellMessage(long time, int srcNodeId,
                                            int msgId, long msgLength, DestinationAddressContainer dac) throws IOException {
        writeCommand(CommandType.SEND_CELL_MSG);

        SendCellMessage.Builder msg = SendCellMessage.newBuilder();
        msg.setTime(time);
        msg.setNodeId(srcNodeId);
        msg.setMessageId(msgId);
        msg.setLength(msgLength);;

        if (!dac.getRoutingType().isTopocast()) {
            throw new IllegalArgumentException("Only topocast is supported");
        }
        SendCellMessage.TopologicalAddress.Builder adr = SendCellMessage.TopologicalAddress.newBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(dac.getAddress().getIPv4Address().getAddress());
        buffer.position(0);
        adr.setIpAddress(buffer.getInt());
        msg.setTopologicalAddress(adr);
        msg.build().writeDelimitedTo(out);
        return readCommand();
    }

    /**
     * Command: advance time.
     *
     * @param time point in time up to which advance is granted
     */
    public void writeAdvanceTimeMessage(long time) throws IOException {
        writeCommand(CommandType.ADVANCE_TIME);
        TimeMessage.Builder timeMessage = TimeMessage.newBuilder();
        timeMessage.setTime(time);
        timeMessage.build().writeDelimitedTo(out);
    }

    /**
     * Write a command.
     *
     * @param cmd the command to write onto the channel
     * @throws IOException Communication error.
     */
    public void writeCommand(CommandType protobufCmd) throws IOException {
        if (protobufCmd == CommandType.UNDEF) {
            return;
        }
        CommandMessage.Builder commandMessage = CommandMessage.newBuilder();
        commandMessage.setCommandType(protobufCmd);
        commandMessage.build().writeDelimitedTo(out);
    }

    //####################################################################
    //   Helper methods and classes
    //####################################################################

    /**
     * Converts a Inet4Address to an int.
     * TODO: make sure ints are handled bitwise when handing to protobuf
     *
     * @param ip The Inet4Address
     * @return an int representing the IP address
     */
    private int inet4ToInt(Inet4Address ip) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(ip.getAddress());
        buffer.position(0);
        return buffer.getInt();
    }

    /**
     * Returns the corresponding {@link AdHocChannel} type to a given Protobuf channel enum type.
     *
     * @param channel the internal channel object
     * @return the protobuf-channel object
     */
    private ClientServerChannelProtos.RadioChannel translateChannel(AdHocChannel channel) {
        return switch (channel) {
            case SCH1 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH1;
            case SCH2 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH2;
            case SCH3 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH3;
            case CCH -> ClientServerChannelProtos.RadioChannel.PROTO_CCH;
            case SCH4 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH4;
            case SCH5 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH5;
            case SCH6 -> ClientServerChannelProtos.RadioChannel.PROTO_SCH6;
        };
    }

    record NodeDataContainer(int id, CartesianPoint pos) {}

    record ReceiveWifiMessageRecord(long time, String receiverName, int msgId, V2xReceiverInformation receiverInformation) {}

    record ReceiveCellMessageRecord(long time, String receiverName, int msgId) {}

}
