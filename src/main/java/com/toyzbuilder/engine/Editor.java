package com.toyzbuilder.engine;

import com.toyzbuilder.world.Entity;
import com.toyzbuilder.world.MapFile;
import com.toyzbuilder.world.WorldGenerator;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * In-world builder: piece inventory, snap grid, 90° rotate, place/pick/delete.
 */
public class Editor {

    private final List<Entity> entities = new ArrayList<>();
    private WorldGenerator.Result terrain;
    private boolean active = false;
    private int selectedIndex = 0; // into PieceCatalog.all()
    private int groupIndex = 0;
    private Entity selected = null;
    private String status = "BUILD | LMB place | RMB break | MMB pick | Scroll hotbar | R rotate";

    public float markerX, markerY, markerZ;
    public boolean markerValid = false;
    public float placeYaw = 0f;
    public float snap = 0.5f;

    private boolean leftQueued = false, rightQueued = false, middleQueued = false;

    public boolean isActive() { return active; }

    public void setActive(boolean a) {
        active = a;
        status = a ? "BUILD MODE" : "PLAY MODE";
    }

    public void setTerrain(WorldGenerator.Result t) { this.terrain = t; }
    public List<Entity> getEntities() { return entities; }
    public Entity getSelected() { return selected; }
    public String getStatus() { return status; }
    public float getPlaceYaw() { return placeYaw; }
    public float getSnap() { return snap; }

    public PieceCatalog.Def currentPiece() {
        List<PieceCatalog.Def> all = PieceCatalog.all();
        if (all.isEmpty()) return null;
        selectedIndex = Math.floorMod(selectedIndex, all.size());
        return all.get(selectedIndex);
    }

    public int getSelectedIndex() { return selectedIndex; }

    public void setSelectedIndex(int i) {
        List<PieceCatalog.Def> all = PieceCatalog.all();
        if (all.isEmpty()) return;
        selectedIndex = Math.floorMod(i, all.size());
        status = "Piece: " + currentPiece().label;
    }

    public void cyclePiece(int dir) {
        setSelectedIndex(selectedIndex + dir);
    }

    public void setGroupIndex(int g) {
        PieceCatalog.Group[] groups = PieceCatalog.Group.values();
        groupIndex = Math.floorMod(g, groups.length);
        List<PieceCatalog.Def> list = PieceCatalog.byGroup(groups[groupIndex]);
        if (!list.isEmpty()) {
            selectedIndex = PieceCatalog.indexOf(list.get(0).id);
            status = groups[groupIndex].label + " — " + list.get(0).label;
        }
    }

    public void cycleGroup(int dir) {
        setGroupIndex(groupIndex + dir);
    }

    public int getGroupIndex() { return groupIndex; }

    public void rotatePlace(int steps) {
        placeYaw = (placeYaw + steps * 90f) % 360f;
        if (placeYaw < 0) placeYaw += 360f;
        status = String.format("Yaw %.0f°  snap %.2f", placeYaw, snap);
    }

    public void cycleSnap() {
        if (snap <= 0.26f) snap = 0.5f;
        else if (snap <= 0.51f) snap = 1.0f;
        else if (snap <= 1.01f) snap = 2.0f;
        else snap = 0.25f;
        status = String.format("Snap %.2f", snap);
    }

    public void mouseLeft() { leftQueued = true; }
    public void mouseRight() { rightQueued = true; }
    public void mouseMiddle() { middleQueued = true; }

    public void update(FirstPersonCamera cam) {
        computeMarker(cam);

        if (leftQueued) {
            leftQueued = false;
            if (markerValid) {
                PieceCatalog.Def d = currentPiece();
                Entity e = new Entity(d.id, markerX, markerY, markerZ, d.solid);
                e.yaw = placeYaw;
                entities.add(e);
                selected = e;
                status = "Placed " + d.label + " (" + entities.size() + ")";
            } else {
                status = "No ground under crosshair";
            }
        }
        if (rightQueued) {
            rightQueued = false;
            Entity hit = pick(cam);
            if (hit != null) {
                entities.remove(hit);
                if (selected == hit) selected = null;
                status = "Broke " + hit.category;
            } else {
                status = "Nothing to break";
            }
        }
        if (middleQueued) {
            middleQueued = false;
            Entity hit = pick(cam);
            if (hit != null) {
                selected = hit;
                selectedIndex = PieceCatalog.indexOf(hit.category);
                placeYaw = hit.yaw;
                status = "Picked " + hit.category;
            } else {
                status = "Nothing to pick";
            }
        }
    }

    public void deleteSelected() {
        if (selected != null) {
            entities.remove(selected);
            selected = null;
            status = "Deleted";
        }
    }

    public void duplicateSelected() {
        if (selected != null) {
            Entity c = selected.copy();
            c.x += snap;
            entities.add(c);
            selected = c;
            status = "Duplicated";
        }
    }

    public void save(File f, float px, float py, float pz) {
        try {
            MapFile.save(f, entities, px, py, pz);
            status = "Saved " + entities.size() + " → " + f.getName();
        } catch (Exception ex) {
            status = "Save FAILED: " + ex.getMessage();
        }
    }

    public void load(File f) {
        try {
            List<Entity> loaded = MapFile.load(f);
            entities.clear();
            entities.addAll(loaded);
            selected = null;
            status = "Loaded " + entities.size() + " from " + f.getName();
        } catch (Exception ex) {
            status = "Load FAILED: " + ex.getMessage();
        }
    }

    private void computeMarker(FirstPersonCamera cam) {
        markerValid = false;
        if (terrain == null) return;
        Vector3f front = cam.getFront();
        float ox = cam.getX(), oy = cam.getY(), oz = cam.getZ();
        float px = 0, py = 0, pz = 0;
        for (float t = 0.5f; t < 500f; t += 0.25f) {
            px = ox + front.x * t;
            py = oy + front.y * t;
            pz = oz + front.z * t;
            float h = WorldGenerator.sampleHeight(terrain, px, pz);
            if (py <= h) { markerValid = true; break; }
            if (front.y > 0.01f && py > oy + 400f) break;
        }
        if (markerValid) {
            markerX = snap(px);
            markerZ = snap(pz);
            PieceCatalog.Def d = currentPiece();
            float ground = WorldGenerator.sampleHeight(terrain, markerX, markerZ);
            // stack: if looking at existing solid top nearby, rest on it
            float stackY = ground;
            for (Entity e : entities) {
                PieceCatalog.Def ed = PieceCatalog.get(e.category);
                float[] sz = orientedSize(ed, e.yaw);
                if (Math.abs(e.x - markerX) < sz[0] * 0.5f + 0.05f
                        && Math.abs(e.z - markerZ) < sz[2] * 0.5f + 0.05f) {
                    float top = e.y + ed.sy;
                    if (top > stackY) stackY = top;
                }
            }
            markerY = stackY;
        }
    }

    private float snap(float v) {
        if (snap <= 1e-4f) return v;
        return Math.round(v / snap) * snap;
    }

    static float[] orientedSize(PieceCatalog.Def d, float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y > 45f && y < 135f || y > 225f && y < 315f) {
            return new float[]{d.sz, d.sy, d.sx};
        }
        return new float[]{d.sx, d.sy, d.sz};
    }

    private Entity pick(FirstPersonCamera cam) {
        Vector3f front = cam.getFront();
        float ox = cam.getX(), oy = cam.getY(), oz = cam.getZ();
        Entity best = null;
        float bestT = Float.MAX_VALUE;
        for (Entity e : entities) {
            PieceCatalog.Def d = PieceCatalog.get(e.category);
            float[] sz = orientedSize(d, e.yaw);
            float hx = sz[0] * 0.5f + 0.15f;
            float hy = sz[1] * 0.5f + 0.15f;
            float hz = sz[2] * 0.5f + 0.15f;
            float cy = e.y + d.sy * 0.5f;
            float[] r = slab(ox, front.x, e.x - hx, e.x + hx, 0f, 500f);
            if (r == null) continue;
            r = slab(oy, front.y, cy - hy, cy + hy, r[0], r[1]);
            if (r == null) continue;
            r = slab(oz, front.z, e.z - hz, e.z + hz, r[0], r[1]);
            if (r == null) continue;
            if (r[0] <= r[1] && r[0] < bestT) {
                best = e;
                bestT = r[0];
            }
        }
        return best;
    }

    private static float[] slab(float o, float d, float mn, float mx, float t0, float t1) {
        if (Math.abs(d) < 1e-6f) {
            return (o >= mn && o <= mx) ? new float[]{t0, t1} : null;
        }
        float inv = 1f / d;
        float ta = (mn - o) * inv;
        float tb = (mx - o) * inv;
        if (ta > tb) { float tmp = ta; ta = tb; tb = tmp; }
        float n0 = Math.max(t0, ta);
        float n1 = Math.min(t1, tb);
        return n0 <= n1 ? new float[]{n0, n1} : null;
    }
}
