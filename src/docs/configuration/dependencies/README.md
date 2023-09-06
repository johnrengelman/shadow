# Configuring Shadowed Dependencies

Shadow configures the default `shadowJar` task to merge all dependencies from the project's `runtimeClasspath` configuration
into the final JAR.
The configurations from which to source dependencies for the merging can be configured using the `configurations` property
of the [`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task type.

```groovy
shadowJar {
  configurations = [project.configurations.compileClasspath]
}
```

The above code sample would configure the `shadowJar` task to merge dependencies from only the `compileClasspath` configuration.
This means any dependency declared in the `runtimeOnly` configuration would be **not** be included in the final JAR.

> Note the literal use of `project.configurations` when setting the `configurations` attribute of a
[`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task.
This is **required**. It maybe be tempting to specify `configurations = [configurations.compile]` but this will not
have the intended effect, as `configurations.compile` will try to delegate to the `configurations` property of the
the [`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task instead of the `project`

## Embedding Jar Files Inside Your Shadow Jar

Because of the way that Gradle handles dependency configuration, from a plugin perspective, shadow is unable to 
distinguish between a jar file configured as a dependency and a jar file included in the resource folder.  This means 
that any jar found in a resource directory will be merged into the shadow jar the same as any other dependency.  If 
your intention is to embed the jar inside, you must rename the jar as to not end with `.jar` before the shadow task 
begins.

## Filtering Dependencies

Individual dependencies can be filtered from the final JAR by using the `dependencies` block of a
[`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task.
Dependency filtering does **not** apply to transitive dependencies.
That is, excluding a dependency does not exclude any of its dependencies from the final JAR.

The `dependency` blocks provides a number of methods for resolving dependencies using the notations familiar from
Gradle's `configurations` block.

```groovy
// Exclude an Module Dependency
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency('org.apache.logging.log4j:log4j-core:2.11.1'))
  }
}
```

```groovy
// Exclude a Project Dependency
dependencies {
  implementation project(':api')
}

shadowJar {
  dependencies {
    exclude(project(':api'))
  }
}
```

> While not being able to filter entire transitive dependency graphs might seem like an oversight, it is necessary
because it would not be possible to intelligently determine the build author's intended results when there is a
common dependency between two 1st level dependencies when one is excluded and the other is not.

### Using Regex Patterns to Filter Dependencies

Dependencies can be filtered using regex patterns.
Coupled with the `<group>:<artifact>:<version>` notation for dependencies, this allows for excluding/including
using any of these individual fields.

```groovy
// Exclude Any Version of a Dependency
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency('org.apache.logging.log4j:log4j-core:.*'))
  }
}
```

Any of the individual fields can be safely absent and will function as though a wildcard was specified.

```groovy
// Ignore Dependency Version
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency('org.apache.logging.log4j:log4j-core'))
  }
}
```

The above code snippet is functionally equivalent to the previous example.

This same pattern can be used for any of the dependency notation fields.

```groovy
// Ignoring An Artifact Regardless of Group
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency(':log4j-core:2.11.1'))
  }
}
```

```groovy
// Excluding All Artifacts From Group
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency('org.apache.logging.log4j::2.11.1'))
  }
}
```

### Programmatically Selecting Dependencies to Filter

If more complex decisions are needed to select the dependencies to be included, the
[`dependencies`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html#dependencies(Action<DependencyFilter>))
block provides a method that accepts a `Closure` for selecting dependencies.

```groovy
// Selecting Dependencies to Filter With a Spec
dependencies {
  implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
}

shadowJar {
  dependencies {
    exclude(dependency {
      it.moduleGroup == 'org.apache.logging.log4j'
    })
  }
}
```
