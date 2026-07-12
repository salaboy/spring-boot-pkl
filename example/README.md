# Spring Boot + Pkl example (Maven)

A minimal Spring Boot application configured with [Pkl](https://pkl-lang.org), built with
**Maven**. It mirrors the official [`apple/pkl-spring`](https://github.com/apple/pkl-spring)
`spring-boot` sample (which is Gradle-only) using the two prototype components in this repo:

- **`pkl-spring-boot-starter`** — a single dependency that pulls in the whole integration.
- **`pkl-maven-plugin`** — a `generate-java` goal that generates the config classes.

> Build and install those two first: from the repo root run `mvn install`.

## How it works

1. **Code generation (build time).** `pkl-maven-plugin` turns the schema
   [`AppConfig.pkl`](src/main/resources/AppConfig.pkl) into a Java class
   `samples.boot.AppConfig` annotated with `@ConfigurationProperties`, and adds it as a
   compile source root.
2. **Property loading (runtime).** `pkl-spring` (via the starter) registers a
   `PropertySourceLoader` that reads [`application.pkl`](src/main/resources/application.pkl)
   through Spring Boot's standard config mechanism — where `application.yml` would be read.

Spring binds the loaded properties onto the generated class and injects it like any other
bean (see [`Server.java`](src/main/java/samples/boot/Server.java)).

## Run it

```bash
mvn spring-boot:run
```

Expected output:

```
Server {
  endpoints = [Endpoint {
    name = endpoint1
    port = 1234
  }, Endpoint {
    name = endpoint2
    port = 5678
  }]
}
```

## What the prototypes remove

Without the plugin and starter, a Maven user must hand-wire codegen with `exec-maven-plugin`
+ `build-helper-maven-plugin`, run it in an isolated JVM to dodge a GraalVM Truffle
classloader clash, and pin the Pkl runtime version manually. That baseline is documented in
[`../docs/manual-setup.md`](../docs/manual-setup.md). The plugin and starter collapse all of
it to one dependency and one plugin execution.
