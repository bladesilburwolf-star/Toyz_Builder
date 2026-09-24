#pragma once
#include "raylib.h"
#include <vector>

enum class WeatherType { Clear, Overcast, Rain, Storm, Snow };

struct RainDrop { Vector3 pos; float speed; };
struct SnowFlake { Vector3 pos; float speed; float drift; float phase; };

// In-game clock. dayLengthMinutes is REAL minutes per full 24h in-game day.
struct WorldClock {
    float timeOfDay = 8.0f;         // hours, 0..24
    float dayLengthMinutes = 20.0f;
    bool paused = false;
    int dayCount = 1;
};

struct WeatherState {
    WeatherType type = WeatherType::Clear;
    bool autoCycle = true;          // let weather change on its own over time
    float changeTimer = 0.0f;
    float nextChangeIn = 90.0f;
    std::vector<RainDrop> rain;
    std::vector<SnowFlake> snow;
    float lightningTimer = 3.0f;
    float lightningFlash = 0.0f;    // 0..1, screen-flash intensity this frame
};

void InitWeather(WeatherState& weather);
void UpdateClock(WorldClock& clock, float dt);
void UpdateWeather(WeatherState& weather, WorldClock& clock, float dt, Vector3 playerPos);

// Sky color blended across dawn/day/dusk/night keyframes, then tinted by
// the current weather (overcast/rain/storm/snow each shift it differently).
Color GetSkyColor(const WorldClock& clock, const WeatherState& weather);

// 0..1 multiplier: how bright the world is right now (night + storms are
// darkest). Multiply this into a WHITE tint and pass it to DrawForestTerrain
// / DrawPlacedPiece so the whole scene actually darkens at night, not just
// the sky color.
float GetAmbientBrightness(const WorldClock& clock, const WeatherState& weather);
Color GetAmbientTint(const WorldClock& clock, const WeatherState& weather);

// Call inside BeginMode3D/EndMode3D.
void DrawWeatherParticles(const WeatherState& weather, Vector3 playerPos);
// Call in screen space, after EndMode3D (lightning flash overlay).
void DrawWeatherOverlay(const WeatherState& weather, int screenW, int screenH);

const char* GetTimeString(const WorldClock& clock); // "6:42 AM"
const char* GetWeatherName(WeatherType t);
