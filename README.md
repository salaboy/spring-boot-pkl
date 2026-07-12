# Pkl + Spring Boot: first-class Maven integration

Configuring a Spring Boot app with [Pkl](https://pkl-lang.org) is well supported for
**Gradle** (via the official [`apple/pkl-spring`](https://github.com/apple/pkl-spring)
samples and the Pkl Gradle plugin), but **Maven** users are on their own — there is no Pkl
Maven plugin, and the naive setup hits a GraalVM Truffle classloader clash and version-drift
NPEs (see [apple/pkl-spring#2](https://github.com/apple/pkl-spring/issues/2)).

This repo does two things:

1. **A working Maven example** — both the hand-wired baseline and a clean version.
2. **Two prototypes** for first-class integration, to explore contributing upstream.

## Modules

| Module | What it is |
| --- | --- |
| [`pkl-maven-plugin`](pkl-maven-plugin) | A Maven plugin with a `generate-java` goal that wraps `pkl-codegen-java`. Bundles the Pkl toolchain and runs codegen in a forked JVM, so users don't wire `exec` + `build-helper`, pin versions, or fight the Truffle clash. |
| [`pkl-spring-boot-starter`](pkl-spring-boot-starter) | A Spring Boot starter that aggregates `pkl-spring` (which self-registers a `PropertySourceLoader` and auto-configuration) into a single dependency. |
| [`example`](example) | A minimal Spring Boot app that uses both of the above. |
| [`docs/manual-setup.md`](docs/manual-setup.md) | The baseline: same result with only stock plugins, and the gotchas it works around. |

## Try it

```bash
# Build + install the plugin and starter into your local ~/.m2:
mvn install

# Run the example (uses the plugin at build time, the starter at runtime):
mvn -f example spring-boot:run
```

Expected output:

```
Server {
  endpoints = [Endpoint { name = endpoint1  port = 1234 },
               Endpoint { name = endpoint2  port = 5678 }]
}
```

## Using the plugin in your own project

```xml
<plugin>
  <groupId>org.pkl-lang.spring</groupId>
  <artifactId>pkl-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals><goal>generate-java</goal></goals>
      <configuration>
        <sourceModules>
          <sourceModule>${project.basedir}/src/main/resources/AppConfig.pkl</sourceModule>
        </sourceModules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Config options: `sourceModules` (required), `outputDirectory`, `generateSpringBoot`
(default `true`), `generateGetters` (default `true`), `addCompileSourceRoot` (default `true`).

## Status

These are **prototypes** (`groupId org.pkl-lang.spring`, not published) intended to seed a
discussion about upstreaming Maven support to the Pkl project.
