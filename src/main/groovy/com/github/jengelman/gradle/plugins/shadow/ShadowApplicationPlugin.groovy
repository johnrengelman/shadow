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
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService

class ShadowApplicationPlugin implements Plugin<Project> {

    public static final String SHADOW_RUN_TASK_NAME = 'runShadow'
    public static final String SHADOW_SCRIPTS_TASK_NAME = 'startShadowScripts'
    public static final String SHADOW_INSTALL_TASK_NAME = 'installShadowDist'

    private Project project
    private JavaApplication javaApplication

    @Override
    void apply(Project project) {
        this.project = project
        this.javaApplication = project.extensions.getByType(JavaApplication)

        DistributionContainer distributions = project.extensions.getByName("distributions") as DistributionContainer
        Distribution distribution = distributions.create("shadow")

        addRunTask(project)
        addCreateScriptsTask(project)

        configureDistSpec(project, distribution.contents)

        configureJarMainClass(project)
        configureInstallTask(project)
    }

    protected void configureJarMainClass(Project project) {
        def classNameProvider = javaApplication.mainClass
        jar.configure { jar ->
            jar.inputs.property('mainClassName', classNameProvider)
            jar.doFirst {
                manifest.attributes 'Main-Class': classNameProvider.get()
            }
        }
    }

    protected void addRunTask(Project project) {

        project.tasks.register(SHADOW_RUN_TASK_NAME, JavaJarExec) { run ->
            def install = project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync)
            run.dependsOn SHADOW_INSTALL_TASK_NAME
            run.mainClass.set('-jar')
            run.description = 'Runs this project as a JVM application using the shadow jar'
            run.group = ApplicationPlugin.APPLICATION_GROUP
            run.conventionMapping.jvmArgs = { javaApplication.applicationDefaultJvmArgs }
            run.conventionMapping.jarFile = {
                project.file("${install.get().destinationDir.path}/lib/${jar.get().archiveFile.get().asFile.name}")
            }
            configureJavaLauncher(run)
        }
    }

    private void configureJavaLauncher(JavaJarExec run) {
        def toolchain = project.getExtensions().getByType(JavaPluginExtension.class).toolchain
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class)
        Provider<JavaLauncher> defaultLauncher = service.launcherFor(toolchain)
        run.getJavaLauncher().set(defaultLauncher)
    }

    protected void addCreateScriptsTask(Project project) {
        project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts) { startScripts ->
            startScripts.unixStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/unixStartScript.txt").text)
            startScripts.windowsStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/windowsStartScript.txt").text)
            startScripts.description = 'Creates OS specific scripts to run the project as a JVM application using the shadow jar'
            startScripts.group = ApplicationPlugin.APPLICATION_GROUP
            startScripts.classpath = project.files(jar)
            startScripts.conventionMapping.mainClassName = { javaApplication.mainClass.get() }
            startScripts.conventionMapping.applicationName = { javaApplication.applicationName }
            startScripts.conventionMapping.outputDir = { new File(project.layout.buildDirectory.asFile.get(), 'scriptsShadow') }
            startScripts.conventionMapping.defaultJvmOpts = { javaApplication.applicationDefaultJvmArgs }
            startScripts.inputs.files project.objects.fileCollection().from { -> jar }
        }
    }

    protected void configureInstallTask(Project project) {
        project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync).configure { task ->
            task.doFirst {
                if (task.destinationDir.directory) {
                    if (task.destinationDir.listFiles().size() != 0 && (!new File(task.destinationDir, 'lib').directory || !new File(task.destinationDir, 'bin').directory)) {
                        throw new GradleException("The specified installation directory '${task.destinationDir}' is neither empty nor does it contain an installation for '${javaApplication.applicationName}'.\n" +
                                "If you really want to install to this directory, delete it and run the install task again.\n" +
                                "Alternatively, choose a different installation directory."
                        )
                    }
                }
            }
            task.doLast {
                task.eachFile {
                    if (it.path == "bin/${javaApplication.applicationName}") {
                        it.mode = 0x755
                    }
                }
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
                filePermissions { unix(493) }
            }
        }

        distSpec
    }

    private TaskProvider<ShadowJar> getJar() {
        project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar)
    }
}
