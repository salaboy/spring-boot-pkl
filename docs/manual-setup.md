# Configuring Spring Boot with Pkl using Maven (no custom plugin)

The official Pkl tooling ships a **Gradle plugin** but **no Maven plugin**. This page shows
how to get the same result with a stock Maven build, using only `exec-maven-plugin` and
`build-helper-maven-plugin`. (This repo also prototypes a dedicated `pkl-maven-plugin` and a
`pkl-spring-boot-starter` that remove this boilerplate — see the top-level README.)

## The two moving parts

1. **Code generation (build time).** `pkl-codegen-java` turns a schema `AppConfig.pkl` into a
   Java class annotated with `@ConfigurationProperties`. The `--generate-spring-boot` flag is
   what adds the Spring annotations.
2. **Property loading (runtime).** `pkl-spring` registers a `PropertySourceLoader` that reads
   `application.pkl` through Spring Boot's standard config mechanism.

## The gotchas (why the naive setup fails)

The GitHub issue [apple/pkl-spring#2](https://github.com/apple/pkl-spring/issues/2) reports two
problems that this setup avoids:

- **Duplicate classes / Truffle clash.** Adding `pkl-codegen-java` (or `pkl-tools`) to the same
  classpath as `pkl-spring` fails, because `pkl-spring` pulls in `pkl-config-java-all` and both
  jars bundle GraalVM Truffle (`Duplicate optional resource id libtruffleattach for component
  engine`). Fix: run codegen in a **separate JVM whose classpath is only `pkl-tools`**.
- **Version drift / NPE at runtime.** The code generator and the runtime must agree on the Pkl
  version. Pin `pkl-tools` to the same Pkl version that `pkl-spring` pulls in transitively
  (for `pkl-spring` 0.18.0 that is `pkl-config-java-all` **0.31.1**).

## pom.xml

```xml
<properties>
  <java.version>21</java.version>
  <!-- Keep in sync with the Pkl runtime that pkl-spring pulls in transitively. -->
  <pkl.version>0.31.1</pkl.version>
  <pkl-spring.version>0.18.0</pkl-spring.version>
  <pkl.generated.dir>${project.build.directory}/generated-sources/pkl</pkl.generated.dir>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
  </dependency>

  <!-- Registers the PropertySourceLoader that reads application.pkl at runtime. -->
  <dependency>
    <groupId>org.pkl-lang</groupId>
    <artifactId>pkl-spring</artifactId>
    <version>${pkl-spring.version}</version>
  </dependency>

  <!-- Pkl toolchain, build-time only; provided keeps it out of the runnable jar. -->
  <dependency>
    <groupId>org.pkl-lang</groupId>
    <artifactId>pkl-tools</artifactId>
    <version>${pkl.version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <!-- 1) Generate Java config classes in an ISOLATED JVM (only pkl-tools on classpath). -->
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <id>pkl-codegen</id>
          <phase>generate-sources</phase>
          <goals><goal>exec</goal></goals>
          <configuration>
            <executable>java</executable>
            <arguments>
              <argument>-cp</argument>
              <argument>${settings.localRepository}/org/pkl-lang/pkl-tools/${pkl.version}/pkl-tools-${pkl.version}.jar</argument>
              <argument>org.pkl.codegen.java.Main</argument>
              <argument>--generate-spring-boot</argument>
              <argument>--generate-getters</argument>
              <argument>--output-dir</argument>
              <argument>${pkl.generated.dir}</argument>
              <argument>${project.basedir}/src/main/resources/AppConfig.pkl</argument>
            </arguments>
          </configuration>
        </execution>
      </executions>
    </plugin>

    <!-- 2) Add the generated sources (note the `java/` subdir) to the compile roots. -->
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>build-helper-maven-plugin</artifactId>
      <version>3.6.0</version>
      <executions>
        <execution>
          <id>add-pkl-generated-sources</id>
          <phase>generate-sources</phase>
          <goals><goal>add-source</goal></goals>
          <configuration>
            <sources><source>${pkl.generated.dir}/java</source></sources>
          </configuration>
        </execution>
      </executions>
    </plugin>

    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

With `AppConfig.pkl` and `application.pkl` in `src/main/resources`, and the application class
annotated with `@ConfigurationPropertiesScan`, `mvn spring-boot:run` prints the bound config.
