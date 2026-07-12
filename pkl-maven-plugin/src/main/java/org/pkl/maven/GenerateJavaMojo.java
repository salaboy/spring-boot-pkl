package org.pkl.maven;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates Java configuration classes from Pkl schema modules using {@code pkl-codegen-java}.
 *
 * <p>Code generation runs in a forked JVM whose classpath is the bundled {@code pkl-tools}
 * jar only. This keeps it isolated from the Pkl runtime jars on the project's own classpath
 * (which also bundle GraalVM Truffle and would otherwise collide in a single classloader).
 */
@Mojo(
    name = "generate-java",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true)
public class GenerateJavaMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Pkl schema modules to generate Java classes from. */
  @Parameter(required = true)
  private List<File> sourceModules;

  /**
   * Directory for generated sources. The generator writes into a {@code java/} subdirectory,
   * which is what gets added as a compile source root.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/pkl")
  private File outputDirectory;

  /** Generate {@code @ConfigurationProperties} classes for Spring Boot. */
  @Parameter(defaultValue = "true")
  private boolean generateSpringBoot;

  /** Generate private fields with getters instead of public fields. */
  @Parameter(defaultValue = "true")
  private boolean generateGetters;

  /** Add the generated {@code java/} directory as a compile source root. */
  @Parameter(defaultValue = "true")
  private boolean addCompileSourceRoot;

  /** Resolved plugin dependencies, used to locate the bundled {@code pkl-tools} jar. */
  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true, required = true)
  private List<Artifact> pluginArtifacts;

  @Override
  public void execute() throws MojoExecutionException {
    if (sourceModules == null || sourceModules.isEmpty()) {
      throw new MojoExecutionException("No <sourceModules> configured for pkl:generate-java.");
    }

    File pklTools = findPklToolsJar();
    String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

    List<String> command = new ArrayList<>();
    command.add(javaBin);
    command.add("-cp");
    command.add(pklTools.getAbsolutePath());
    command.add("org.pkl.codegen.java.Main");
    if (generateSpringBoot) {
      command.add("--generate-spring-boot");
    }
    if (generateGetters) {
      command.add("--generate-getters");
    }
    command.add("--output-dir");
    command.add(outputDirectory.getAbsolutePath());
    for (File module : sourceModules) {
      command.add(module.getAbsolutePath());
    }

    getLog().info("Generating Java config classes from " + sourceModules + " into " + outputDirectory);
    getLog().debug("Codegen command: " + command);

    int exitCode;
    try {
      exitCode = new ProcessBuilder(command).inheritIO().start().waitFor();
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to run pkl-codegen-java", e);
    }
    if (exitCode != 0) {
      throw new MojoExecutionException("pkl-codegen-java exited with code " + exitCode);
    }

    if (addCompileSourceRoot) {
      File javaDir = new File(outputDirectory, "java");
      project.addCompileSourceRoot(javaDir.getAbsolutePath());
      getLog().info("Added compile source root: " + javaDir);
    }
  }

  private File findPklToolsJar() throws MojoExecutionException {
    for (Artifact artifact : pluginArtifacts) {
      if ("pkl-tools".equals(artifact.getArtifactId()) && artifact.getFile() != null) {
        return artifact.getFile();
      }
    }
    throw new MojoExecutionException(
        "Could not locate the pkl-tools jar on the plugin classpath. "
            + "This is a bug in pkl-maven-plugin.");
  }
}
