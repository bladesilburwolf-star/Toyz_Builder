# Toyz Builder Engine — Java v3 (Jaylib)

Separate line from the C/Raylib desktop build. Path toward **Android OpenJDK**.

## Requirements
- JDK 17+ (`javac` on PATH). On Windows, a full JDK — not JRE-only.
- `gradlew.bat` (Windows) or `./gradlew` (Linux/macOS)

## Build / run
```bat
compile_and_run.bat build
compile_and_run.bat
```
```
./gradlew compileJava
./gradlew run
```
Keep `assets/` next to the project (textures + skyboxes).

## Controls (Java v3)
- Title: New World / Continue / Settings / Exit
- ESC pause · E inventory · C camera · M weather cycle
- F11 / Alt+Enter fullscreen

## Jaylib 6 notes
- Texture not Texture2D; RenderTexture not RenderTexture2D
- Ray._position() not Ray.position()
- String.format instead of TextFormat varargs

## Status
`compileJava` SUCCESS. New World regenerates terrain. Exit unload hardened vs Windows heap corruption.
