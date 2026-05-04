# Final Check Notes

This package has been statically checked after the Meteor/Orbit classpath fix.

Checked items:

- `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are present.
- `meteor-client-1.21.11-63.jar` is present.
- `baritone-meteor-1.21.11.jar` is present.
- `orbit-0.2.4.jar` is present.
- Java source braces are balanced.
- No old `1.21.4` references remain in source/config.
- `ZCoreCat` is a standalone file.
- Categories are registered in `ZCore#onRegisterCategories()`.

Build on Windows:

```bat
gradlew.bat clean build --warning-mode all
```
