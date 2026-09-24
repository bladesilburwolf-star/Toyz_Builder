#include "map.h"
#include "piece.h"
#include <fstream>
#include <sstream>
#include <algorithm>

// Global map system instance
static MapSystem g_mapSystem;

MapSystem& GetMapSystem() {
    return g_mapSystem;
}

void MapSystem::AddMap(const Map& map) {
    maps.push_back(map);
}

Map* MapSystem::GetCurrentMap() {
    if (currentMapIndex >= 0 && currentMapIndex < (int)maps.size()) {
        return &maps[currentMapIndex];
    }
    return nullptr;
}

const Map* MapSystem::GetCurrentMap() const {
    if (currentMapIndex >= 0 && currentMapIndex < (int)maps.size()) {
        return &maps[currentMapIndex];
    }
    return nullptr;
}

bool MapSystem::LoadMap(const std::string& filename) {
    std::ifstream file(filename);
    if (!file.is_open()) {
        return false;
    }
    
    std::string line;
    Map map;
    
    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#') continue;
        
        std::istringstream iss(line);
        std::string token;
        iss >> token;
        
        if (token == "NAME") {
            std::getline(iss, map.name);
            // Remove leading whitespace
            size_t start = map.name.find_first_not_of(" \t");
            if (start != std::string::npos) {
                map.name = map.name.substr(start);
            }
        } else if (token == "TYPE") {
            int type;
            iss >> type;
            map.type = static_cast<MapType>(type);
        } else if (token == "SPAWN") {
            iss >> map.spawnPoint.x >> map.spawnPoint.y >> map.spawnPoint.z;
        } else if (token == "SKYBOX") {
            std::getline(iss, map.skybox);
            size_t start = map.skybox.find_first_not_of(" \t");
            if (start != std::string::npos) {
                map.skybox = map.skybox.substr(start);
            }
        } else if (token == "AMBIENT_COLOR") {
            iss >> map.ambientColor.r >> map.ambientColor.g >> map.ambientColor.b >> map.ambientColor.a;
        } else if (token == "AMBIENT_INTENSITY") {
            iss >> map.ambientIntensity;
        } else if (token == "HAS_CEILING") {
            iss >> map.hasCeiling;
        } else if (token == "CEILING_HEIGHT") {
            iss >> map.ceilingHeight;
        } else if (token == "PIECE") {
            PlacedPiece piece;
            int type;
            iss >> type >> piece.position.x >> piece.position.y >> piece.position.z
                >> piece.rotationY >> piece.rotationX >> piece.rotationZ;
            piece.type = static_cast<PieceType>(type);
            int color;
            iss >> color;
            piece.color = static_cast<PieceColor>(color);
            int length;
            iss >> length;
            piece.length = static_cast<PieceLength>(length);
            map.pieces.push_back(piece);
        }
    }
    
    // Add to maps and set as current
    maps.push_back(map);
    currentMapIndex = maps.size() - 1;
    return true;
}

bool MapSystem::SaveMap(const std::string& filename) {
    if (currentMapIndex < 0 || currentMapIndex >= (int)maps.size()) {
        return false;
    }
    
    const Map& map = maps[currentMapIndex];
    std::ofstream file(filename);
    if (!file.is_open()) {
        return false;
    }
    
    file << "# Toyz Builder Map File\n";
    file << "NAME " << map.name << "\n";
    file << "TYPE " << (int)map.type << "\n";
    file << "SPAWN " << map.spawnPoint.x << " " << map.spawnPoint.y << " " << map.spawnPoint.z << "\n";
    file << "SKYBOX " << map.skybox << "\n";
    file << "AMBIENT_COLOR " << map.ambientColor.r << " " << map.ambientColor.g << " " << map.ambientColor.b << " " << map.ambientColor.a << "\n";
    file << "AMBIENT_INTENSITY " << map.ambientIntensity << "\n";
    file << "HAS_CEILING " << (map.hasCeiling ? 1 : 0) << "\n";
    file << "CEILING_HEIGHT " << map.ceilingHeight << "\n";
    
    for (const auto& piece : map.pieces) {
        file << "PIECE " << (int)piece.type << " "
             << piece.position.x << " " << piece.position.y << " " << piece.position.z << " "
             << piece.rotationY << " " << piece.rotationX << " " << piece.rotationZ << " "
             << (int)piece.color << " " << (int)piece.length << "\n";
    }
    
    return true;
}

void MapSystem::CreateNewMap(const std::string& name, MapType type) {
    Map map;
    map.name = name;
    map.type = type;
    map.spawnPoint = {0, 1, 0};
    map.skybox = "day1";
    map.ambientColor = Color{40, 44, 52, 255};
    map.ambientIntensity = 0.4f;
    map.hasCeiling = (type == MapType::INDOOR);
    map.ceilingHeight = (type == MapType::INDOOR) ? 10.0f : 0.0f;
    
    maps.push_back(map);
    currentMapIndex = maps.size() - 1;
}

void MapSystem::SwitchToMap(int index) {
    if (index >= 0 && index < (int)maps.size()) {
        currentMapIndex = index;
    }
}

void MapSystem::SwitchToMap(const std::string& name) {
    for (int i = 0; i < (int)maps.size(); i++) {
        if (maps[i].name == name) {
            currentMapIndex = i;
            return;
        }
    }
}

std::vector<std::string> MapSystem::GetMapList() const {
    std::vector<std::string> names;
    for (const auto& map : maps) {
        names.push_back(map.name);
    }
    return names;
}

int MapSystem::GetMapCount() const {
    return maps.size();
}

void MapSystem::CreateDefaultMaps() {
    // Outdoor forest map
    Map outdoor;
    outdoor.name = "Forest Map";
    outdoor.type = MapType::OUTDOOR;
    outdoor.spawnPoint = {0, 1, 0};
    outdoor.skybox = "day1";
    outdoor.ambientColor = Color{40, 44, 52, 255};
    outdoor.ambientIntensity = 0.4f;
    outdoor.hasCeiling = false;
    outdoor.ceilingHeight = 0.0f;
    
    // Add some starter pieces
    {
        PlacedPiece p; p.type = PieceType::StraightLog; p.position = {0, 0.25f, 0}; p.rotationY = 0; outdoor.pieces.push_back(p);
        PlacedPiece p2; p2.type = PieceType::StraightLog; p2.position = {1, 0.25f, 0}; p2.rotationY = 90; outdoor.pieces.push_back(p2);
        PlacedPiece p3; p3.type = PieceType::MagnetixBall; p3.position = {0.5f, 0.5f, 0.5f}; p3.color = PieceColor::White; outdoor.pieces.push_back(p3);
    }
    
    maps.push_back(outdoor);
    
    // Indoor map
    Map indoor;
    indoor.name = "House Interior";
    indoor.type = MapType::INDOOR;
    indoor.spawnPoint = {0, 1, 0};
    indoor.skybox = "overcast1";
    indoor.ambientColor = Color{80, 60, 40, 255};
    indoor.ambientIntensity = 0.6f;
    indoor.hasCeiling = true;
    indoor.ceilingHeight = 5.0f;
    
    // Add some starter pieces for indoor
    {
        PlacedPiece p; p.type = PieceType::StraightLog; p.position = {0, 0.25f, 0}; p.rotationY = 0; indoor.pieces.push_back(p);
        PlacedPiece p2; p2.type = PieceType::StraightLog; p2.position = {-2, 0.25f, 0}; p2.rotationY = 90; indoor.pieces.push_back(p2);
        PlacedPiece p3; p3.type = PieceType::StraightLog; p3.position = {2, 0.25f, 0}; p3.rotationY = 90; indoor.pieces.push_back(p3);
        PlacedPiece p4; p4.type = PieceType::StraightLog; p4.position = {0, 0.25f, -2}; p4.rotationY = 0; indoor.pieces.push_back(p4);
    }
    
    maps.push_back(indoor);
    
    // Cave map
    Map cave;
    cave.name = "Underground Cave";
    cave.type = MapType::CAVE;
    cave.spawnPoint = {0, 1, 0};
    cave.skybox = "night1";
    cave.ambientColor = Color{20, 20, 30, 255};
    cave.ambientIntensity = 0.3f;
    cave.hasCeiling = true;
    cave.ceilingHeight = 8.0f;
    
    // Add some starter pieces for cave
    {
        PlacedPiece p; p.type = PieceType::MagnetixBall; p.position = {0, 0.5f, 0}; p.color = PieceColor::White; cave.pieces.push_back(p);
        PlacedPiece p2; p2.type = PieceType::MagnetixRod; p2.position = {1, 0.5f, 0}; p2.color = PieceColor::Red; cave.pieces.push_back(p2);
    }
    
    maps.push_back(cave);
    
    // Set first map as current
    currentMapIndex = 0;
}
