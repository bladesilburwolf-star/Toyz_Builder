#include "weather.h"
#include "raymath.h"
#include <cstdlib>
#include <cstdio>
#include <cmath>

namespace {
    Color LerpColor(Color a, Color b, float t) {
        t = Clamp(t, 0.0f, 1.0f);
        return Color{
            (unsigned char)Lerp((float)a.r, (float)b.r, t),
            (unsigned char)Lerp((float)a.g, (float)b.g, t),
            (unsigned char)Lerp((float)a.b, (float)b.b, t),
            255
        };
    }

    float RandRange(float lo, float hi) {
        return lo + (hi - lo) * (float)GetRandomValue(0, 10000) / 10000.0f;
    }

    Color ApplyWeatherTint(Color base, WeatherType type) {
        switch (type) {
            case WeatherType::Overcast: {
                unsigned char g = (unsigned char)((base.r + base.g + base.b) / 3);
                return LerpColor(base, Color{g, g, g, 255}, 0.55f);
            }
            case WeatherType::Rain:
                return LerpColor(base, Color{70, 80, 95, 255}, 0.55f);
            case WeatherType::Storm:
                return LerpColor(base, Color{35, 38, 50, 255}, 0.78f);
            case WeatherType::Snow:
                return LerpColor(base, Color{212, 222, 232, 255}, 0.40f);
            default:
                return base;
        }
    }

    struct SkyKeyframe { float hour; Color color; };
    const SkyKeyframe kSkyKeys[] = {
        {0.0f,  {10, 12, 30, 255}},
        {5.0f,  {20, 24, 55, 255}},
        {6.5f,  {255, 150, 110, 255}},
        {8.0f,  {135, 206, 235, 255}},
        {12.0f, {140, 210, 240, 255}},
        {17.0f, {255, 170, 110, 255}},
        {19.0f, {90, 70, 110, 255}},
        {21.0f, {25, 28, 60, 255}},
        {24.0f, {10, 12, 30, 255}},
    };
    const int kSkyKeyCount = sizeof(kSkyKeys) / sizeof(kSkyKeys[0]);
}

void InitWeather(WeatherState& weather) {
    weather.rain.clear();
    weather.snow.clear();
    weather.rain.reserve(400);
    weather.snow.reserve(250);
    for (int i = 0; i < 400; i++) {
        RainDrop d;
        d.pos = { RandRange(-20, 20), RandRange(0, 20), RandRange(-20, 20) };
        d.speed = RandRange(14.0f, 22.0f);
        weather.rain.push_back(d);
    }
    for (int i = 0; i < 250; i++) {
        SnowFlake s;
        s.pos = { RandRange(-20, 20), RandRange(0, 20), RandRange(-20, 20) };
        s.speed = RandRange(1.2f, 2.6f);
        s.drift = RandRange(0.3f, 1.0f);
        s.phase = RandRange(0.0f, 6.28f);
        weather.snow.push_back(s);
    }
    weather.nextChangeIn = RandRange(60.0f, 150.0f);
}

void UpdateClock(WorldClock& clock, float dt) {
    if (clock.paused) return;
    float hoursPerSecond = 24.0f / (clock.dayLengthMinutes * 60.0f);
    clock.timeOfDay += dt * hoursPerSecond;
    while (clock.timeOfDay >= 24.0f) {
        clock.timeOfDay -= 24.0f;
        clock.dayCount++;
    }
}

void UpdateWeather(WeatherState& weather, WorldClock& clock, float dt, Vector3 playerPos) {
    if (weather.autoCycle) {
        weather.changeTimer += dt;
        if (weather.changeTimer >= weather.nextChangeIn) {
            weather.changeTimer = 0.0f;
            weather.nextChangeIn = RandRange(90.0f, 240.0f);
            // Weighted toward Clear so it doesn't storm constantly.
            int roll = GetRandomValue(0, 99);
            if (roll < 45) weather.type = WeatherType::Clear;
            else if (roll < 65) weather.type = WeatherType::Overcast;
            else if (roll < 82) weather.type = WeatherType::Rain;
            else if (roll < 92) weather.type = WeatherType::Storm;
            else weather.type = WeatherType::Snow;
        }
    }

    bool raining = (weather.type == WeatherType::Rain || weather.type == WeatherType::Storm);
    if (raining) {
        for (auto& d : weather.rain) {
            d.pos.y -= d.speed * dt;
            if (d.pos.y < playerPos.y - 2.0f) {
                d.pos = { playerPos.x + RandRange(-20, 20), playerPos.y + RandRange(14, 20), playerPos.z + RandRange(-20, 20) };
            }
        }
    }
    if (weather.type == WeatherType::Snow) {
        for (auto& s : weather.snow) {
            s.phase += dt * 1.5f;
            s.pos.y -= s.speed * dt;
            s.pos.x += sinf(s.phase) * s.drift * dt;
            if (s.pos.y < playerPos.y - 2.0f) {
                s.pos = { playerPos.x + RandRange(-20, 20), playerPos.y + RandRange(14, 20), playerPos.z + RandRange(-20, 20) };
            }
        }
    }

    if (weather.type == WeatherType::Storm) {
        weather.lightningTimer -= dt;
        if (weather.lightningTimer <= 0.0f) {
            weather.lightningTimer = RandRange(4.0f, 14.0f);
            weather.lightningFlash = 1.0f;
        }
    }
    weather.lightningFlash = fmaxf(0.0f, weather.lightningFlash - dt * 2.2f);
}

Color GetSkyColor(const WorldClock& clock, const WeatherState& weather) {
    float t = clock.timeOfDay;
    for (int i = 0; i < kSkyKeyCount - 1; i++) {
        if (t >= kSkyKeys[i].hour && t <= kSkyKeys[i + 1].hour) {
            float f = (t - kSkyKeys[i].hour) / (kSkyKeys[i + 1].hour - kSkyKeys[i].hour);
            Color base = LerpColor(kSkyKeys[i].color, kSkyKeys[i + 1].color, f);
            Color tinted = ApplyWeatherTint(base, weather.type);
            if (weather.lightningFlash > 0.0f) tinted = LerpColor(tinted, WHITE, weather.lightningFlash * 0.85f);
            return tinted;
        }
    }
    return ApplyWeatherTint(kSkyKeys[0].color, weather.type);
}

float GetAmbientBrightness(const WorldClock& clock, const WeatherState& weather) {
    // Brightness curve: full daylight ~0.8-20h, dim toward night, floor at night.
    float t = clock.timeOfDay;
    float b;
    if (t < 5.0f) b = 0.32f;
    else if (t < 7.0f) b = Lerp(0.32f, 1.0f, (t - 5.0f) / 2.0f);
    else if (t < 18.0f) b = 1.0f;
    else if (t < 20.0f) b = Lerp(1.0f, 0.32f, (t - 18.0f) / 2.0f);
    else b = 0.32f;

    switch (weather.type) {
        case WeatherType::Overcast: b *= 0.85f; break;
        case WeatherType::Rain:     b *= 0.75f; break;
        case WeatherType::Storm:    b *= 0.55f; break;
        case WeatherType::Snow:     b *= 0.90f; break;
        default: break;
    }
    if (weather.lightningFlash > 0.0f) b = fminf(1.15f, b + weather.lightningFlash * 0.6f);
    return b;
}

Color GetAmbientTint(const WorldClock& clock, const WeatherState& weather) {
    float b = GetAmbientBrightness(clock, weather);
    unsigned char v = (unsigned char)Clamp(b * 255.0f, 0.0f, 255.0f);
    return Color{v, v, v, 255};
}

void DrawWeatherParticles(const WeatherState& weather, Vector3 playerPos) {
    (void)playerPos;
    if (weather.type == WeatherType::Rain || weather.type == WeatherType::Storm) {
        Color rainCol = Fade(Color{170, 190, 210, 255}, 0.6f);
        for (const auto& d : weather.rain) {
            DrawLine3D(d.pos, Vector3{d.pos.x, d.pos.y - 0.5f, d.pos.z}, rainCol);
        }
    } else if (weather.type == WeatherType::Snow) {
        for (const auto& s : weather.snow) {
            DrawSphere(s.pos, 0.045f, Fade(WHITE, 0.85f));
        }
    }
}

void DrawWeatherOverlay(const WeatherState& weather, int screenW, int screenH) {
    if (weather.lightningFlash > 0.0f) {
        DrawRectangle(0, 0, screenW, screenH, Fade(WHITE, weather.lightningFlash * 0.5f));
    }
}

const char* GetTimeString(const WorldClock& clock) {
    static char buf[16];
    int totalMinutes = (int)(clock.timeOfDay * 60.0f) % (24 * 60);
    int h24 = totalMinutes / 60;
    int m = totalMinutes % 60;
    int h12 = h24 % 12; if (h12 == 0) h12 = 12;
    const char* ampm = (h24 < 12) ? "AM" : "PM";
    snprintf(buf, sizeof(buf), "%d:%02d %s", h12, m, ampm);
    return buf;
}

const char* GetWeatherName(WeatherType t) {
    switch (t) {
        case WeatherType::Clear:    return "Clear";
        case WeatherType::Overcast: return "Overcast";
        case WeatherType::Rain:     return "Rain";
        case WeatherType::Storm:    return "Storm";
        case WeatherType::Snow:     return "Snow";
    }
    return "?";
}
