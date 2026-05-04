# Build Error Fixes for 1.21.11

This package fixes the compile error for `package meteordevelopment.orbit does not exist`.

Cause: Meteor Client 1.21.11 contains `META-INF/jars/orbit-0.2.4.jar` as a nested jar, but Gradle/javac does not automatically add that nested jar to the compile classpath when using local `modCompileOnly fileTree(...)` dependencies.

Fixes applied:

- Extracted `orbit-0.2.4.jar` into `libs/orbit-0.2.4.jar`.
- Added `compileOnly fileTree(dir: 'libs', include: ['orbit*.jar'])` to `build.gradle`.
- Added `checkLibs` validation for Meteor, Baritone, and Orbit jars.
- Updated Fabric Loader to `0.18.2` because `meteor-client-1.21.11-63.jar` requires it.

Build on Windows:

```bat
gradlew.bat clean build --warning-mode all
```
