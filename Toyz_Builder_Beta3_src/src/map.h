#pragma once

#include "raylib.h"
#include "raymath.h"
#include "piece.h"
#include <vector>
#include <string>

// Map types
enum class MapType {
    OUTDOOR,  // Outdoor terrain map
    INDOOR,   // Indoor building map
    CAVE,     // Underground cave map
    COUNT
};

// Map structure for saving/loading maps
struct Map {
    std::string name;
    MapType type;
    std::vector<PlacedPiece> pieces;
    Vector3 spawnPoint;
    std::string skybox; // Skybox texture name
    Color ambientColor;
    float ambientIntensity;
    bool hasCeiling; // For indoor maps
    float ceilingHeight; // For indoor maps
};

// Map system for managing multiple maps
class MapSystem {
public:
    std::vector<Map> maps;
    int currentMapIndex = -1; // -1 = no map loaded
    
    void AddMap(const Map& map);
    Map* GetCurrentMap();
    const Map* GetCurrentMap() const;
    bool LoadMap(const std::string& filename);
    bool SaveMap(const std::string& filename);
    void CreateNewMap(const std::string& name, MapType type);
    void SwitchToMap(int index);
    void SwitchToMap(const std::string& name);
    
    // Create default maps
    void CreateDefaultMaps();
    
    // Helper functions
    std::vector<std::string> GetMapList() const;
    int GetMapCount() const;
};

// Global map system instance
MapSystem& GetMapSystem();
