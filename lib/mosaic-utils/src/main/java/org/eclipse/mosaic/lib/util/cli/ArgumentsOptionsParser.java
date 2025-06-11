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

package org.eclipse.mosaic.lib.util.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Automatically converts a list of arguments and options from the command line into a parameter object.
 * The parameter object is expected to contain fields annotated with @{@link CliOption} or @{@link CliArgument}.
 * {@link CliArgument}s follows a strict order and are expected at the beginning of the input command call. After
 * that, additional {@link CliOption}s follow, which have a short and/or long parameter name.
 *
 * @param <T> class of the parameter object, which later holds the parsed parameter values
 */
public class ArgumentsOptionsParser<T> {

    private final CommandLineParser parser = new DefaultParser();
    private final Options options = new Options();
    private final List<Field> parameterFields;
    private final List<Field> argumentFields;

    private String usageHint;
    private String header;
    private String footer;

    /**
     * Constructs a new {@link ArgumentsOptionsParser} with a class of the object, which later holds the
     * argument and option values. This must also declare all command line related properties, such
     * as the name of the option and its description. For this, the fields in the given class
     * need to be public and need to be annotated with a @{@link CliArgument} or @{@link CliOption}.
     * Everything else is done by this parser.
     */
    public ArgumentsOptionsParser(final Class<T> parameterClass) {
        parameterFields = new LinkedList<>();

        options.addOption("h", "help", false, "Prints this help screen.\n");

        // collect all fields which hold required arguments
        argumentFields = Arrays.stream(FieldUtils.getAllFields(parameterClass))
                .filter(f -> f.isAnnotationPresent(CliArgument.class))
                .sorted(Comparator.comparingInt(a -> a.getAnnotation(CliArgument.class).index()))
                .collect(Collectors.toList());

        // build Options out of declared fields of the parameter object
        final Map<String, OptionGroup> optionGroups = new HashMap<>();
        for (Field field : FieldUtils.getAllFields(parameterClass)) {
            if (field.isAnnotationPresent(CliOption.class)) {
                final CliOption cliAnnotation = field.getAnnotation(CliOption.class);

                final Option option =
                        new Option(StringUtils.defaultIfBlank(cliAnnotation.shortOption(), null), cliAnnotation.description());
                option.setLongOpt(cliAnnotation.longOption());
                if (StringUtils.isNotEmpty(cliAnnotation.argumentHint())) {
                    option.setArgs(1);
                    option.setArgName(cliAnnotation.argumentHint());
                    option.setOptionalArg(StringUtils.isNotEmpty(cliAnnotation.defaultArgValue()));
                }

                if (StringUtils.isNotBlank(cliAnnotation.group())) {
                    OptionGroup optionGroup = optionGroups
                            .computeIfAbsent(cliAnnotation.group().trim(), (k) -> new OptionGroup());
                    optionGroup.addOption(option);
                    if (cliAnnotation.isRequired()) {
                        optionGroup.setRequired(true);
                    }
                } else {
                    if (cliAnnotation.isRequired()) {
                        option.setRequired(true);
                    }
                    options.addOption(option);
                }
                parameterFields.add(field);
            }
        }
        optionGroups.values().forEach(options::addOptionGroup);
    }

    /**
     * Parses a list of arguments (POSIX style) and writes the set values into the given parameter object.
     * The parameter object should be of the same class as this parser is initialized with.
     *
     * @param args         the input arguments from the command line.
     * @param targetObject the object which provides the required fields to fill in values parsed from the list of args.
     */
    public final T parseArguments(final String[] args, final T targetObject) throws ParseException {
        if (((!options.getRequiredOptions().isEmpty() || !argumentFields.isEmpty()) && args.length == 0)
                || ArrayUtils.contains(args, "--help")
                || ArrayUtils.contains(args, "-h")) {
            printHelp();
            return null;
        }

        final LinkedList<String> argumentsToParse = filterSystemProperties(args);

        for (Field argumentField : argumentFields) {
            if (argumentsToParse.isEmpty() || argumentsToParse.peek().startsWith("-")) {
                throw new IllegalArgumentException("Missing argument: <" + argumentField.getAnnotation(CliArgument.class).argumentHint() + ">");
            }
            setField(argumentField, targetObject, argumentsToParse.removeFirst());
        }

        if (!argumentsToParse.isEmpty()) {
            Validate.isTrue(argumentsToParse.peek().startsWith("-"), "Unrecognized option: " + argumentsToParse.peek());
        }

        // parse command line
        final CommandLine line = parser.parse(options, argumentsToParse.toArray(new String[0]));

        // write option values into parameter object
        for (Field field : parameterFields) {
            final CliOption cliAnnotation = field.getAnnotation(CliOption.class);
            field.setAccessible(true);

            if (!line.hasOption(cliAnnotation.longOption())) {
                continue;
            }

            try {
                final String defaultValue = cliAnnotation.defaultArgValue();

                if (boolean.class.isAssignableFrom(field.getType())) {
                    field.set(targetObject, true);
                } else if (List.class.isAssignableFrom(field.getType())) {
                    field.set(targetObject, Arrays.asList(line.getOptionValues(cliAnnotation.longOption()), defaultValue));
                } else {
                    setField(field, targetObject, line.getOptionValue(cliAnnotation.longOption(), defaultValue));
                }
            } catch (Throwable e) {
                throw new ParseException("Could not set field " + field.getName() + ": " + e.getLocalizedMessage());
            }
        }

        return targetObject;
    }

    private void setField(Field field, Object target, String value) throws ParseException {
        try {
            field.setAccessible(true);
            if (double.class.isAssignableFrom(field.getType()) || Double.class.isAssignableFrom(field.getType())) {
                field.set(target, Double.parseDouble(value));
            } else if (float.class.isAssignableFrom(field.getType()) || Float.class.isAssignableFrom(field.getType())) {
                field.set(target, Float.parseFloat(value));
            } else if (int.class.isAssignableFrom(field.getType()) || Integer.class.isAssignableFrom(field.getType())) {
                field.set(target, Integer.parseInt(value));
            } else if (long.class.isAssignableFrom(field.getType()) || Long.class.isAssignableFrom(field.getType())) {
                field.set(target, Long.parseLong(value));
            } else if (File.class.isAssignableFrom(field.getType())) {
                field.set(target, new File(value));
            } else {
                field.set(target, value);
            }
        } catch (Throwable e) {
            throw new ParseException("Could not set field " + field.getName() + ": " + e.getMessage());
        }
    }

    private LinkedList<String> filterSystemProperties(String[] args) {
        final LinkedList<String> argumentsToParse = new LinkedList<>();

        for (String arg : args) {
            if (arg.startsWith("-D") && arg.contains("=")) {
                String[] systemProperty = arg.substring(2).split("=");
                System.setProperty(systemProperty[0], systemProperty[1]);
            } else {
                argumentsToParse.add(arg);
            }
        }
        return argumentsToParse;
    }

    /**
     * Transforms the object, which holds the parameter values into a list of arguments, which can be
     * used to start MOSAIC processes with valid arguments.
     *
     * @throws RuntimeException if a field of the parameter class could not be transformed to an argument
     */
    public final List<String> transformToArguments(final T parameters) {
        final List<String> arguments = new LinkedList<>();

        try {
            for (Field field : parameterFields) {
                final CliOption cliAnnotation = field.getAnnotation(CliOption.class);

                if (boolean.class.isAssignableFrom(field.getType())) {
                    if (field.getBoolean(parameters)) {
                        arguments.add("--" + cliAnnotation.longOption());
                    }
                } else if (File.class.isAssignableFrom(field.getType())) {
                    if (field.get(parameters) != null) {
                        arguments.add("--" + cliAnnotation.longOption());
                        arguments.add(((File) field.get(parameters)).getAbsolutePath());
                    }
                } else {
                    if (field.get(parameters) != null) {
                        arguments.add("--" + cliAnnotation.longOption());
                        arguments.add(String.valueOf(field.get(parameters)));
                    }
                }

            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Could not transform parameters to arguments", e);
        }
        return arguments;
    }

    /**
     * Prints the help.
     *
     * @param printWriter writer to output help to
     */
    public void printHelp(PrintWriter printWriter) {
        final HelpFormatter helpFormatter = new HelpFormatter();

        final List<String> ordering = new LinkedList<>();
        for (Field field : parameterFields) {
            final CliOption cliAnnotation = field.getAnnotation(CliOption.class);
            ordering.add(cliAnnotation.longOption());
        }
        helpFormatter.setOptionComparator(Comparator.comparingInt(o -> ordering.indexOf(o.getLongOpt())));

        if (StringUtils.isNotBlank(usageHint)) {
            printWriter.print("USAGE\n  ");
            helpFormatter.printWrapped(printWriter, 120, 10, usageHint);
            printWriter.println();
        }

        if (StringUtils.isNotEmpty(header)) {
            helpFormatter.printWrapped(printWriter, 120, 2, header);
            printWriter.println();
        }

        if (!argumentFields.isEmpty()) {
            final StringBuilder arguments = new StringBuilder("ARGUMENTS");
            final int indent = argumentFields.stream()
                    .mapToInt(a -> a.getAnnotation(CliArgument.class).argumentHint().length()).max().orElse(0) + 4;
            for (Field argumentField : argumentFields) {
                CliArgument arg = argumentField.getAnnotation(CliArgument.class);
                arguments.append("\n  ").append(arg.argumentHint()).append(" ");
                int repeat = indent - arg.argumentHint().length() - 2;
                if (repeat > 0) {
                    arguments.append(StringUtils.repeat(" ", repeat));
                }
                arguments.append(arg.description());
            }
            arguments.append("\n\n");
            helpFormatter.printWrapped(printWriter, 120, 2, arguments.toString());
        }

        printWriter.println("OPTIONS");
        helpFormatter.printOptions(printWriter, 120, getOptions(), 2, 3);

        if (StringUtils.isNotEmpty(footer)) {
            helpFormatter.printWrapped(printWriter, 120, 2, footer);
        }
    }

    public void printHelp() {
        printHelp(new PrintWriter(System.out, true, StandardCharsets.UTF_8));
    }

    /**
     * This method provides all options declared in the parameter object this parser has been initialized with.
     */
    public final Options getOptions() {
        return options;
    }

    /**
     * Returns the names of all required arguments.
     */
    List<String> getArgumentFields() {
        return argumentFields.stream().map(f -> f.getAnnotation(CliArgument.class).argumentHint()).collect(Collectors.toList());
    }

    /**
     * This method is used to define a usage hint for the respective {@link ArgumentsOptionsParser}.
     *
     * @param usageHint the hint to be set
     * @param header    header for the hint
     * @param footer    footer for the hint
     * @return the object to chain further methods
     */
    public ArgumentsOptionsParser<T> usageHint(String usageHint, String header, String footer) {
        this.header = ObjectUtils.defaultIfNull(header, this.header);
        this.footer = ObjectUtils.defaultIfNull(footer, this.footer);
        this.usageHint = ObjectUtils.defaultIfNull(usageHint, this.usageHint);
        return this;
    }
}
