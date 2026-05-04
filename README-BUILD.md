# Z-Core Build Notes

Requirements:

- Java 21
- Fabric Loader 0.18.2+
- Minecraft 1.21.11
- Meteor Client 1.21.11 jar
- Baritone compatible with Minecraft 1.21.11

The `libs/` folder in this package already includes:

- `meteor-client-1.21.11-63.jar`
- `baritone-meteor-1.21.11.jar`
- `orbit-0.2.4.jar`

Build on Windows:

```bat
gradlew.bat clean build --warning-mode all
```

If Gradle reports missing internet dependencies, run the command again on a machine with internet access so Gradle can download Minecraft, Fabric, Yarn, and Fabric API.
