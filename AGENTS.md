# AGENTS Playbook
Purpose-built for autonomous coding agents working in `eclipse-mosaic/mosaic`.

## Repository Orientation
- Parent `pom.xml` aggregates `bundle`, `rti`, `lib`, `fed`, `test`, and tutorial `app` modules.
- `bundle/` assembles the runnable distribution that ends up under `bundle/target`.
- `rti/` houses the runtime infrastructure API, core, and starter entry point `org.eclipse.mosaic.starter.MosaicStarter`.
- `lib/` modules (communication, database, docker, geomath, interactions, network, perception, routing, objects, utils) provide shared Java libraries.
- `fed/` modules wrap simulators (application, environment, cell, mapping, ns3, omnetpp, output, sns, sumo) as HLA-inspired federates.
- `test/mosaic-integration-tests` contains long-running simulation ITs plus helper rules like `MosaicSimulationRule` and SUMO-backed apps.
- `app/tutorials/*` ship example applications (e.g., `highway-management`, `traffic-light-communication`, `weather-warning`).
- `scenarios/` and `legal/` contain reusable simulation inputs and Eclipse licensing templates respectively.
- Check `.github/workflows` for CI expectations (`mvn -B package`) and Jenkins mirrors the same on ci.eclipse.org.

## Platform Highlights
- Per [eclipse.dev/mosaic](https://eclipse.dev/mosaic), MOSAIC provides a multi-domain, multi-scale co-simulation stack that blends traffic, communication, and application models through standardized HLA-inspired interfaces.
- Latest public release is 25.2 (Dec 2025) featuring demand-responsive traffic (DRT) tooling; previous 25.1 introduced full-stack LTE C-V2X in ns-3—stay aligned with these capabilities when referencing examples.
- Official docs, tutorials, and workshops are accessible via the site navigation; keep README pointers up-to-date when new guides drop.
- Website highlights canonical use cases (mobility apps, intelligent connected vehicles, flexible traffic management, e-mobility, collaborative driving); reflect these narratives in examples or demo scenarios you add.
- Fraunhofer FOKUS and DCAITI maintain the platform; respect their branding (EPL-2.0) and cite the IEEE T-ITS paper listed on the homepage when producing academic content.

## Toolchain & Environment
- Requires Java 17+ (Temurin recommended) and Maven 3.1+; Java 21 works but source/target stays at 17.
- SUMO 1.25.0 is the tested traffic simulator; set `SUMO_HOME` before invoking SUMO-dependent ITs or bundles.
- Optional simulators (ns-3, OMNeT++, GraphHopper 10.2) are consumed through `fed` modules; install only if needed.
- Logging is SLF4J + Logback 1.5.0 with overrides in bundle resources; match that stack when extending logging.
- Mockito 5.15.2 and JUnit 4.13.2 are the default test stack via the parent POM.
- SpotBugs annotations come from `findbugs-annotations`; use them for nullness/contract hints.
- Encoding is UTF-8 everywhere; new files must stay in UTF-8 and include the EPL-2.0 header.
- IP compliance is enforced; dependency updates require Eclipse CQ approval (see comments in `pom.xml`).

## Build & Package Commands
- Full build with tests: `mvn clean install` (runs across every module, including integration scenarios if SUMO is configured).
- Skip tests when bootstrapping: `mvn install -DskipTests` (use sparingly and call out in PRs).
- Build a subset and its dependencies: `mvn -pl lib/mosaic-utils -am clean install`.
- Package the runnable bundle: `mvn -pl bundle -am package` then unzip `bundle/target/mosaic-bundle-<version>.zip`.
- Rebuild tutorials only (after libs exist): `mvn -pl app/tutorials/example-applications -am package`.
- Generate distributable NOTICE info: `mvn -Pgenerate-third-party-list-parent org.codehaus.mojo:license-maven-plugin:aggregate-add-third-party`.
- Enable coverage reports (off by default via `skip.coverage=true`): `mvn -Dskip.coverage=false verify` to emit JaCoCo HTML under `target/site/jacoco`.
- Re-enable Javadoc generation when needed: `mvn -Dskip.javadoc=false javadoc:aggregate`.

## Test Strategy & Commands
- Run the entire unit test suite inside one module: `mvn -pl lib/mosaic-utils test`.
- Execute a single test class: `mvn -pl lib/mosaic-utils -Dtest=RingBufferTest test`.
- Execute a single test method: `mvn -pl lib/mosaic-utils -Dtest=RingBufferTest#iterateOverFullBuffer test` (Surefire 3.5.0 syntax).
- Integration tests live in `test/mosaic-integration-tests` and expect SUMO + scenario assets; run selectively via `mvn -pl test/mosaic-integration-tests -Dtest=ReleaseHelloWorldIT test`.
- Many integration tests spin up MOSAIC via `MosaicSimulationRule`; confirm `SUMO_HOME` and network ports are free.
- When SUMO or external simulators are unavailable, keep `mvn clean install` green by restricting the reactor: `mvn -pl '!test/mosaic-integration-tests' -am clean install`.
- Mockito-based tests use classic `@RunWith(MockitoJUnitRunner.class)` or manual `MockitoAnnotations.openMocks`; stay with JUnit4 patterns for consistency.
- Test resources (JSON configs, SUMO data) live under each module's `src/test/resources`; keep file-relative lookups via `ClassLoader#getResource` and wrap with `Objects.requireNonNull` like existing tests.

## Quality Gates & Static Analysis
- Checkstyle enforces Google Java Style tweaks; invoke via `mvn -Dcheckstyle.skip=false checkstyle:check` (config at `checkstyle.xml`, exclusions in `checkstyle-excludes.xml`).
- SpotBugs runs through `com.github.spotbugs:spotbugs-maven-plugin`; trigger locally with `mvn com.github.spotbugs:spotbugs-maven-plugin:spotbugs -Dspotbugs.skip=false` and honor `spotbugs-excludes.xml`.
- CodeQL scanning is configured for `java-kotlin` with build mode `none`; avoid introducing languages outside Java without updating `.github/workflows/codeql.yml`.
- Jenkins and GitHub Actions both expect `mvn -B package`; do not rely on `mvnw` because it is not present in this repo.
- JaCoCo hooks are wired but opt-in; ensure new features flip coverage on at least once per PR before merge.
- Legal tooling uses `legal/templates`; when adding dependencies ensure they appear in `NOTICE-THIRD-PARTY.md` after running the license plugin profile.
- Git history should stay linear; squash before merging and keep author email matching your Eclipse Foundation account (see `CONTRIBUTING.md`).

## Runtime & Scenarios
- After packaging, run MOSAIC via `bundle/target/mosaic-bundle-*/bin/mosaic.sh -s HelloWorld` (or `.bat` on Windows).
- IDE launches should use `org.eclipse.mosaic.starter.MosaicStarter` and reference scenario configs from `bundle/config` or `scenarios`.
- `scenarios/` contains SUMO networks, configuration JSON, and assets referenced by tutorial apps; use relative paths so bundles stay portable.
- `fed/mosaic-sumo` is the TraCI bridge; ensure the SUMO version in `pom.xml` matches the SUMO binaries on PATH.
- Example applications in `app/tutorials` are wired to demonstration scenarios; mirror their patterns when adding new instructional content.

## Module Entry Points
- `rti/mosaic-rti-api` and `rti/mosaic-rti-core` expose the federation contracts plus time management and scheduling kernels.
- `rti/mosaic-starter` houses CLI parsing and the `MosaicStarter` main used by both bundle scripts and IDE launches.
- `lib/*` modules share the `org.eclipse.mosaic.lib` namespace and contain reusable math, routing, perception, and utility code—prefer extending them over duplicating logic.
- `fed/*` packages ship ambassadors that wrap external simulators; each module keeps its own `config` subtree and Gradle-like resources under `src/main`.
- `bundle/` layers configuration, launch scripts, and logging defaults on top of the built artifacts; adjust Logback profiles there.
- `app/tutorials` contains runnable teaching apps that map directly to entries under `scenarios/`; use them as regression fixtures when touching tutorial APIs.
- `test/mosaic-integration-tests` mixes `org.eclipse.mosaic.test.*` ITs with helper apps so everything needed to run a release scenario lives in one module.
- `legal/` and top-level `NOTICE*.md` capture licensing; rerun the license plugin whenever dependencies shift.

## Simulator-Specific Notes
- `fed/mosaic-sumo` talks to SUMO/Libsumo; keep `version.libsumo` aligned with your SUMO install and remember the website touts SUMO as the microscopic traffic backbone handling megacity-scale networks faster than real time.
- `fed/mosaic-application` supplies reference mobility, traffic-management, and safety apps; docs stress modeling RSUs, traffic lights, and vehicle logic with publish/subscribe interactions, so keep DTOs stable for cross-domain reuse.
- `fed/mosaic-cell` and `fed/mosaic-sns` coordinate cellular and ad-hoc backbones; align message DTOs with `lib/mosaic-objects` before changing payloads and preserve LTE/5G plus ITS-G5 scenarios highlighted on the site.
- `fed/mosaic-ns3` and `fed/mosaic-omnetpp` wrap high-fidelity network simulators; native installs remain optional but recommended for studies like the ns-3 LTE C-V2X stack announced in release 25.1.
- `lib/mosaic-routing` wraps GraphHopper 10.2; tweak routing behavior via `GraphHopperRouting`/`GraphHopperWeighting` rather than editing federates, and surface DRT-related heuristics through routing configs when referencing release 25.2 examples.
- `lib/mosaic-geomath` and `lib/mosaic-perception` depend on JTS and custom sensor fusion types; preserve floating-point tolerances already encoded in tests and mirror the perception-module docs (field-of-view filters backed by spatial indexes).
- `lib/mosaic-utils` offers scheduling, CLI, serialization, and file helpers (e.g., `ProcessLoggingThread`, `RingBuffer`); re-use them to maintain consistent behavior.
- `fed/mosaic-output` formats run-time telemetry; adjust classes like `ExtendedMethodSet` only with a migration plan for downstream tooling and the visualization stack (2D/3D/Statistics) featured on the website.

## Visualization & Evaluation
- 2D Visualizer (browser-based) and 3D Visualizer (PHABMACS engine) are the preferred front ends for inspecting runs; both are documented at [eclipse.dev/mosaic/docs/visualization](https://eclipse.dev/mosaic/docs/visualization/).
- `fed/mosaic-output` powers FileOutput, which logs specific interactions for downstream analytics; keep custom formats compatible with existing consumers.
- Statistics Visualizer aggregates metrics such as travel time, per-class speed, and loop-based flow; ensure newly logged KPIs slot cleanly into this pipeline.
- When adding perception or traffic-management demos, capture relevant screenshots or videos using the official visualization toolchain to match website narratives.

## Maven Flag Reference
- Use `-pl module1,module2` to limit the reactor scope; combine with `-am` to pull required dependencies automatically.
- Prefix modules with `!` (e.g., `-pl '!test/mosaic-integration-tests'`) to exclude heavy integrations when SUMO or other simulators are unavailable.
- Pass `-Dtest=Class#method` or `-Dtest=Pattern*` for targeted Surefire runs; prefer module-scoped invocations to avoid cross-module pollution.
- Toggle static analysis by overriding plugin skips: `-Dcheckstyle.skip=false`, `-Dspotbugs.skip=false`.
- Enable coverage instrumentation with `-Dskip.coverage=false`; disable again before pushing if the extra overhead slows builds.
- Rebuild API docs via `-Dskip.javadoc=false javadoc:aggregate` when publishing developer bundles or updating website artifacts.
- Use `-DskipTests` only on clean feature branches; document the compromise in PR descriptions and re-run tests before merging.
- Combine `-Dmaven.repo.local=/custom/path` or `-nsu` sparingly for CI, keeping shared scripts aligned with upstream defaults.

## Workflow Guardrails
- Discuss substantial or cross-cutting work via GitHub issues or Discussions before opening large PRs, as requested in `CONTRIBUTING.md`.
- Branch off `main`, keep scope focused, and squash to a single logical commit before requesting review.
- Sign commits and use the Eclipse Foundation email that matches your CLA; CI checks this metadata in Jenkins and GitHub.
- Never commit generated artifacts from `target/`, IDE metadata, or local simulator binaries; extend `.gitignore` instead of deleting user files.
- Update README/tutorial docs when changing user-facing behavior so downstream bundle users stay current.
- Run `mvn clean install` (or at least the narrowed module plan) plus Checkstyle/SpotBugs locally before every PR to keep CI green.
- Keep TODO/FIXME markers out of the codebase unless paired with a tracking issue number and short removal timeline.
- Coordinate with release engineers before altering dependency versions, SUMO expectations, or public JSON schema contracts.

## Code Style & Formatting
- Follow Google Java Style with MOSAIC tweaks: 4 spaces, never tabs, and a soft 140-character line limit.
- Brace style is K&R: opening brace on the same line, closing brace alone on its own line, with `else`/`catch` hugging the closing brace.
- One statement per line, and keep methods focused and short; extract helpers when logic branches multiply.
- Imports never use wildcards; order groups as `org.eclipse.mosaic.*`, third-party packages, then `java.*`, with blank lines between groups and static imports placed after regular ones.
- Add horizontal whitespace around binary/ternary operators, after casts, and between keywords (`if`, `for`, `catch`) and parentheses as enforced by Checkstyle.
- File encoding must remain UTF-8 and every Java file starts with the EPL-2.0 header block shown in `CONTRIBUTING.md`.
- Keep `@Override` on every implemented method and include Javadoc (`/** ... */`) for all public types, constructors, and methods (getters/setters may omit docs if obvious).
- Inline comments should explain intent, not mechanics; prefer `// why this is needed` rather than repeating code.
- Avoid TODO/FIXME markers; if absolutely necessary, pair them with a tracking issue ID and remove before merging.

## Naming & Types
- Classes, interfaces, and enums use UpperCamelCase; interfaces never use an `I` prefix.
- Enum constants and read-only static fields are SCREAMING_SNAKE_CASE; mutable fields and methods stay lowerCamelCase.
- Packages are lowercase, dot-separated, starting with `org.eclipse.mosaic`.
- Boolean getters prefer `is/has` prefixes, collection-returning methods should document mutability (use `Collections.unmodifiable*` when exposing internals).
- Type parameters are single uppercase letters (e.g., `T`, `E`), matching the table in `CONTRIBUTING.md`.
- Abbreviations are discouraged unless widely recognized (IP, V2X, DENM, SUMO); when used inside identifiers only the first letter stays capitalized (`DenmContent`, `V2xMessage`).
- Favor `Optional` only when absence is meaningful; otherwise throw `IllegalArgumentException` or `IllegalStateException` early with descriptive messages.
- For non-null enforcement, rely on `Objects.requireNonNull` (ubiquitous across libs) and document the contract in Javadoc.

## Error Handling & Logging
- Guard public methods with `Objects.requireNonNull` or explicit argument validation; prefer `IllegalArgumentException` for bad inputs and `IllegalStateException` when sequencing is wrong.
- Keep exception messages actionable (state what was expected vs. received) because integration tests inspect logs.
- Wrap checked exceptions only when adding context; otherwise propagate them so callers can react.
- Logging flows through SLF4J; obtain loggers via `LoggerFactory.getLogger(getClass())` and never use `System.out`.
- Long-running threads (e.g., `ProcessLoggingThread`) should honor the existing pattern: use volatile flags, provide `close()` hooks, and suppress warnings with explicit justification strings.
- Use `@SuppressWarnings` sparingly and always include the rationale, mirroring examples in `lib/mosaic-utils`.

## Testing Patterns
- Stick with JUnit 4 rules and annotations; reuse helper classes like `MosaicSimulationRule`, `LibsumoCheckRule`, and `LogAssert` when spinning up simulations.
- Mock external collaborators with Mockito and verify critical logging via `LogAssert.expect(String pattern)` if behavior is observable only via logs.
- Use deterministic data: SUMO fixtures, JSON configs, and serialized payloads belong under `src/test/resources`; load them with `getResourceAsStream` and fail fast via `Objects.requireNonNull`.
- Prefer descriptive test names mirroring existing `CamelCase` plus `_ExpectedBehavior` suffixes.
- Keep assertions concise using `assertEquals`, `assertTrue`, etc., and annotate exception tests with `@Test(expected = ...)` instead of manual try/catch, matching `RingBufferTest` style.
- Integration tests often orchestrate MOSAIC end-to-end; mark them clearly with `*IT` suffixes and isolate scenario-specific code under `org.eclipse.mosaic.test.app.*` packages.

## AI & Assistant Notes
- No Cursor rules (`.cursor/rules` or `.cursorrules`) or Copilot instructions exist in this repo as of 2026-01-30; treat this document plus `CONTRIBUTING.md` as the authority.
- Run available commands yourself (no pseudocode) and summarize results; default to action unless blocked by credentials, destructive changes, or ambiguous specs.
- Reference files with workspace-relative paths (e.g., `lib/mosaic-utils/src/main/java/...`) and favor structured tools (`Read`, `Glob`, `apply_patch`) instead of ad-hoc shell edits.
