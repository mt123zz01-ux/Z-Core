# Mobile build guide

Use this project with GitHub Actions if you only have a phone.

## Steps

1. Create a new GitHub repository.
2. Upload the contents of this `Z-Core-fixed` folder to the repository.
3. Open the `Actions` tab.
4. Select `Build Z-Core`.
5. Tap `Run workflow`.
6. After the run finishes, open the run page.
7. Download the artifact named `Z-Core-build-output`.
8. The compiled mod jar is inside the downloaded artifact zip under `build/libs/`.

## Notes

- The workflow uses Java 21.
- It runs `./gradlew clean build --stacktrace`.
- If the build fails, open the failed run and copy the `compileJava` error text.
