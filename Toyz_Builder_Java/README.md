# Toyz Builder Engine — Java (Jaylib)

Separate line from the C/Raylib desktop build. Intended path toward **Android OpenJDK**.

## Requirements
- JDK 17 or newer (`javac` on PATH)
- Windows: use `gradlew.bat`  |  Linux/macOS: `./gradlew`

## Build / run
```bat
compile_and_run.bat build
compile_and_run.bat
```
or:
```
./gradlew compileJava
./gradlew run
```

Optional: copy `assets/textures/` next to the working directory for bark/metal/grass textures.

## Jaylib 6 notes (fixes applied)
- `Texture2D` → `Texture`, `RenderTexture2D` → `RenderTexture`
- `Ray.position()` conflicts with JavaCPP `Pointer.position(long)` → use `Ray._position()`
- `TextFormat(fmt, args...)` is not varargs in Jaylib → use `String.format`

## Status
Desktop compile verified (Gradle `compileJava` SUCCESS).
