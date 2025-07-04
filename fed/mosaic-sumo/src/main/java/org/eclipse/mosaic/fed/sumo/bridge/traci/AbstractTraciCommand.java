/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.sumo.bridge.traci;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.SumoVar;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.TraciDatatypes;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.AbstractTraciResultReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.ByteTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.CommandLengthReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.DoubleTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.IntegerTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.StatusReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.StringTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.reader.VehicleIdTraciReader;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.AbstractTraciParameterWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.ByteTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.DoubleTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.IntegerTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.ListTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.PersonIdTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.StringTraciWriter;
import org.eclipse.mosaic.fed.sumo.bridge.traci.writer.VehicleIdTraciWriter;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * This class handles the whole construction of a traci command message and reads its responds. To
 * achieve this, each subclass needs to define the command, the variable and all parameters which are
 * required for the specific command.
 */
public abstract class AbstractTraciCommand<T> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final CommandLengthReader COMMAND_LENGTH_READER = new CommandLengthReader();
    private static final StatusReader STATUS_READER = new StatusReader();

    /**
     * A list of writers which are used to construct the message.
     */
    private final List<AbstractTraciParameterWriter<?>> writers = new ArrayList<>();

    /**
     * A list of readers which are used to read the results from the response.
     */
    private final List<AbstractTraciResultReader<?>> readers = new ArrayList<>();

    /**
     * The API supportedVersion this command supports at least.
     */
    private final VersionSupport support;

    protected AbstractTraciCommand(@Nullable SumoVersion since) {
        this.support = (current) -> (since == null || current.getCurrentVersion().isGreaterOrEqualThan(since)
        );
    }

    protected AbstractTraciCommand(@Nullable TraciVersion since) {
        this.support = (current) -> (since == null || current.getCurrentVersion().getApiVersion() >= since.getApiVersion());
    }

    /**
     * Call this method in the constructor to define all writers for this command.
     */
    protected TraciCommandWriterBuilder write() {
        return new TraciCommandWriterBuilder(this);
    }

    /**
     * Call this method in the constructor to define all readers for this command.
     */
    protected TraciCommandResultReaderBuilder read() {
        return new TraciCommandResultReaderBuilder(this);
    }

    /**
     * Call this method to execute the command with the given arguments. The order of arguments must match
     * the order of parameter writers which have been defined in the constructor. No result is returned.
     *
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    protected void execute(Bridge bridge, Object... arguments) throws CommandException, InternalFederateException {
        if (checkVersion(bridge)) {
            sendMessageToTraci(bridge, arguments);
            readResults(bridge, false);
        }
    }

    /**
     * Call this method to execute the command with the given arguments. The order of arguments must match
     * the order of parameter writers which have been defined in the constructor. The response is expected to
     * return a list of results. For each of those results, all readers are called and the resulting objects are passed
     * to {@link #constructResult(Status, Object...)}. All objects constructed by this method are added to the list which
     * eventually will be returned by this method.
     *
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    protected List<T> executeAndReturnList(Bridge bridge, Object... arguments) throws CommandException, InternalFederateException {
        if (checkVersion(bridge)) {
            sendMessageToTraci(bridge, arguments);
            return readResults(bridge, true);
        } else {
            return Lists.newArrayList();
        }
    }

    /**
     * Call this method to execute the command with the given arguments. The order of arguments must match
     * the order of parameter writers which have been defined in the constructor. For the response all configured
     * readers are called and the resulting objects are passed to {@link #constructResult(Status, Object...)}. The
     * object constructed by this method will be returned by this method.
     *
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The TraCI connection is shut down.
     */
    protected Optional<T> executeAndReturn(Bridge bridge, Object... arguments) throws CommandException, InternalFederateException {
        if (checkVersion(bridge)) {
            sendMessageToTraci(bridge, arguments);
            return Optional.ofNullable(Iterables.getFirst(readResults(bridge, false), null));
        } else {
            return Optional.empty();
        }
    }

    private boolean checkVersion(Bridge bridge) {
        if (!support.isSupported(bridge)) {
            log.warn("The command {} will be skipped since it is not available with the current supportedVersion of SUMO (is: {})",
                    getClass().getSimpleName(),
                    bridge.getCurrentVersion());
            return false;
        }
        return true;
    }

    /**
     * Writes the provided arguments to the {@link java.io.DataOutputStream} of the {@link Bridge}
     * in the way it has been defined by the constructor of the extending command implementation.
     * All configured {@link AbstractTraciParameterWriter}s are sequentially called to write
     * their actual content as bytes to the {@link java.io.DataOutputStream}. If a writer
     * is variable, the next argument from the list of given arguments is used accordingly.
     * This implies, that the length of the passed arguments array must match the length of the writers which
     * are supposed to write variable content.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sendMessageToTraci(Bridge bridge, Object[] arguments) throws InternalFederateException {
        try {

            int messageLength = 0;
            int i = 0;
            for (AbstractTraciParameterWriter block : writers) {
                if (block.isVariable()) {
                    Validate.isTrue(i < arguments.length,
                            "Too many arguments given. Please revise writer configuration in the constructor."
                    );
                    messageLength += block.getVariableLength(arguments[i++]);
                } else {
                    messageLength += block.getLength();
                }
            }

            Validate.isTrue(i == arguments.length,
                    "Too few arguments given. Please revise writer configuration in the constructor."
            );

            if (messageLength < 255) {
                bridge.getOut().writeInt(4 + 1 + messageLength);
                bridge.getOut().writeByte(1 + messageLength);
            } else {
                // 255 is the maximum possible command length. Other cases require to set the
                // command length to zero and use an integer field instead.
                bridge.getOut().writeInt(4 + 1 + 4 + messageLength);
                bridge.getOut().writeByte(0);
                bridge.getOut().writeInt(1 + 4 + messageLength);
            }

            i = 0;
            for (AbstractTraciParameterWriter block : writers) {
                if (block.isVariable()) {
                    block.writeVariableArgument(bridge.getOut(), arguments[i++]);
                } else {
                    block.write(bridge.getOut());
                }
            }
        } catch (Exception t) {
            bridge.emergencyExit(t);
            throw new InternalFederateException("Error during executing TraCI command " + this.getClass().getSimpleName(), t);
        }
    }

    /**
     * Reads the result message from the previous called TraCI command. After reading
     * header information such as length of the message and the result status,
     * all configured {@link AbstractTraciResultReader} objects are called sequentially
     * to read from the {@link java.io.DataInputStream} of the {@link Bridge}.
     * If there are bytes left which have not been read by the readers at the end of the message,
     * those are pulled from the input stream and ignored. At the end, the {@link #constructResult(Status, Object...)}
     * method is called which builds the actual result object based on the various objects read
     * by all readers.
     *
     * @param listMode with this mode enabled, the readers are iterated several times as the result message requires.
     * @return a list of results. If listMode is disabled, this list contains only ONE item.
     */
    @SuppressWarnings(value = "RR_NOT_CHECKED", justification = "It's fine to ignore some of the bytes when reading the stream.")
    private List<T> readResults(Bridge bridge, boolean listMode) throws CommandException, InternalFederateException {
        try {
            int messageBytesLeft = bridge.getIn().readInt() - 4;

            COMMAND_LENGTH_READER.read(bridge.getIn(), messageBytesLeft);
            messageBytesLeft -= COMMAND_LENGTH_READER.getNumberOfBytesRead();

            // requested command variable
            bridge.getIn().readUnsignedByte();
            messageBytesLeft -= 1;

            Status status = STATUS_READER.read(bridge.getIn(), messageBytesLeft);
            messageBytesLeft -= STATUS_READER.getNumberOfBytesRead();

            final List<T> results = new ArrayList<>();
            if (status.getResultType() == Status.STATUS_OK) {

                int iterations = 1;
                if (listMode) {
                    iterations = bridge.getIn().readInt();
                    messageBytesLeft -= 4;
                }

                while (iterations > 0 && messageBytesLeft > 0) {
                    iterations--;

                    int commandLength = COMMAND_LENGTH_READER.read(bridge.getIn(), messageBytesLeft);

                    messageBytesLeft -= commandLength;

                    int actualBytesRead = COMMAND_LENGTH_READER.getNumberOfBytesRead();

                    ArrayList<Object> resultObjects = new ArrayList<>();
                    for (AbstractTraciResultReader<?> reader : readers) {
                        Object o = reader.read(bridge.getIn(), commandLength - actualBytesRead);
                        actualBytesRead += reader.getNumberOfBytesRead();
                        if (o != null) {
                            resultObjects.add(o);
                        }
                    }
                    if (resultObjects.size() > 0) {
                        results.add(constructResult(status, resultObjects.toArray(new Object[0])));
                    }

                    if (actualBytesRead < commandLength) {
                        //discard any unused bytes
                        //noinspection ResultOfMethodCallIgnored, reasoning: just flush buffer
                        bridge.getIn().read(new byte[commandLength - actualBytesRead]);
                    }
                }

            } else {
                throw new CommandException(String.format("TraCI Command failed: %s", status.getDescription()), status);
            }
            return results;
        } catch (CommandException e) {
            throw e;
        } catch (Exception t) {
            bridge.emergencyExit(t);
            String className = this.getClass().getSimpleName();
            throw new InternalFederateException("Error during reading response from TraCI command " + className + ".", t);
        } finally {
            bridge.onCommandCompleted();
        }
    }

    /**
     * This method is called during reading the command response when all readers have been executed.
     * The results of the readers are passed to this method.
     *
     * @param status  the status of the response
     * @param objects the objects created by the configured readers
     * @return the final result constructed from the passed objects
     */
    protected abstract T constructResult(Status status, Object... objects);

    protected final static class TraciCommandWriterBuilder {

        private final AbstractTraciCommand<?> command;

        private TraciCommandWriterBuilder(AbstractTraciCommand<?> command) {
            this.command = command;
        }

        /**
         * Defines the command identifier (usually the first writer).
         */
        public final TraciCommandWriterBuilder command(int commandIdentifier) {
            command.writers.add(new ByteTraciWriter(commandIdentifier));
            return this;
        }

        /**
         * Defines the variable identifier (usually the second writer).
         */
        public final TraciCommandWriterBuilder variable(int variableIdentifier) {
            command.writers.add(new ByteTraciWriter(variableIdentifier));
            return this;
        }

        /**
         * Defines the variable identifier (usually the second writer).
         */
        public final TraciCommandWriterBuilder variable(SumoVar variableIdentifier) {
            command.writers.add(new ByteTraciWriter(variableIdentifier.var));
            return this;
        }

        /**
         * Defines a specific Byte value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeByte(int byteValue) {
            command.writers.add(new ByteTraciWriter(byteValue));
            return this;
        }

        /**
         * Defines a specific Integer value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeInt(int intValue) {
            command.writers.add(new IntegerTraciWriter(intValue));
            return this;
        }

        /**
         * Defines a specific Integer value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeIntWithType(int intValue) {
            writeByte(TraciDatatypes.INTEGER);
            return writeInt(intValue);
        }

        /**
         * Defines a specific Double value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeDouble(double doubleValue) {
            command.writers.add(new DoubleTraciWriter(doubleValue));
            return this;
        }

        /**
         * Defines a specific Double value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeDoubleWithType(double doubleValue) {
            writeByte(TraciDatatypes.DOUBLE);
            return writeDouble(doubleValue);
        }

        /**
         * Defines a specific String value to be written.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeString(String stringValue) {
            command.writers.add(new StringTraciWriter(stringValue));
            return this;
        }

        /**
         * Defines a specific String value to be written. Additionally the data type identifier is added beforehand.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeStringWithType(String stringValue) {
            writeByte(TraciDatatypes.STRING);
            return writeString(stringValue);
        }

        /**
         * Defines a writer for adding a String parameter to the message construction.
         * For this parameter, a arguments needs to be passed to
         * the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeStringParam() {
            command.writers.add(new StringTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Vehicle ID parameter to the message construction.
         * For this parameter, one argument needs to be passed to
         * the {@link #execute(Bridge, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeVehicleIdParam() {
            command.writers.add(new VehicleIdTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Person ID parameter to the message construction.
         * For this parameter, one argument needs to be passed to
         * the {@link #execute(Bridge, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writePersonIdParam() {
            command.writers.add(new PersonIdTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a String parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeStringParamWithType() {
            writeByte(TraciDatatypes.STRING);
            return writeStringParam();
        }

        /**
         * Defines a writer for adding a String list parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeStringListParamWithType() {
            writeByte(TraciDatatypes.STRING_LIST);
            return writeStringListParam();
        }

        /**
         * Defines a writer for adding a String list parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeStringListParam() {
            command.writers.add(new ListTraciWriter<>(new StringTraciWriter()));
            return this;
        }

        /**
         * Defines a writer for adding a Double parameter to the message construction.
         * For this parameter, a arguments needs to be passed to
         * the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeDoubleParam() {
            command.writers.add(new DoubleTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Double parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeDoubleParamWithType() {
            writeByte(TraciDatatypes.DOUBLE);
            command.writers.add(new DoubleTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Integer parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeIntParamWithType() {
            writeByte(TraciDatatypes.INTEGER);
            command.writers.add(new IntegerTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Integer parameter to the message construction.
         * For this parameter, a arguments needs to be passed to
         * the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeIntParam() {
            command.writers.add(new IntegerTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Byte parameter to the message construction.
         * For this parameter, a arguments needs to be passed to
         * the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeByteParam() {
            command.writers.add(new ByteTraciWriter());
            return this;
        }

        /**
         * Defines a writer for adding a Byte parameter to the message construction.
         * Additionally the data type identifier is added beforehand.  For this parameter,
         * a arguments needs to be passed to the {@link #execute(Bridge, Object...)} methods.
         */
        @SuppressWarnings("UnusedReturnValue")

        public final TraciCommandWriterBuilder writeByteParamWithType() {
            writeByte(TraciDatatypes.BYTE);
            command.writers.add(new ByteTraciWriter());
            return this;
        }

        /**
         * Adds a custom writer.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandWriterBuilder writeComplex(AbstractTraciParameterWriter<?> complexTraciWriter) {
            command.writers.add(complexTraciWriter);
            return this;
        }
    }

    /**
     * Provides methods to configure readers in a builder-like manner. The order of
     * method calls reflects the order of readers used to read the message response.
     */
    protected final static class TraciCommandResultReaderBuilder {

        private final AbstractTraciCommand<?> command;

        private TraciCommandResultReaderBuilder(AbstractTraciCommand<?> command) {
            this.command = command;
        }

        /**
         * Defines a reader for skipping a given number of Byte values. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder skipBytes(int numberOfBytes) {
            for (int i = 0; i < numberOfBytes; i++) {
                skipByte();
            }
            return this;
        }

        /**
         * Defines a reader for skipping an Byte value. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder skipByte() {
            command.readers.add(new ByteTraciReader((Integer i) -> true));
            return this;
        }

        /**
         * Defines a reader for skipping an Integer value. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder skipInteger() {
            command.readers.add(new IntegerTraciReader((Integer b) -> true));
            return this;
        }

        /**
         * Defines a reader for reading an Integer value. The result will be passed
         * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readInteger() {
            command.readers.add(new IntegerTraciReader());
            return this;
        }

        /**
         * Defines a reader for reading an Integer value with preceding datatype identifier.
         * The result will be passed * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readIntegerWithType() {
            command.readers.add(new ByteTraciReader((Integer i) -> i == TraciDatatypes.INTEGER));
            command.readers.add(new IntegerTraciReader());
            return this;
        }

        /**
         * Defines a reader for skipping a String value. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder skipString() {
            command.readers.add(new StringTraciReader((String s) -> true));
            return this;
        }

        /**
         * Defines a reader for reading a String value. The result will be passed
         * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readString() {
            command.readers.add(new StringTraciReader());
            return this;
        }

        /**
         * Defines a reader for reading a String value with preceding datatype identifier.
         * The result will be passed * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readStringWithType() {
            command.readers.add(new ByteTraciReader((Integer i) -> i == TraciDatatypes.STRING));
            command.readers.add(new StringTraciReader());
            return this;
        }

        /**
         * Defines a reader for reading a String value with preceding datatype identifier.
         * The result will be passed * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readVehicleIdWithType() {
            command.readers.add(new ByteTraciReader((Integer i) -> i == TraciDatatypes.STRING));
            command.readers.add(new VehicleIdTraciReader());
            return this;
        }

        /**
         * Defines a reader for reading a Double value with preceding datatype identifier.
         * The result will be passed * to the {@link #constructResult(Status, Object...)} method.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readDoubleWithType() {
            command.readers.add(new ByteTraciReader((Integer i) -> i == TraciDatatypes.DOUBLE));
            command.readers.add(new DoubleTraciReader());
            return this;
        }

        /**
         * Defines a reader which expects a specific Byte value to be read. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder expectByte(final int expected) {
            command.readers.add(new ByteTraciReader((Integer i) -> i == expected));
            return this;
        }

        /**
         * Defines a reader which expects a specific Integer value to be read. (no result will be created)
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder expectInteger(final int expected) {
            command.readers.add(new IntegerTraciReader((Integer i) -> i == expected));
            return this;
        }

        /**
         * Adds a custom reader.
         */
        @SuppressWarnings("UnusedReturnValue")
        public final TraciCommandResultReaderBuilder readComplex(AbstractTraciResultReader<?> complexReader) {
            command.readers.add(complexReader);
            return this;
        }

        /**
         * Skip all remaining bytes of this command.
         */
        public void skipRemaining() {
            // nop, just for providing a fluent API
        }
    }

    interface VersionSupport {
        boolean isSupported(Bridge connection);
    }
}
