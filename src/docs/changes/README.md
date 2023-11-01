# Change Log

## v8.1.1 (2023-03-20)

**NOTE: ** As of this version, the Github repository has migrated to the `main` branch as the default branch for releases.
* Added collision logging from chapmajs.

[Release Notes](https://github.com/johnrengelman/shadow/releases/tag/8.1.1)

## v8.1.0 (2023-02-26)

**BREAKING CHANGE**: Due to adoption of the latest version of the `com.gradle.plugin-publish` plugin, the maven GAV coordinates have changed as of this version.
The correct coordinates now align with the plugin ID itself: `group=com.github.johnrengelman, artifact=shadow, version=<version>`.
For example, `classpath("com.github.johnrengelman:shadow:8.1.0")` is the correct configuration for this version.

[Release Notes](https://github.com/johnrengelman/shadow/releases/tag/8.1.0)

## v8.0.0 (2023-02-24)

[Release Notes](https://github.com/johnrengelman/shadow/releases/tag/8.0.0)

## v7.1.3 (unreleased)

- The GroovyExtensionModuleTransformer now also works for Groovy 2.5+.

## v7.1.2 (2021-12-28)

- Upgrade log4j to 2.17.1 due to CVE-2021-45105 and CVE-2021-44832

## v7.1.1 (2021-12-14)

- Upgrade log4j to 2.16.0 due to CVE-2021-44228 and CVE-2021-45046

## v7.1.0 (2021-10-04)

- **BREAKING** - The maven coordinates for the plugins have changed as of this version. The proper `group:artifact` is `gradle.plugin.com.github.johnrengelman:shadow`
- [Jeff](https://github.com/mathjeff) - Fix `shadowJar` Out-Of-Date with configuration caching [#708](https://github.com/johnrengelman/shadow/pull/708)
- [Fiouz](https://github.com/Fiouz) - Better support for statically typed languages. This change may require code changes if you are utilizing the Groovy generated getters for properties in some Shadow transformers [#706](https://github.com/johnrengelman/shadow/pull/706)
- [Helder Pereira](https://github.com/helfper) - Various cleanups [#672](https://github.com/johnrengelman/shadow/pull/672), [#700](https://github.com/johnrengelman/shadow/pull/700), [#701](https://github.com/johnrengelman/shadow/pull/701), [#702](https://github.com/johnrengelman/shadow/pull/702)
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Support JVM Toolchains [#691](https://github.com/johnrengelman/shadow/pull/691)
- [mjulianotq](https://github.com/mjulianotq) - Fix `Project.afterEvaluate` conflicts [#675](https://github.com/johnrengelman/shadow/pull/675)
- [Ilya Muradyan](https://github.com/ileasile) - Fix relocation for `ComponentsXmlResourceTransformer` [#678](https://github.com/johnrengelman/shadow/pull/678)
- [Vaidotas Valuckas](https://github.com/rieske) - Fix `JavaExec.main` deprecation [#686](https://github.com/johnrengelman/shadow/pull/686)
- [Dmitry Vyazelenko](https://github.com/vyazelenko) - Support Java 18 with ASM 9.2 [#698](https://github.com/johnrengelman/shadow/pull/698)
- [Jason](https://github.com/jpenilla) - Support Records with JDependency 2.7.0 [#681](https://github.com/johnrengelman/shadow/pull/681)

## v7.0.0 (2021-04-26)

- Required Gradle 7.0+
- Support for Java 16
- Removes JCenter references
- **Breaking Change!** - The maven group coordinate has changed to be `gradle.plugin.com.github.jengelman.gradle.plugins`. Users explicitly declaring the buildscript classpath will need to update their configuration.

  ```
  buildscript {
    repositories {
      maven {
        url "https://plugins.gradle.org/m2/"
      }
    }
    dependencies {
      classpath "gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0"
    }
  }

  apply plugin: "com.github.johnrengelman.shadow"
  ```

- [Cédric Champeau](https://github.com/melix) - Support Gradle 7 [#624](https://github.com/johnrengelman/shadow/pull/624)
- [Daniel Oakey](https://github.com/danieloakey) - Close `FileInputStream` when remapping close to avoid classloader locks [#642](https://github.com/johnrengelman/shadow/pull/642)
- [Maximilian Müller](https://github.com/maxm123) - Groovy error in `ServiceFileTransformer` in Gradle 3 [#655](https://github.com/johnrengelman/shadow/pull/655)
- [Helder Pereira](https://github.com/helfper) - Fix deprecations errors in transformers and add CI testing around future deprecations [#647](https://github.com/johnrengelman/shadow/pull/647)
- [Nicolas Humblot](https://github.com/nhumblot) - Handle deprecation of `mainClassName` configuration [#609](https://github.com/johnrengelman/shadow/pull/609), [#612](https://github.com/johnrengelman/shadow/pull/612)
- [Bernie Schelberg](https://github.com/bschelberg) - Exclude `api` and `implementations` from legacy `maven` POM [#615](https://github.com/johnrengelman/shadow/pull/615)

## v6.1.0 (2020-10-05)

- As of this version, Shadow is compiled with Java 8 source and target compatibility. This aligns the plugin with the minimum required Java version
  for Gradle 6.0 (https://docs.gradle.org/6.0/release-notes.html).
- Update ASM to 9.0 to support JDK 16.
- [Tim Yates](https://github.com/timyates), [Benedikt Ritter](https://github.com/britter) - Enable Configuration Caching for Gradle 6.6+ [#591](https://github.com/johnrengelman/shadow/pull/591)
- [Caleb Larsen](https://github.com/MuffinTheMan) - doc updates [#583](https://github.com/johnrengelman/shadow/pull/593)
- [Schalk W. Cronjé](https://github.com/ysb33r) - log4j version update for CVE-2020-9488 [#590](https://github.com/johnrengelman/shadow/pull/590)
- [Victor Tso](https://github.com/roxchkplusony) - Input stream handling for large projects [#587](https://github.com/johnrengelman/shadow/pull/587)
- [Matthew Haughton](https://github.com/3flex) - Implement Task Configuration Avoidance pattern [#597](https://github.com/johnrengelman/shadow/pull/597)

## v6.0.0 (2020-06-15)

- Required Gradle 6.0+
- _NEW_: Support for Gradle Metadata publication via the `shadowRuntimeElements` configuration. This is a _beta_ feature
  the hasn't been tested extensively. Feedback is appreciated.
- Fix Gradle 7 deprecation warnings [#530](https://github.com/johnrengelman/shadow/issues/530)
- Fix to generated start script to correctly use `optsEnvironmentVar`[#518](https://github.com/johnrengelman/shadow/commit/7e99c02957773205c3babdd23f4bbf883330c975)
- [Yahor Berdnikau](https://github.com/Tapchicoma) - Fix issues with Gradle API being embedded into published JAR [#527](https://github.com/johnrengelman/shadow/issues/527)
- [Dmitry Vyazelenko](https://github.com/vyazelenko) - ASM updates to support latest Java versions [#549](https://github.com/johnrengelman/shadow/pull/549)
- [ejjcase](https://github.com/ejjcase) - Support exposing shadowed project dependencies via POM [#543](https://github.com/johnrengelman/shadow/pull/543)
- [Artem Chubaryan](https://github.com/Armaxis) - Performance optimizations [#535](https://github.com/johnrengelman/shadow/pull/535)
- [Trask Stalnaker](https://github.com/trask) - Fix exclude patterns on Windows [#539](https://github.com/johnrengelman/shadow/pull/539)
- [Artem Chubaryan](https://github.com/Armaxis) - Allow usage of true regex patterns for include/exclude by the `%regex[<pattern>]` syntax [#536](https://github.com/johnrengelman/shadow/pull/536)

## v5.2.0 (2019-11-10)

- [Inez Korczyński](https://github.com/inez) - Performance optimization when evaluating relocation paths [#507](https://github.com/johnrengelman/shadow/pull/507)
- [Jeff Adler](https://github.com/jeffalder) - Fix remapping issues with multi release JARS [#526](https://github.com/johnrengelman/shadow/pull/526)
- [Gary Hale](https://github.com/ghale) - Implement support for Gradle build cache [#524](https://github.com/johnrengelman/shadow/pull/524)
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Gradle 6.x support [#517](https://github.com/johnrengelman/shadow/pull/517)
- [Konstantin Gribov](https://github.com/grossws) - Return support for 5.0 for convention mapping [#502](https://github.com/johnrengelman/shadow/pull/502)
- [Lai Jiang](https://github.com/jianglai) - Documentation updates on how to reconfigure `classifier` and `version` [#512](https://github.com/johnrengelman/shadow/pull/512)

## v5.1.0 (2019-06-29)

- [Chris Rankin](https://github.com/chrisr3) - Add `ManifestAppenderTransformer` to support appending to Jar manifest [#474](https://github.com/johnrengelman/shadow/pull/474)
- [Min-Ken Lai](https://github.com/minkenlai) - Additional escaping fixes in start script [#487](https://github.com/johnrengelman/shadow/pull/487)
- [Alan D. Cabrera](https://github.com/maguro) - Automatically remove `gradleApi` from `compile` scope in the presence of `shadow` [#459](https://github.com/johnrengelman/shadow/pull/459)
- [Christian Stein](https://github.com/sormuras) - Do not initialize `UnusedTracker` when not requested [#480](https://github.com/johnrengelman/shadow/pull/480), [#479](https://github.com/johnrengelman/shadow/issues/479)
- [Attila Kelemen](https://github.com/kelemen) - Fix `NullPointerException` when using java minimization and api project dependency with version [#477](https://github.com/johnrengelman/shadow/pull/477)

## v5.0.0 (2019-02-28)

- Require Gradle 5.0+
- Fix issue with build classifier `-all` being dropped in Gradle 5.1+
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Exclude project dependencies from minimization [#420](https://github.com/johnrengelman/shadow/pull/420)
- [Matt King](https://github.com/kyrrigle), [Richard Marbach](https://github.com/RichardMarbach) - Fix escaping in start script [#453](https://github.com/johnrengelman/shadow/pull/454), [#455](https://github.com/johnrengelman/shadow/pull/455)
- [Dennis Schumann](https://github.com/Hillkorn) - Fix Gradle 5.2 incompatibility with `ShadowJar.getMetaClass()` [#456](https://github.com/johnrengelman/shadow/pull/456)
- [Brane F. Gračnar](https://github.com/bfg) - Fix compatibility with `com.palantir.docker` [#460](https://github.com/johnrengelman/shadow/pull/460)

## v4.0.4 (2019-01-19)

- When using `shadow`, `application`, and `maven` plugins together, remove `shadowDistZip` and `shadowDistTar` from
  `configurations.archives` so they are not published or installed by default with the `uploadArchives` or `install`
  tasks. [#347](https://github.com/johnrengelman/shadow/issues/347)
- [James Nelson](https://github.com/JamesXNelson) - Fix `null` path when using Jar minimization and Gradle's `api` configuration. [#424](https://github.com/johnrengelman/shadow/issues/424), [#425](https://github.com/johnrengelman/shadow/issues/425)

## v4.0.3 (2018-11-21)

- [Mark Vieira](https://github.com/mark-vieira) - Don't leak plugin classes to Gradle's Spec cache [#430](https://github.com/johnrengelman/shadow/pull/430)

## v4.0.2 (2018-10-27)

- [Petar Petrov](https://github.com/petarov) - Update to ASM 7.0-beta and jdependency 2.1.1 to support Java 11, [#415](https://github.com/johnrengelman/shadow/pull/415)
- [Victor Tso](https://github.com/roxchkplusony) - Ensure input streams are closed, [#411](https://github.com/johnrengelman/shadow/pull/411)
- [Osip Fatkullin](https://github.com/osipxd) - Exclude `api` configuration from minimization, [#405](https://github.com/johnrengelman/shadow/pull/405)

## v4.0.1 (2018-09-30)

- **Breaking Change!** `Transform.modifyOutputStream(ZipOutputStream os)` to `Transform.modifyOutputStream(ZipOutputStream jos, boolean preserveFileTimestamps)`.
  Typically breaking changes are reserved for major version releases, but this change was necessary for `preserverFileTimestamps` (introduced in v4.0.0) to work correctly
  in the presence of transformers, [#404](https://github.com/johnrengelman/shadow/issues/404)
- Fix regression in support Java 10+ during relocation, [#403](https://github.com/johnrengelman/shadow/issues/403)

## v4.0.0 (2018-09-25)

- **Breaking Change!** Restrict Plugin to Gradle 4.0+. Shadow major versions will align with Gradle major versions going forward.
- **Breaking Change!** For clarity purposes `com.github.johnrengelman.plugin-shadow` has been removed. If you intend to use this feature, you will need to declare your own `ConfigureShadowRelocation` task. See section [2.9.2](http://imperceptiblethoughts.com/shadow/#automatically_relocating_dependencies) of the User Guide
- [Sergey Tselovalnikov](https://github.com/SerCeMan) - Upgrade to ASM 6.2.1 to support Java 11
- [Chris Cowan](https://github.com/Macil) - Add support for `shadowJar.preserveFileTimestamps` property. See [Jar.preserveFileTimestamps](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:preserveFileTimestamps)
- [Paul N. Baker](https://github.com/paul-nelson-baker) - Add `Log4j2PluginsCacheFileTransformer` to process Log4j DAT files during merge.
- [Felipe Lima](https://github.com/felipecsl) - Fix the long standing "No property `mainClassName`" issue.
- [debanne](https://github.com/debanne) - Implement JAR minimization actions. This will attempt to exclude unused classes in your shadowed JAR.
- Configure exclusion of `module-info.class` from `shadowJar` when using the Shadow the Java plugin, [#352](https://github.com/johnrengelman/shadow/issues/352)

## v2.0.4 (2018-04-27)

- Update to ASM 6.1.1 to address performance issues - [ASM Issue 317816](https://gitlab.ow2.org/asm/asm/issues/317816)
- Close InputStreams after using them, [#364](https://github.com/johnrengelman/shadow/issues/364)
- Remove usage of Gradle internal `AbstractFileCollection`.
- Add task annotations to remove warnings when validating plugin.

## v2.0.3 (2018-03-24)

- [Martin Sadowski](https://github.com/ttsiebzehntt) - Update to ASM 6.1
- [Scott Newson](https://github.com/sgnewson) - Fix deprecated Gradle warnings, [#356](https://github.com/johnrengelman/shadow/pull/356)

## v2.0.2 (2017-12-12)

- [Ben Adazza](https://github.com/ben-adazza), [Tyler Benson](https://github.com/tylerbenson) - documentation
- [Marke Vieira](https://github.com/mark-vieira) - Support multi-project builds with Build-Scan integration
- Upgrade to ASM 6, [#294]https://github.com/johnrengelman/shadow/issues/294, [#303](https://github.com/johnrengelman/shadow/issues/303)
- [Rob Spieldenner](https://github.com/rspieldenner) - Fix integration with `application` plugin in Gradle 4.3, [#339](https://github.com/johnrengelman/shadow/issues/339)
- Fixed deprecation warning from Gradle 4.2+, [#326](https://github.com/johnrengelman/shadow/issues/326)

## v2.0.1 (2017-06-23)

- Fix `null+configuration` error, [#297](https://github.com/johnrengelman/shadow/issues/297)

## v2.0.0 (2017-05-09)

- **Breaking Change!** Restrict Plugin to Gradle 3.0+
- **Breaking Change!** Build with Java 7
- **Breaking Change!** Updated `Transformer` interface to accept `TransformerContext` object instead of individual values
- **Breaking Change!** Updated `Relocator` interface to accept `RelocatePathContext` and `RelocateClassContext` objects
- **Breaking Change!** Distribution tasks `distShadowZip` and `distShadowTar` have been removed and replaced with the standard `shadowDistZip` and `shadowDistTar` from the Gradle Distribution plugin.
- **Breaking Change!** The `installShadowApp` task has been removed and replaced with the standard `installShadowDist` task from the Gradle Distribution plugin.
- **Breaking Change!** The new `installShadowDist` task outputs to `build/install/<project name>-shadow` per the standard (formerly was `build/installShadow`)
- **Breaking Change!** `component.shadow` removed in favor of `project.shadow.component(publication)` so as to remove dependency on internal Gradle APIs.
- _NEW_ Introducing `ConfigureShadowRelocation` task and `com.github.johnrengelman.plugin-shadow` plugin to automatically configure package relocation for Gradle plugins.
- _NEW_ Integration with Gradle Build Scans. When running a `ShadowJar` task with Build Scans, custom values including dependencies merged anc package relocations are published in the scan.
- Build Shadow w/ Shadow. This will help prevent any future classpath conflicts with Gradle.
- Replace `startShadowScripts` tasks with Gradle's built-in `CreateStartScripts` type.
- Build with Gradle 3.1
- [Marc Philipp](https://github.com/marcphilipp) - Add `keyTransformer` property to `PropertiesFileTransformer`
- Update to ASM 5.2
- [Piotr Kubowicz](https://github.com/pkubowicz) - Support `api`, `implementation`, `runtimeOnly` dependency configurations introdcued in Gradle 3.3

## v1.2.4 (2016-11-03)

- Don't resolve dependency configurations during config phase, [#128](https://github.com/johnrengelman/shadow/issues/129)
- Build plugin with Gradle 2.14
- Fix docs regarding inheriting Jar manifest, [#251](https://github.com/johnrengelman/shadow/issues/251)
- [Ethan Hall](https://github.com/ethankhall) - Support projects that configure uploading to Ivy repositories, [#256](https://github.com/johnrengelman/shadow/pull/256)
- Force task to depend on dependency configuration, [#152](https://github.com/johnrengelman/shadow/issues/152)
- Do not explode ZIP files into shadow jar, [#196](https://github.com/johnrengelman/shadow/issues/196)
- [John Szakmeister](https://github.com/jszakmeister) - Preserve timestamps on merged jar entries, [#260](https://github.com/johnrengelman/shadow/pull/260)

## v1.2.3 (2016-01-25)

- Support for Gradle 2.11-rc-1, [#177](https://github.com/johnrengelman/shadow/issues/177)
- Convert internal framework to [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
- [Fedor Korotkov](https://github.com/fkorotkov) - Use BufferedOutputStream when writing the Zip file, [#171](https://github.com/johnrengelman/shadow/pull/171[)
- [Haw-Bin Chai](https://github.com/hbchai) - Quote Jar path in Windows start script as it may contain spaces, [#170](https://github.com/johnrengelman/shadow/pull/170)
- [Serban Iordache](https://github.com/siordache) - Evaluate relocation specs when merging service descriptors, [#165](https://github.com/johnrengelman/shadow/pull/165)

## v1.2.2 (2015-07-17)

- [Minecrell](https://github.com/Minecrell) - Gradle 2.5 compatibility, [#147](https://github.com/johnrengelman/shadow/issues/147)

## v1.2.1 (2015-01-23)

- Apply package relocations to dependency resources, [#114](https://github.com/johnrengelman/shadow/issues/114)

## v1.2.0 (2014-11-24)

- Re-organize some code to remove need for forcing the Gradle API ClassLoader to allow the `org.apache.tools.zip` package.
- Upgrade JDOM library from 1.1 to 2.0.5 (change dependency from `jdom:jdom:1.1` to `org.jdom:jdom2:2.0.5`), [#98](https://github.com/johnrengelman/shadow/issues/98)
- Convert ShadowJar.groovy to ShadowJar.java to workaround binary incompatibility introduced by Gradle 2.2, [#106](https://github.com/johnrengelman/shadow/issues/106)
- Updated ASM library to `5.0.3` to support JDK8, [#97](https://github.com/johnrengelman/shadow/issues/97)
- Allows for regex pattern matching in the `dependency` string when including/excluding, [#83](https://github.com/johnrengelman/shadow/issues/83)
- Apply package relocations to resource files, [#93](https://github.com/johnrengelman/shadow/issues/93)

## v1.1.2 (2014-09-09)

- fix bug in `runShadow` where dependencies from the `shadow` configuration are not available, [#94](https://github.com/johnrengelman/shadow/issues/94)

## v1.1.1 (2014-08-27)

- Fix bug in `'createStartScripts'` task that was causing it to not execute `'shadowJar'` task, [#90](https://github.com/johnrengelman/shadow/issues/90)
- Do not include `null` in ShadowJar Manifest `'Class-Path'` value when `jar` task does not specify a value for it, [#92](https://github.com/johnrengelman/shadow/issues/92)
- ShadowJar Manifest `'Class-Path'` should reference jars from `'shadow'` config as relative to location of `shadowJar` output, [#91](https://github.com/johnrengelman/shadow/issues/91)

## v1.1.0 (2014-08-26)

- **Breaking Change!** Fix leaking of `shadowJar.manifest` into `jar.manifest`, [#82](https://github.com/johnrengelman/shadow/issues/82)
  To simplify behavior, the `shadowJar.appendManifest` method has been removed. Replace uses with `shadowJar.manifest`
- `ShadowTask` now has a `configurations` property that is resolved to the files in the resolved configuration before
  being added to the copy spec. This allows for an easier implementation for filtering. The default 'shadowJar' task
  has the convention of adding the `'runtime'` scope to this list. Manually created instances of `ShadowTask` have no
  configurations added by default and can be configured by setting `task.configurations`.
- Properly configure integration with the `'maven'` plugin when added. When adding `'maven'` the `'uploadShadow'` task
  will now properly configure the POM dependencies by removing the `'compile'` and `'runtime'` configurations from the
  POM and adding the `'shadow'` configuration as a `RUNTIME` scope in the POM. This behavior matches the behavior when
  using the `'maven-publish'` plugin.
- [Matt Hurne](https://github.com/mhurne) - Allow `ServiceFileTransformer` to specify include/exclude patterns for
  files within the configured path to merge.
- [Matt Hurne](https://github.com/mhurne) - Added `GroovyExtensionModuleTransformer` for merging Groovy Extension module
  descriptor files. The existing `ServiceFileTransformer` now excludes Groovy Extension Module descriptors by default.
- `distShadowZip` and `distShadowZip` now contain the shadow library and run scripts instead of the default from the
  `'application'` plugin, [#89](https://github.com/johnrengelman/shadow/issues/89)

## v1.0.3 (2014-07-29)

- Make service files root path configurable for `ServiceFileTransformer`, [#72](https://github.com/johnrengelman/shadow/issues/72)
- [Andres Almiray](https://github.com/aalmiray - Added PropertiesFileTransformer, [#73](https://github.com/johnrengelman/shadow/issues/73)
- [Brandon Kearby](https://github.com/brandonkearby) - Fixed StackOverflow when a cycle occurs in the resolved dependency grap, [#69](https://github.com/johnrengelman/shadow/pull/69)
- Apply Transformers to project resources, [#70](https://github.com/johnrengelman/shadow/issues/70), [#71](https://github.com/johnrengelman/shadow/issues/71)
- [Minecrell](https://github.com/Minecrell) - Do not drop non-class files from dependencies when relocation is enabled, [#61](https://github.com/johnrengelman/shadow/issues/61)
- Remove support for applying individual sub-plugins by Id (easier maintenance and cleaner presentation in Gradle Portal)

## v1.0.2 (2014-07-07)

- Do not add an empty Class-Path attribute to the manifest when the `shadow` configuration contains no dependencies.
- `runShadow` now registers `shadowJar` as an input. Previously, `runShadow` did not execute `shadowJar` and an error occurred.
- Support Gradle 2.0, [#66](https://github.com/johnrengelman/shadow/issues/66)
- Do not override existing 'Class-Path' Manifest attribute settings from Jar configuration. Instead combine, [#65](https://github.com/johnrengelman/shadow/issues/65)

## v1.0.1 (2014-06-28)

- Fix issue where non-class files are dropped when using relocation, [#58](https://github.com/johnrengelman/shadow/issues/58)
- Do not create a `/` directory inside the output jar.
- Fix `runShadow` task to evaluate the `shadowJar.archiveFile` property at execution time, [#60](https://github.com/johnrengelman/shadow/issues/60)

## v1.0.0 (2014-06-27)

- Previously known as v0.9.0
- All changes from 0.9.0-M1 to 0.9.0-M5
- Properly configure the ShadowJar task inputs to observe the include/excludes from the `dependencies` block. This
  allows UP-TO-DATE checking to work properly when changing the `dependencies` rulea, [#54](https://github.com/johnrengelman/shadow/issues/54)
- Apply relocation remappings to classes and imports in source project, [#55](https://github.com/johnrengelman/shadow/issues/55)
- Do not create directories in jar for source of remapped class, created directories in jar for destination of remapped classes, [#53](https://github.com/johnrengelman/shadow/issues/53)

## v0.9.0-M5

- Add commons-io to compile classpath
- Update asm library to 4.1

## v0.9.0-M4

- Break plugin into multiple sub-plugins. `ShadowBasePlugin` is always applied.
  `ShadowJavaPlugin` and `ShadowApplicationPlugin` are applied in reaction to applying the `java` and `application`
  plugins respectively.
- Shadow does not applied `java` plugin automatically. `java` or `groovy` must be applied in conjunction with `shadow`.
- Moved artifact filtering to `dependencies {}` block underneath `shadowJar`. This allows better include/exclude control
  for dependencies.
- Dependencies added to the `shadow` configuration are automatically added to the `Class-Path` attribute in the manifest
  for `shadowJar`
- Applying `application` plugin and settings `mainClassName` automatically configures the `Main-Class` attribute in
  the manifest for `shadowJar`
- `runShadow` now utilizes the output of the `shadowJar` and executes using `java -jar <shadow jar file>`
- Start Scripts for shadow distribution now utilize `java -jar` to execute instead of placing all files on classpath
  and executing main class.
- Excluding/Including dependencies no longer includes transitive dependencies. All dependencies for inclusion/exclusion
  must be explicitly configured via a spec.

## v0.9.0-M3

- Use commons.io FilenameUtils to determine name of resolved jars for including/excluding

## v0.9.0-M2

- Added integration with `application` plugin to replace old `OutputSignedJars` task
- Fixed bug that resulted in duplicate file entries in the resulting Jar
- Changed plugin id to 'com.github.johnrengelman.shadow' to support Gradle 2.x plugin infrastructure.

## v0.9.0-M1

- Rewrite based on Gradle Jar Task
- `ShadowJar` now extends `Jar`
- Removed `signedCompile` and `signedRuntime` configurations in favor of `shadow` configuration
- Removed `OutputSignedJars` task
