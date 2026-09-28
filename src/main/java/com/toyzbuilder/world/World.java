package com.toyzbuilder.world;

import com.toyzbuilder.engine.Collision;
import com.toyzbuilder.engine.FirstPersonCamera;
import com.toyzbuilder.engine.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class World {

    private final FirstPersonCamera camera;
    private final PlayerController controller;
    private final Collision collision;
    private WorldGenerator.Result terrain;
    private final List<Warp> warps = new ArrayList<>();
    private float warpCooldown = 0f;

    public World(WorldGenerator.Result terrain) {
        this.terrain = terrain;
        camera = new FirstPersonCamera();
        controller = new PlayerController(camera);
        collision = new Collision();
        collision.setTerrain(terrain);
        controller.setCollision(collision);
    }

    public void setTerrain(WorldGenerator.Result terrain) {
        this.terrain = terrain;
        collision.setTerrain(terrain);
        collision.clearSolids();
        warps.clear();
    }

    /** Wire freshly generated structures: collision solids + warp pads. */
    public void setStructures(List<StructureGenerator.Part> parts, List<Warp> newWarps) {
        collision.rebuildFromStructures(parts);
        setWarps(newWarps);
    }

    public void setWarps(List<Warp> list) {
        warps.clear();
        if (list != null) warps.addAll(list);
        warpCooldown = 1.5f;
    }

    public List<Warp> getWarps() { return warps; }

    public FirstPersonCamera getCamera() { return camera; }
    public PlayerController getController() { return controller; }
    public WorldGenerator.Result getTerrain() { return terrain; }
    public Collision getCollision() { return collision; }

    public void update(long window, float deltaTime) {
        controller.handleMouse(window);
        controller.handleMovement(window, deltaTime);
        updateWarps(deltaTime);
    }

    private void updateWarps(float dt) {
        if (warpCooldown > 0f) {
            warpCooldown -= dt;
            return;
        }
        if (warps.isEmpty()) return;
        var p = controller.getPlayerPos();
        for (Warp w : warps) {
            float dx = p.x - w.x;
            float dz = p.z - w.z;
            if (dx * dx + dz * dz <= w.radius * w.radius
                    && Math.abs(p.y - w.y) < 2.5f) {
                controller.setPlayerPos(w.tx, w.ty, w.tz);
                controller.resyncMouseIfNeeded();
                warpCooldown = 2.5f;
                System.out.println("[Warp] " + w.label);
                break;
            }
        }
    }
}
