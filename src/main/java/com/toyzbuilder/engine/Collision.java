package com.toyzbuilder.engine;

import com.toyzbuilder.world.Entity;
import com.toyzbuilder.world.StructureGenerator;
import com.toyzbuilder.world.WorldGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared collision: continuous heightfield + solid AABBs.
 * Two solid lists: editor-placed entity solids and structure solids
 * (building / dungeon walls, floors, ceilings). All AABBs are two-sided
 * (block movement from either face).
 * No OpenGL — pure world queries for player / future pieces.
 */
public final class Collision {

    /** Axis-aligned box in world space. */
    public static final class AABB {
        public float minX, maxX, minY, maxY, minZ, maxZ;

        public AABB() {}

        public AABB(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
            set(minX, maxX, minY, maxY, minZ, maxZ);
        }

        public void set(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
            this.minX = minX; this.maxX = maxX;
            this.minY = minY; this.maxY = maxY;
            this.minZ = minZ; this.maxZ = maxZ;
        }

        public static AABB fromCenterSize(float cx, float cy, float cz, float sx, float sy, float sz) {
            float hx = sx * 0.5f, hy = sy * 0.5f, hz = sz * 0.5f;
            return new AABB(cx - hx, cx + hx, cy - hy, cy + hy, cz - hz, cz + hz);
        }

        public boolean intersects(AABB o) {
            return minX < o.maxX && maxX > o.minX
                    && minY < o.maxY && maxY > o.minY
                    && minZ < o.maxZ && maxZ > o.minZ;
        }
    }

    private WorldGenerator.Result terrain;
    private final List<AABB> solids = new ArrayList<>();
    private final List<AABB> structureSolids = new ArrayList<>();
    private boolean debugDraw = false;

    public void setTerrain(WorldGenerator.Result terrain) {
        this.terrain = terrain;
    }

    public WorldGenerator.Result getTerrain() { return terrain; }

    public void clearSolids() {
        solids.clear();
        structureSolids.clear();
    }

    public void addSolid(AABB box) { solids.add(box); }

    public List<AABB> getSolids() { return solids; }
    public List<AABB> getStructureSolids() { return structureSolids; }

    public boolean isDebugDraw() { return debugDraw; }
    public void setDebugDraw(boolean on) { debugDraw = on; }
    public void toggleDebugDraw() { debugDraw = !debugDraw; }

    public float groundHeight(float x, float z) {
        if (terrain == null) return 0f;
        return WorldGenerator.sampleHeight(terrain, x, z);
    }

    /**
     * Rebuild solid AABBs from generated structure parts.
     * Structure yaw is limited to 90-degree steps, so a rotated part
     * simply swaps its X/Z extents and stays axis-aligned.
     */
    public void rebuildFromStructures(List<StructureGenerator.Part> parts) {
        structureSolids.clear();
        if (parts == null) return;
        for (StructureGenerator.Part p : parts) {
            if (p == null || !p.solid) continue;
            float sx = p.sx, sz = p.sz;
            float yaw = ((p.yaw % 360f) + 360f) % 360f;
            if ((yaw > 89f && yaw < 91f) || (yaw > 269f && yaw < 271f)) {
                float t = sx; sx = sz; sz = t;
            }
            structureSolids.add(AABB.fromCenterSize(p.x, p.y, p.z, sx, p.sy, sz));
        }
    }

    /**
     * Rebuild solid AABBs from placed editor entities (solid categories only).
     * Structure solids are kept — editing does not un-build the world.
     */
    public void rebuildFromEntities(List<Entity> entities) {
        solids.clear();
        if (entities == null) return;
        for (Entity e : entities) {
            if (e == null || !e.solid) continue;
            PieceCatalog.Def d = PieceCatalog.get(e.category);
            float[] sz = Editor.orientedSize(d, e.yaw);
            float cy = e.y + d.sy * 0.5f;
            solids.add(AABB.fromCenterSize(e.x, cy, e.z, sz[0], sz[1], sz[2]));
        }
    }

    /**
     * Move an AABB body with horizontal resolution then vertical (gravity/jump).
     * @param body mutable player box (feet-based: minY = feet)
     * @return true if standing on ground or a solid top after move
     */
    public boolean moveBody(AABB body, float dx, float dy, float dz, float stepUp) {
        // --- X ---
        body.minX += dx;
        body.maxX += dx;
        resolveAxis(body, 0);
        // --- Z ---
        body.minZ += dz;
        body.maxZ += dz;
        resolveAxis(body, 2);
        // --- Y ---
        body.minY += dy;
        body.maxY += dy;
        boolean onSolid = resolveAxisY(body);

        // Terrain floor under feet (sample center XZ)
        float cx = (body.minX + body.maxX) * 0.5f;
        float cz = (body.minZ + body.maxZ) * 0.5f;
        float ground = groundHeight(cx, cz);
        boolean onTerrain = false;
        if (body.minY <= ground) {
            float raise = ground - body.minY;
            body.minY += raise;
            body.maxY += raise;
            onTerrain = true;
        }

        return onTerrain || onSolid;
    }

    /** Resolve X (axis=0) or Z (axis=2) against both solid lists. */
    private void resolveAxis(AABB body, int axis) {
        resolveAxisList(body, axis, solids);
        resolveAxisList(body, axis, structureSolids);
    }

    private void resolveAxisList(AABB body, int axis, List<AABB> list) {
        for (AABB s : list) {
            if (!body.intersects(s)) continue;
            if (axis == 0) {
                float overlapL = body.maxX - s.minX;
                float overlapR = s.maxX - body.minX;
                if (overlapL < overlapR) {
                    body.minX -= overlapL;
                    body.maxX -= overlapL;
                } else {
                    body.minX += overlapR;
                    body.maxX += overlapR;
                }
            } else {
                float overlapN = body.maxZ - s.minZ;
                float overlapF = s.maxZ - body.minZ;
                if (overlapN < overlapF) {
                    body.minZ -= overlapN;
                    body.maxZ -= overlapN;
                } else {
                    body.minZ += overlapF;
                    body.maxZ += overlapF;
                }
            }
        }
    }

    /** @return true if landed on a solid top */
    private boolean resolveAxisY(AABB body) {
        boolean landed = resolveAxisYList(body, solids);
        landed |= resolveAxisYList(body, structureSolids);
        return landed;
    }

    private boolean resolveAxisYList(AABB body, List<AABB> list) {
        boolean landed = false;
        for (AABB s : list) {
            if (!body.intersects(s)) continue;
            float overlapDown = body.maxY - s.minY;
            float overlapUp = s.maxY - body.minY;
            if (overlapUp < overlapDown) {
                body.minY += overlapUp;
                body.maxY += overlapUp;
                landed = true;
            } else {
                body.minY -= overlapDown;
                body.maxY -= overlapDown;
            }
        }
        return landed;
    }

    /**
     * Build line-list mesh data for debug: 12 edges × 2 verts × 3 floats.
     * Returns pos-only interleaved floats for all solids (entity + structure).
     */
    public float[] buildDebugLines() {
        int n = solids.size() + structureSolids.size();
        float[] out = new float[n * 12 * 2 * 3];
        int o = 0;
        for (AABB b : solids) o = boxEdges(out, o, b);
        for (AABB b : structureSolids) o = boxEdges(out, o, b);
        return out;
    }

    private static int boxEdges(float[] out, int o, AABB b) {
        o = edge(out, o, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ);
        o = edge(out, o, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
        o = edge(out, o, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        o = edge(out, o, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ);
        o = edge(out, o, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ);
        o = edge(out, o, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        o = edge(out, o, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        o = edge(out, o, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        o = edge(out, o, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ);
        o = edge(out, o, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ);
        o = edge(out, o, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ);
        o = edge(out, o, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ);
        return o;
    }

    private static int edge(float[] out, int o, float x0, float y0, float z0, float x1, float y1, float z1) {
        out[o++] = x0; out[o++] = y0; out[o++] = z0;
        out[o++] = x1; out[o++] = y1; out[o++] = z1;
        return o;
    }
}
