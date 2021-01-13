package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.util.GradleVersion

class ShadowApplicationPlugin implements Plugin<Project> {

    static final String SHADOW_RUN_TASK_NAME = 'runShadow'
    static final String SHADOW_SCRIPTS_TASK_NAME = 'startShadowScripts'
    static final String SHADOW_INSTALL_TASK_NAME = 'installShadowDist'

    private Project project
    private ApplicationPluginConvention pluginConvention

    @Override
    void apply(Project project) {
        this.project = project
        this.pluginConvention = (ApplicationPluginConvention) project.convention.plugins.application

        DistributionContainer distributions = project.extensions.getByName("distributions")
        Distribution distribution = distributions.create("shadow")

        addRunTask(project)
        addCreateScriptsTask(project)

        configureDistSpec(project, distribution.contents)

        configureJarMainClass(project)
        configureInstallTask(project)

        project.plugins.withType(MavenPlugin) {
            project.configurations.archives.with {
                artifacts.findAll {
                    if (it.hasProperty("provider")) {
                        it.provider.get().is(project.tasks.shadowDistZip) ||
                                it.provider.get().is(project.tasks.shadowDistTar)
                    }
                }.each {
                    artifacts.remove it
                }
            }
        }
    }

    protected void configureJarMainClass(Project project) {
        def classNameProvider = getMainClass(project)
        jar.configure { jar ->
            jar.inputs.property('mainClassName', classNameProvider)
            jar.doFirst {
                manifest.attributes 'Main-Class': classNameProvider.get()
            }
        }
    }

    private static Provider<String> getMainClass(Project project) {
        def application = (JavaApplication) project.extensions.application
        if (GradleVersion.current() >= GradleVersion.version("6.4")) {
            return application.mainClass.orElse(project.provider { application.mainClassName })
        } else {
            return project.provider { application.mainClassName }
        }
    }

    protected void addRunTask(Project project) {
        ApplicationPluginConvention pluginConvention = (
                ApplicationPluginConvention) project.convention.plugins.application

        project.tasks.register(SHADOW_RUN_TASK_NAME, JavaJarExec) { run ->
            def install = project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync)
            run.dependsOn SHADOW_INSTALL_TASK_NAME
            run.setMain('-jar')
            run.description = 'Runs this project as a JVM application using the shadow jar'
            run.group = ApplicationPlugin.APPLICATION_GROUP
            run.conventionMapping.jvmArgs = { pluginConvention.applicationDefaultJvmArgs }
            run.conventionMapping.jarFile = {
                project.file("${install.get().destinationDir.path}/lib/${jar.get().archivePath.name}")
            }
        }
    }

    protected void addCreateScriptsTask(Project project) {
        ApplicationPluginConvention pluginConvention =
                (ApplicationPluginConvention) project.convention.plugins.application

        project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts) { startScripts ->
            startScripts.unixStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/unixStartScript.txt").text)
            startScripts.windowsStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/windowsStartScript.txt").text)
            startScripts.description = 'Creates OS specific scripts to run the project as a JVM application using the shadow jar'
            startScripts.group = ApplicationPlugin.APPLICATION_GROUP
            startScripts.classpath = project.files(jar)
            startScripts.conventionMapping.mainClassName = { getMainClass(project).get() }
            startScripts.conventionMapping.applicationName = { pluginConvention.applicationName }
            startScripts.conventionMapping.outputDir = { new File(project.buildDir, 'scriptsShadow') }
            startScripts.conventionMapping.defaultJvmOpts = { pluginConvention.applicationDefaultJvmArgs }
            startScripts.inputs.files project.objects.fileCollection().from { -> jar }
        }
    }

    protected void configureInstallTask(Project project) {
        ApplicationPluginConvention pluginConvention =
                (ApplicationPluginConvention) project.convention.plugins.application

        project.tasks.named(SHADOW_INSTALL_TASK_NAME).configure { installTask ->
            installTask.doFirst { Sync task ->
                if (task.destinationDir.directory) {
                    if (task.destinationDir.listFiles().size() != 0 && (!new File(task.destinationDir, 'lib').directory || !new File(task.destinationDir, 'bin').directory)) {
                        throw new GradleException("The specified installation directory '${task.destinationDir}' is neither empty nor does it contain an installation for '${pluginConvention.applicationName}'.\n" +
                                "If you really want to install to this directory, delete it and run the install task again.\n" +
                                "Alternatively, choose a different installation directory."
                        )
                    }
                }
            }
            installTask.doLast { Sync task ->
                project.ant.chmod(file: "${task.destinationDir.absolutePath}/bin/${pluginConvention.applicationName}", perm: 'ugo+x')
            }
        }
    }

    protected CopySpec configureDistSpec(Project project, CopySpec distSpec) {
        def startScripts = project.tasks.named(SHADOW_SCRIPTS_TASK_NAME)

        distSpec.with {
            from(project.file("src/dist"))

            into("lib") {
                from(jar)
                from(project.configurations.shadow)
            }
            into("bin") {
                from(startScripts)
                fileMode = 493
            }
        }

        distSpec
    }

    private TaskProvider<ShadowJar> getJar() {
        project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar)
    }
}
