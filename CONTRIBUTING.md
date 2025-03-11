# Contribute to Eclipse MOSAIC

Thank you for your interest in contributing to the Eclipse MOSAIC project.

## Issues

You are more than welcomed to report bugs or suggest enhancements on our [GitHub repository](https://github.com/eclipse-mosaic/mosaic).
Before creating a new issue, please check if the problem or feature was not reported already by someone else. 

When reporting bugs, please follow the guidelines 
proposed by [Eclipse](https://bugs.eclipse.org/bugs/page.cgi?id=bug-writing.html):
* Be precise
* Be clear - explain it so others can reproduce the bug
* One bug per report
* No bug is too trivial to report - small bugs may hide big bugs
* Clearly separate fact from speculation

If you want to suggest a new feature or enhancement, please provide as many details as possible:
* What exact enhancement do you think of  what's the purpose of it?
* Why do you need this feature?
* Do you already have ideas about how this could be implemented?

## Prerequisites

Eclipse MOSAIC is an Eclipse Project and therefore requires all committers to follow the Eclipse Development process. Each contributor 
is obligated to execute the following steps in order to us to be able to accept her or his contributions: 

* Create a user account for the Eclipse Foundation. If you don't already have such an account, please register 
  [on the Eclipse web site](https://dev.eclipse.org/site_login/createaccount.php). 
* Sign the [Contributor License Agreement](http://www.eclipse.org/legal/CLA.php) (see `My Account/Contributor License Aggreement` tab in your profile.)
* Add your GitHub username to your Eclipse Foundation profile.

## Contribute

Please first discuss the change you wish to make via issue or email with us as the owners of this repository. 
To share contributions with us,  create a GitHub pull request following the steps below.
You must use the same email address in your Git commits which is associated with your Eclipse account.
More details can be found [here](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git). 
    
Make your changes by following those steps:

* Create a fork of this repository on GitHub
* Create a new branch
* Make your changes
* Add new tests for new functionality and extend tests before refactoring code.
* Check your contribution carefully (see Definition of Done below):
  * All unit tests must pass with your changes (`mvn clean install` must succeed).
  * No additional spotbugs warnings must be found.  
  * The code format should follow our Codestyle (see below).  
  * Check for any missing license headers.
* Commit your changes into your branch:
    * **Use meaningful commit messages**.
    * Ideally there's only one single commit per contribution. If you have more than one, squash them into a single commit. 
    * Use the email address from your Eclipse account in the author field.
* Push your changes to your branch.
* Create a pull request with a meaningful description of your changes.
  
## Definition of Done

* The code is well documented.
* Existing documentation has been updated.
* Your solution observes our coding and testing guidelines (see below).
* New functionality of your solution is covered by either unit or integration tests - The code coverage must not decrease!
* SpotBugs does not report any new warnings.
* There are no new `FIXME`/`TODO` in your code.
* New files contain a valid license header.
* The main branch of this repository has been merged into your fork.
* Everything's Green! - All related builds and integration tests on Jenkins are successful.

## Coding Conventions

We understand that each developer has different preferences in regard to code formatting, especially when it comes to indentation and braces. 
However, the look of our codebase needs to be consistent and therefore the following conventions must be followed. 

### Code Style

Eclipse MOSAIC follows the [Google Java Style Guide](https://github.com/google/styleguide). Please stick to those conventions to 
get your contribution accepted.

* No tab characters, 4-space indentation!
* One statement per line
* Column limit of 140 characters per line. The shorter the better.
* Braces follow Kernighan and Ritchie Style:
  * No line break before the opening brace.
  * Line break after the opening brace.
  * Line break before the closing brace.
  * Line break after the closing brace. (exception: else after if, catch after try)
* Horizontal whitespaces
  * Separate any reserved word (e.g. if, for, catch) from an open parenthesis
  * Separate any reserved word (e.g. else, catch) from a closing curly brace
  * Before any curly brace (except array definitions): `for (int i=0; i<10; i++) {`
  * After the closing parenthesis of a cast, e.g. `Target target = ((Target) object);`
  * On both sides of any binary or ternary operator, e.g. `a == 1 && b == 3`
* Methods should be as short as possible. Classes should focus on one thing only.
* UTF-8 file encoding
* No asterisk sign for imports
* Grouping and ordering of imports (blank line between groups):
  * Imports from `org.eclipse.mosaic`
  * Imports from third-party-libraries
  * Standard libraries
  
The Code style can be checked using a _Checkstyle_ plugin in your IDE. A `checkstyle.xml` file can be found in the root of the repository.
  
### Naming conventions

| Element                | Casing         | Example          |
|------------------------|----------------|------------------|
| Class                  | UpperCamelCase | AppDomain        |
| Interface              | UpperCamelCase | IBusinessService |
| Enumeration type       | UpperCamelCase | ErrorLevel       |
| Enumeration values     | UPPER_CASE     | FATAL            |
| Field                  | lowerCamelCase | mainPanel        |
| Final field            | lowerCamelCase | maximumItems     |
| Read-only static field | UPPER_CASE     | RED_VALUE        |
| Variable               | lowerCamelCase | listOfValues     |
| Method                 | lowerCamelCase | toString         |
| Package                | lowercase      | org.eclipse      |
| Parameter              | lowerCamelCase | typeName         |
| Type Parameter         | UPPER_CASE     | T                |
 
* All names should reflect the purpose of the class, method, or field.
* Class names should be nouns.
* Interfaces should be adjectives (e.g. Clonable, Moveable) or nouns if they resemble a family of classes
  (e.g. `Map`, `Vehicle`). Interfaces should _not_ start with an `I` prefix (e.g. `IVehicle`).
* Abbreviations should be avoided, unless they're very common (e.g. IP, V2X, DENM, etc). When written, only the first letter is 
  in upper case, e.g. `V2xMessage`, `DenmContent` `getIpAddress`.
* Methods returning a boolean preferably start with `is` or `has`.
  
### Commenting

* Add license header at the top of each source file (see below).
* Use `//` in code for inline comments. Describe _why_, not _what_'s happening.
* Use standard javadoc (`/** ... */`) and its annotations (`@param`, `@return`) for all _public_ classes, constructors, 
  and methods. Simple self-explaining methods, like Getters and Setters do not require a method comment.
 
## License header

Each Java file must include the following license header at the top of each file:
 
```java
/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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
```

If this is your first contribution, please also add your Copyright information to [NOTICE.md](NOTICE.md).
