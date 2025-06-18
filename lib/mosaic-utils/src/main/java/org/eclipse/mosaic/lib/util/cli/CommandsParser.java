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

package org.eclipse.mosaic.lib.util.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Automatically converts a list of arguments and options from the command line based on a list of
 * (sub)commands. The input is expected to match the format "command [ARGUMENTS] [OPTIONS]".
 * Each command is a separate class implementing {@link Runnable} and annotated with @{@link CliCommand}.
 * The fields within the command class shall be annotated with @{@link CliArgument} or @{@link CliOption} to
 * define required arguments in a fixed order and/or additional options.
 */
public class CommandsParser {

    private final static Pattern COMMAND_PATTERN = Pattern.compile("^[a-z]+(?: [a-z]+)*$");

    private final Map<String, CommandExecutable<? extends Runnable>> commandExecutables = new HashMap<>();

    private int maxCommandLength = 0;
    private CommandExecutable<?> selectedCommand;

    private String usageHint;
    private String header;
    private String footer;

    public CommandsParser(Runnable atLeastOneCommand, Runnable... furtherCommands) {

        final List<Runnable> allCommands = new ArrayList<>();
        allCommands.add(atLeastOneCommand);
        allCommands.addAll(List.of(furtherCommands));

        for (Runnable runnable : allCommands) {
            final CommandExecutable<?> commandExecutable = new CommandExecutable<>(runnable);

            final String commandKey = commandExecutable.command();
            Validate.isTrue(!commandExecutables.containsKey(commandKey), "Command \"{}\" is already defined.", commandKey);
            this.commandExecutables.put(commandKey, commandExecutable);

            maxCommandLength = Math.max(maxCommandLength, commandKey.split(" ").length);
        }
    }

    /**
     * Parses a list of arguments and selects the matching command class and returns it.
     * Furthermore, it sets all values into the fields of the command class according to
     * provided @{@link CliArgument} and @{@link CliOption} annotations.
     *
     * @param args the plain args of the command line input
     * @throws ParseException           if the given input command line is wrong formatted (e.g., missing arguments)
     * @throws IllegalArgumentException if the given arguments are wrongly formatted (e.g., refer to missing files)
     */
    public final Runnable parseArguments(final String[] args) throws ParseException {
        selectedCommand = null;

        final LinkedList<String> argumentsToParse = new LinkedList<>(Arrays.asList(args));

        if (argumentsToParse.isEmpty()) {
            printHelp();
            return null;
        }

        boolean printHelp = argumentsToParse.contains("-h") || argumentsToParse.contains("--help");

        if (argumentsToParse.size() == 1 && printHelp) {
            printHelp();
            return null;
        }

        selectedCommand = chooseCommand(argumentsToParse);

        if (printHelp) {
            selectedCommand.printHelp();
            return null;
        }

        return selectedCommand.parseArguments(argumentsToParse);
    }

    private CommandExecutable<? extends Runnable> chooseCommand(LinkedList<String> argumentsToParse) {
        List<String> commandsSortedByLength = commandExecutables.keySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(a -> StringUtils.countMatches(a, " "))))
                .collect(Collectors.toList());

        // test all commands (starting with the longest one), if any of these matches with the provided arguments
        for (String commandKey : commandsSortedByLength) {
            int commandLength = commandKey.split(" ").length;
            String commandInArguments = argumentsToParse.stream().limit(commandLength).collect(Collectors.joining(" "));
            if (commandInArguments.equalsIgnoreCase(commandKey)) {
                for (int i = 0; i < commandLength; i++) {
                    argumentsToParse.removeFirst();
                }
                return commandExecutables.get(commandKey);
            }
        }

        if (!argumentsToParse.isEmpty() && COMMAND_PATTERN.matcher(argumentsToParse.peek()).matches()) {
            throw new IllegalArgumentException("Unknown command: " + guessInputCommandFromArguments(argumentsToParse));
        }
        throw new IllegalArgumentException("No command given.");
    }

    /**
     * Selects and joins the first items of the provided arguments which could be meant to be a command.
     * This is only required if the input command could not be matched with any available commands
     * and therefore used only for helping purposes.
     */
    private String guessInputCommandFromArguments(List<String> arguments) {
        StringBuilder guess = new StringBuilder();
        for (int i = 0; i < Math.min(arguments.size(), maxCommandLength); i++) {
            String arg = arguments.get(i);
            if (!COMMAND_PATTERN.matcher(arg).matches()) {
                return guess.toString();
            }
            if (guess.length() > 0) {
                guess.append(" ");
            }
            guess.append(arg);
        }
        return guess.toString();
    }

    /**
     * Prints a help screen to standard output.
     */
    public final void printHelp() {
        printHelp(new PrintWriter(System.out, true, StandardCharsets.UTF_8));
    }

    /**
     * Prints a help screen to provided {@link PrintWriter}.
     *
     * @param printWriter writer to output help to
     */
    public final void printHelp(PrintWriter printWriter) {
        if (selectedCommand != null) {
            selectedCommand.printHelp(printWriter);
            return;
        }

        final String commandUsage = this.usageHint + " command [-h] [<ARGUMENT(S)>] [OPTION(S)]";

        final StringBuilder header = new StringBuilder();
        if (StringUtils.isNotEmpty(this.header)) {
            header.append(this.header).append("\n\n");
        }
        header.append("COMMANDS\n");
        int indent = commandExecutables.keySet().stream().mapToInt(String::length).max().orElse(0) + 5;
        commandExecutables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                header.append(StringUtils.rightPad("  " + e.getKey(), indent, " "))
                        .append(e.getValue().commandDescription.description())
                        .append("\n")
        );
        new ArgumentsOptionsParser<>(Object.class)
                .usageHint(commandUsage, header.toString(), footer)
                .printHelp(printWriter);
    }

    /**
     * This method is used to define a usage hint for the respective {@link ArgumentsOptionsParser}.
     *
     * @param usageHint the hint to be set
     * @param header    header for the hint
     * @return the object to chain further methods
     */
    public CommandsParser usageHint(String usageHint, String header, String footer) {
        this.header = ObjectUtils.defaultIfNull(header, this.header);
        this.usageHint = ObjectUtils.defaultIfNull(usageHint, this.usageHint);
        this.footer = ObjectUtils.defaultIfNull(footer, this.footer);
        return this;
    }

    private static class CommandExecutable<T extends Runnable> {

        private final ArgumentsOptionsParser<T> parser;
        private final T executable;

        private final CliCommand commandDescription;

        private CommandExecutable(T executable) {
            Validate.isTrue(executable.getClass().isAnnotationPresent(CliCommand.class), "Executable must be annotated with @CliCommand");
            this.commandDescription = executable.getClass().getAnnotation(CliCommand.class);
            this.executable = executable;
            this.parser = new ArgumentsOptionsParser<>((Class<T>) executable.getClass());
            parser.usageHint(generateUsage(), generateHeader(), null);

            final String commandKey = commandDescription.command();
            Validate.isTrue(COMMAND_PATTERN.matcher(commandKey).matches(), "Command \"{}\" does not match the required pattern.");
        }

        private String command() {
            return commandDescription.command();
        }

        private void printHelp() {
            parser.printHelp();
        }

        private void printHelp(PrintWriter pw) {
            parser.printHelp(pw);
        }

        private T parseArguments(List<String> arguments) throws ParseException {
            return parser.parseArguments(arguments.toArray(String[]::new), executable);
        }

        private String generateUsage() {
            final StringBuilder builder = new StringBuilder()
                    .append(commandDescription.command());
            parser.getArgumentFields().forEach(a ->
                    builder.append(" <").append(a).append(">")
            );
            for (Option option : parser.getOptions().getOptions()) {
                if ("h".equals(option.getOpt())) {
                    continue;
                }
                builder.append(" [");
                if (option.getOpt() != null) {
                    builder.append("-").append(option.getOpt());
                } else {
                    builder.append("--").append(option.getLongOpt());
                }
                if (option.hasArg()) {
                    builder.append(" <").append(option.getArgName()).append(">");
                }
                builder.append("]");
            }
            return builder.toString();
        }

        private String generateHeader() {
            return "DESCRIPTION\n  "
                    + commandDescription.description()
                    + (StringUtils.isNotBlank(commandDescription.help()) ? " " + commandDescription.help() : "");
        }

    }
}
