<!--
Draft comment for https://github.com/apple/pkl-spring/issues/2
Review, then post it yourself (e.g. `gh issue comment 2 --repo apple/pkl-spring --body-file docs/issue-2-comment.md`).
-->

Here's a **self-contained Maven setup** that works, and specifically avoids the two problems reported above (the duplicate-class conflict and the runtime `NPE`).

Two root causes:

1. **Duplicate classes / Truffle clash.** Putting `pkl-codegen-java` (or `pkl-tools`) on the *same* classpath as `pkl-spring` fails, because `pkl-spring` pulls in `pkl-config-java-all` and both jars bundle GraalVM Truffle → `Duplicate optional resource id libtruffleattach for component engine`. The fix is to run codegen in a **separate JVM whose classpath is only `pkl-tools`**.
2. **Version drift → NPE.** The code generator and the runtime must be the *same* Pkl version. `pkl-spring` `0.18.0` pulls in `pkl-config-java-all` **0.31.1**, so pin `pkl-tools` to `0.31.1` too.

### pom.xml

```xml
<properties>
  <pkl.version>0.31.1</pkl.version>            <!-- must match pkl-spring's transitive Pkl runtime -->
  <pkl-spring.version>0.18.0</pkl-spring.version>
  <pkl.generated.dir>${project.build.directory}/generated-sources/pkl</pkl.generated.dir>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.pkl-lang</groupId>
    <artifactId>pkl-spring</artifactId>
    <version>${pkl-spring.version}</version>
  </dependency>
  <!-- build-time only; provided keeps it out of the runnable jar -->
  <dependency>
    <groupId>org.pkl-lang</groupId>
    <artifactId>pkl-tools</artifactId>
    <version>${pkl.version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <!-- 1) Generate config classes in an ISOLATED JVM (only pkl-tools on the classpath) -->
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

    <!-- 2) Add the generated sources (note the `java/` subdir the generator creates) -->
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
  </plugins>
</build>
```

With `AppConfig.pkl` + `application.pkl` in `src/main/resources` and `@ConfigurationPropertiesScan` on the application class, `mvn spring-boot:run` prints the bound config. This mirrors the official Gradle `spring-boot` sample exactly.

### Note on the `NPE`

The `NullPointerException` reported above comes from generating **without** `--generate-spring-boot`: the classes are then plain data classes with no `@ConfigurationProperties`, so Spring never binds them and injection yields `null`. Adding that flag (as above) fixes it.

### Would a first-class Maven story be welcome?

The `exec` + `build-helper` + version-pinning boilerplate is easy to get wrong (as this thread shows). I've prototyped a small **`pkl-maven-plugin`** (a `generate-java` goal that bundles the toolchain and runs codegen in a forked JVM — a Maven plugin's isolated class realm sidesteps the Truffle clash entirely, and it does `addCompileSourceRoot` for you) plus a thin **`pkl-spring-boot-starter`**. With those, the whole thing reduces to one dependency and one plugin execution. Happy to open a PR adding a `samples/spring-boot-maven` sample and/or contribute the plugin if the maintainers are interested — let me know which direction you'd prefer.
