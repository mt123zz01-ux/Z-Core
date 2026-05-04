# Z-Core 1.21.11 Upgrade Notes

The project configuration has been updated for Minecraft 1.21.11.

## Main changes

- Minecraft target: `1.21.11`
- Yarn mappings: `1.21.11+build.4`
- Fabric Loader: `0.18.2`
- Fabric API: `0.141.0+1.21.11`
- Loom: `1.14.10`
- Gradle wrapper: `9.2.1`
- The build now checks that local Meteor, Baritone, and Orbit jars are present and valid.

## Required jars

Place these files in `libs/` before building if they are missing:

```text
meteor-client-1.21.11-*.jar
baritone*.jar or baritone-api-fabric-*.jar compatible with Minecraft 1.21.11
orbit-0.2.4.jar
```

Do not rename older jars to 1.21.11. Use binaries built for Minecraft 1.21.11.

## Build

```bat
gradlew.bat clean build --warning-mode all
```
