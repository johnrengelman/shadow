# Integrating with Application Plugin

Shadow reacts to the presence of Gradle's
[`application`](https://docs.gradle.org/current/userguide/application_plugin.html) plugin and will automatically
configure additional tasks for running the shadowed JAR and creating distributions containing the shadowed JAR.

Just like the normal `jar` task, when the `application` plugin is applied, the `shadowJar` manifest will be
configured to contain the `Main-Class` attribute with the value specified in the project's `mainClassName` attribute.

```groovy
// Using Shadow with Application Plugin
apply plugin: 'java'
apply plugin: 'application'
apply plugin: 'com.github.johnrengelman.shadow'

application {
    mainClass = 'myapp.Main'
}
```

## Running the Shadow JAR

When applied along with the `application` plugin, the `runShadow` task will be created for starting
the application from the shadowed JAR.
The `runShadow` task is a [`JavaExec`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html)
task that is configured to execute `java -jar myproject-all.jar`.
It can be configured the same as any other `JavaExec` task.

```groovy
// Configuring the runShadow Task
runShadow {
  args 'foo'
}
```

## Distributing the Shadow JAR

The Shadow plugin will also configure distribution tasks when in the presence of the `application` plugin.
The plugin will create `shadowDistZip` and `shadowDistTar` which creates Zip and Tar distributions
respectively.
Each distribution will contain the shadowed JAR file along with the necessary start scripts to launch
the application.

Additionally, the plugin will create the `installShadowDist` and `startShadowScripts` tasks which stages the necessary
files for a distribution to `build/install/<project name>-shadow/`.
