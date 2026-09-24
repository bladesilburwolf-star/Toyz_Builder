cmake_minimum_required(VERSION 3.16)
project(ToyzBuilder_Engine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# --- raylib 5.5 ---
FetchContent_Declare(
    raylib
    GIT_REPOSITORY https://github.com/raysan5/raylib.git
    GIT_TAG 5.5
)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_GAMES OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(raylib)

# --- Dear ImGui 1.92.7 ---
FetchContent_Declare(
    imgui
    GIT_REPOSITORY https://github.com/ocornut/imgui.git
    GIT_TAG v1.92.7
)
FetchContent_MakeAvailable(imgui)

add_library(imgui STATIC
    ${imgui_SOURCE_DIR}/imgui.cpp
    ${imgui_SOURCE_DIR}/imgui_draw.cpp
    ${imgui_SOURCE_DIR}/imgui_tables.cpp
    ${imgui_SOURCE_DIR}/imgui_widgets.cpp
    ${imgui_SOURCE_DIR}/imgui_demo.cpp
)
target_include_directories(imgui PUBLIC ${imgui_SOURCE_DIR})

# --- rlImGui ---
FetchContent_Declare(
    rlimgui
    GIT_REPOSITORY https://github.com/raylib-extras/rlImGui.git
    GIT_TAG Raylib_5_5
)
FetchContent_MakeAvailable(rlimgui)

add_library(rlImGui STATIC ${rlimgui_SOURCE_DIR}/rlImGui.cpp)
target_include_directories(rlImGui PUBLIC ${rlimgui_SOURCE_DIR})
target_link_libraries(rlImGui PUBLIC raylib imgui)

# --- ToyzBuilder_Engine v5 Sources ---
# Lincoln Logs + Magnetix (200pc + 60pc Xtreme + Castle) + Lolo + Tech
set(TOYZ_SOURCES
    src/main.cpp
    src/piece.cpp
    src/inventory.cpp
    src/terrain.cpp
    src/player.cpp
    src/map.cpp
    src/ui.cpp
    src/weather.cpp
)

# Main executable (was LincolnLogs3D, now ToyzBuilder)
add_executable(ToyzBuilder_Engine ${TOYZ_SOURCES})
target_link_libraries(ToyzBuilder_Engine PRIVATE raylib rlImGui)
target_include_directories(ToyzBuilder_Engine PRIVATE src)

# Keep old name as alias for your existing scripts
add_executable(LincolnLogs3D ${TOYZ_SOURCES})
target_link_libraries(LincolnLogs3D PRIVATE raylib rlImGui)
target_include_directories(LincolnLogs3D PRIVATE src)

if (WIN32)
    target_link_libraries(ToyzBuilder_Engine PRIVATE winmm)
    target_link_libraries(LincolnLogs3D PRIVATE winmm)
endif()

# --- Copy runtime assets next to the built executable ---
# piece.cpp/terrain.cpp load textures via relative paths like
# "assets/textures/bark1.png", and map.cpp writes to "maps/<name>.map".
# Both are relative to the CURRENT WORKING DIRECTORY at runtime, which for a
# normal `cmake --build` + run workflow is the executable's own folder
# (build/ or build/Release/), NOT the source tree - so without this, every
# texture/skybox load silently falls back to a solid color and SaveMap()
# silently returns false because maps/ doesn't exist yet.
foreach(_target ToyzBuilder_Engine LincolnLogs3D)
    add_custom_command(TARGET ${_target} POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_directory
            ${CMAKE_SOURCE_DIR}/assets
            $<TARGET_FILE_DIR:${_target}>/assets
        COMMAND ${CMAKE_COMMAND} -E make_directory
            $<TARGET_FILE_DIR:${_target}>/maps
    )
endforeach()

# --- Mobile / Touch defines ---
# Enable raylib touch gestures
target_compile_definitions(ToyzBuilder_Engine PRIVATE SUPPORT_TOUCH_GESTURES=1)
target_compile_definitions(LincolnLogs3D PRIVATE SUPPORT_TOUCH_GESTURES=1)

# --- Emscripten / Web target (for your web prototype) ---
if (EMSCRIPTEN)
    set(CMAKE_EXECUTABLE_SUFFIX ".html")
    target_link_options(ToyzBuilder_Engine PRIVATE
        -sUSE_GLFW=3
        -sALLOW_MEMORY_GROWTH=1
        -sASYNCIFY
        --shell-file ${CMAKE_SOURCE_DIR}/web/shell.html
    )
endif()

# --- Install ---
install(TARGETS ToyzBuilder_Engine DESTINATION bin)
