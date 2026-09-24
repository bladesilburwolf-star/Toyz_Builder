#pragma once
#include "raylib.h"
#include <string>
#include <vector>
#include <functional>

// Serif System Works palette
namespace SSW {
    inline Color SteelTop()    { return {28, 30, 34, 240}; }
    inline Color SteelBot()    { return {12, 14, 16, 250}; }
    inline Color SteelEdge()   { return {70, 75, 85, 255}; }
    inline Color Phosphor()    { return {80, 255, 120, 255}; }
    inline Color PhosphorDim() { return {40, 160, 80, 255}; }
    inline Color White()       { return {230, 235, 240, 255}; }
    inline Color Danger()      { return {255, 90, 90, 255}; }
    inline Color Panel()       { return {18, 20, 24, 230}; }
}

enum class PauseAction {
    None,
    Resume,
    ToggleOptions,
    SaveMap,
    NewMap,
    ExitGame
};

struct PauseMenuState {
    bool open = false;
    bool showOptionsPanel = false;
    int selected = 0; // keyboard/gamepad highlight
};

// Draw steel panel with phosphor title
void DrawSteelPanel(Rectangle r, const char* title);

// Returns true if mouse clicked inside button this frame
bool DrawSteelButton(Rectangle r, const char* label, bool selected);

// Full pause overlay. Returns action chosen this frame.
PauseAction DrawPauseMenu(PauseMenuState& state, bool hasController);

// Options sub-panel (sensitivity, invert, third person, sky index)
// Mutates the floats/bools passed in. skyNames for sky picker.
void DrawOptionsPanel(Rectangle area,
                      float* mouseSens, float* gamepadSens, float* arrowSens,
                      bool* invertX, bool* invertY, bool* thirdPerson,
                      float* camDist, float* camHeight,
                      int* skyIndex, const std::vector<std::string>& skyNames,
                      int* resIndex, const char** resNames, int resCount,
                      bool* requestApplyRes, bool* requestToggleFs);

// Simple HUD line top-left (phosphor)
void DrawGameHud(const char* mode, const char* mapName, int pieceCount, bool controller);
