package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.List;

/**
 * Port of inventory.h / inventory.cpp — Minecraft-style hotbar plus the full
 * inventory grid. The C++ version used ImGui; this is a custom raylib-drawn UI:
 *   - Click a hotbar slot to select it, right-click to clear it.
 *   - Click a piece in the inventory to equip it into the selected hotbar slot.
 *   - Drag a piece icon onto any hotbar slot to assign it there.
 */
public final class Hotbar {

    public static final int SLOT_COUNT = 9;

    public static class HotbarState {
        public int[] slots = { -1, -1, -1, -1, -1, -1, -1, -1, -1 };
        public int selected = 0;

        public int heldPieceIndex() { return slots[selected]; }
        public void setSlot(int i, int v) { slots[i] = v; }
    }

    public static class InventoryState {
        public boolean isOpen = false;
        public int filterCategory = 0; // 0 = All
        public String search = "";
        public boolean searchFocus = false;
        public int sortMode = 0; // 0 = name A-Z, 1 = category
        // Simple drag state replacing ImGui drag-and-drop
        int draggedPieceIndex = -1;
    }

    private static final float SLOT_SIZE = 56f;
    private static final float PADDING = 6f;

    private Hotbar() {}

    /** Which category a piece belongs to: 1 Lincoln Logs, 2 Magnetix, 3 Tech, 4 Lolo. */
    public static int categoryOf(Piece.PieceType t) {
        switch (t) {
            case StraightLog: case LogVert: case NotchedLog: case CornerLog:
            case HalfLog: case LogStub: case Plank: case PlankWide:
            case Roof: case RoofPeak: case Window: case Door: case Sign: case FabricFlag:
                return 1;
            case MagnetixBall: case MagnetixRod: case MagnetixRodVert:
            case MagnetixTriangle: case MagnetixSquare: case MagnetixHex: case MagnetixFlag:
                return 2;
            case TechLight: case TechEngine: case TechPulley:
                return 3;
            case LoloBlock: case LoloPlayer:
                return 4;
            default:
                return 0;
        }
    }

    private static String categoryName(int c) {
        switch (c) {
            case 1: return "Lincoln Logs";
            case 2: return "Magnetix";
            case 3: return "Tech";
            case 4: return "Lolo";
            default: return "All";
        }
    }

    /** Draws one icon (render texture is stored flipped — unflip via negative source height). */
    private static void drawIcon(Piece.PieceDef def, Rectangle dest) {
        Texture tex = def.icon.texture();
        Rectangle src = Helpers.newRectangle(0, 0, tex.width(), -tex.height());
        DrawTexturePro(tex, src, dest, Helpers.newVector2(0, 0), 0f, WHITE);
    }

    /** Screen rect of hotbar slot i — shared by drawing and drop testing. */
    public static Rectangle slotRect(int i) {
        float totalWidth = SLOT_COUNT * (SLOT_SIZE + PADDING) - PADDING;
        float startX = (GetScreenWidth() - totalWidth) * 0.5f;
        float startY = GetScreenHeight() - SLOT_SIZE - 20f;
        return Helpers.newRectangle(startX + i * (SLOT_SIZE + PADDING), startY, SLOT_SIZE, SLOT_SIZE);
    }

    /** Always call once per frame: the persistent bottom hotbar. */
    public static void drawHotbar(HotbarState hotbar, List<Piece.PieceDef> defs) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            Rectangle r = slotRect(i);
            boolean isSelected = (hotbar.selected == i);

            Color border = isSelected ? UI.phosphor()
                                      : Helpers.newColor(70, 75, 85, 255);
            Color bg = Helpers.newColor(18, 20, 24, 220);
            DrawRectangleRec(r, bg);
            DrawRectangleLinesEx(r, isSelected ? 2.5f : 1.5f, border);

            int pieceIdx = hotbar.slots[i];
            if (pieceIdx >= 0 && pieceIdx < defs.size()) {
                drawIcon(defs.get(pieceIdx), r);
            }

            // Slot number label, top-left (1-9)
            DrawText(String.valueOf(i + 1), (int) r.x() + 3, (int) r.y() + 2, 12,
                     Helpers.newColor(255, 255, 255, 200));

            Vector2 m = GetMousePosition();
            if (CheckCollisionPointRec(m, r)) {
                if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) hotbar.selected = i;
                if (IsMouseButtonPressed(MOUSE_BUTTON_RIGHT)) hotbar.slots[i] = -1;
            }
        }
    }

    /** Call only while InventoryState.isOpen: steel + phosphor piece grid. */
    public static void updateFullInventory(InventoryState state, HotbarState hotbar, List<Piece.PieceDef> defs) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        // Dim world behind
        DrawRectangle(0, 0, sw, sh, Helpers.newColor(0, 0, 0, 140));

        float panelW = 640f, panelH = 500f;
        Rectangle panel = Helpers.newRectangle((sw - panelW) * 0.5f, (sh - panelH) * 0.5f - 10f, panelW, panelH);
        UI.drawSteelPanel(panel, "INVENTORY  //  SERIF SYSTEM WORKS");

        float x = panel.x() + 14, y = panel.y() + 36;
        Vector2 m = GetMousePosition();

        DrawText("Click to equip  |  drag to hotbar  |  right-click slot to clear",
                 (int) x, (int) y, 13, UI.phosphorDim());
        y += 20;

        // Search box
        Rectangle searchBox = Helpers.newRectangle(x, y, 280, 26);
        boolean hoverSearch = CheckCollisionPointRec(m, searchBox);
        if (hoverSearch && IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) state.searchFocus = true;
        if (!hoverSearch && IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) state.searchFocus = false;
        DrawRectangleRec(searchBox, Helpers.newColor(12, 14, 16, 255));
        DrawRectangleLinesEx(searchBox, 1.5f, state.searchFocus ? UI.phosphor() : Helpers.newColor(70, 75, 85, 255));
        String searchLabel = state.search.isEmpty() && !state.searchFocus ? "Search pieces..." : state.search;
        DrawText(searchLabel, (int) searchBox.x() + 8, (int) searchBox.y() + 5, 14,
                 state.search.isEmpty() && !state.searchFocus ? UI.phosphorDim() : UI.phosphor());
        if (state.searchFocus) {
            int ch = GetCharPressed();
            while (ch > 0) {
                if (ch >= 32 && ch < 127 && state.search.length() < 24)
                    state.search += (char) ch;
                ch = GetCharPressed();
            }
            if (IsKeyPressed(KEY_BACKSPACE) && !state.search.isEmpty())
                state.search = state.search.substring(0, state.search.length() - 1);
        }

        // Sort toggle
        Rectangle sortBtn = Helpers.newRectangle(x + 290, y, 140, 26);
        String sortLabel = state.sortMode == 0 ? "SORT: NAME" : "SORT: TYPE";
        if (UI.drawSteelButton(sortBtn, sortLabel, false))
            state.sortMode = 1 - state.sortMode;

        // Clear search
        Rectangle clearBtn = Helpers.newRectangle(x + 438, y, 100, 26);
        if (UI.drawSteelButton(clearBtn, "CLEAR", false)) {
            state.search = "";
            state.searchFocus = false;
        }
        y += 34;

        // Category tabs
        for (int c = 0; c <= 4; c++) {
            Rectangle tab = Helpers.newRectangle(x + c * 120, y, 114, 28);
            boolean active = state.filterCategory == c;
            if (UI.drawSteelButton(tab, categoryName(c), active)) state.filterCategory = c;
        }
        y += 36;

        // Build filtered index list
        java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
        String q = state.search.toLowerCase();
        for (int i = 0; i < defs.size(); i++) {
            Piece.PieceDef def = defs.get(i);
            int cat = def.category > 0 ? def.category : categoryOf(def.type);
            if (state.filterCategory != 0 && cat != state.filterCategory) continue;
            if (!q.isEmpty() && !def.name.toLowerCase().contains(q)) continue;
            indices.add(i);
        }
        if (state.sortMode == 0) {
            indices.sort((a, b) -> defs.get(a).name.compareToIgnoreCase(defs.get(b).name));
        } else {
            indices.sort((a, b) -> {
                int ca = defs.get(a).category > 0 ? defs.get(a).category : categoryOf(defs.get(a).type);
                int cb = defs.get(b).category > 0 ? defs.get(b).category : categoryOf(defs.get(b).type);
                if (ca != cb) return Integer.compare(ca, cb);
                return defs.get(a).name.compareToIgnoreCase(defs.get(b).name);
            });
        }

        // Scrollable-ish grid region
        float gridTop = y;
        float gridBottom = panel.y() + panelH - 36;
        int columns = 6;
        float cellW = 100f, cellH = 88f;
        int col = 0;
        float gx = x, gy = gridTop;

        BeginScissorMode((int) x, (int) gridTop, (int) (panelW - 28), (int) (gridBottom - gridTop));
        for (int idx : indices) {
            Piece.PieceDef def = defs.get(idx);
            Rectangle cellRect = Helpers.newRectangle(gx + col * cellW, gy, 72, 56);
            // Steel cell
            DrawRectangleRec(cellRect, Helpers.newColor(22, 24, 28, 255));
            DrawRectangle((int) cellRect.x(), (int) cellRect.y(), (int) cellRect.width(), 2,
                          Helpers.newColor(55, 60, 70, 255));
            boolean hover = CheckCollisionPointRec(m, cellRect);
            DrawRectangleLinesEx(cellRect, hover ? 2f : 1f, hover ? UI.phosphor() : Helpers.newColor(70, 75, 85, 255));
            drawIcon(def, cellRect);

            if (hover) {
                if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT))
                    hotbar.slots[hotbar.selected] = idx;
                if (IsMouseButtonDown(MOUSE_BUTTON_LEFT))
                    state.draggedPieceIndex = idx;
            }

            // Short phosphor label under icon
            String label = def.name;
            if (label.length() > 12) label = label.substring(0, 11) + "…";
            int tw = MeasureText(label, 11);
            DrawText(label,
                     (int) (cellRect.x() + (cellRect.width() - tw) * 0.5f),
                     (int) (cellRect.y() + cellRect.height() + 3), 11, UI.phosphorDim());

            col++;
            if (col >= columns) {
                col = 0;
                gy += cellH;
            }
        }
        EndScissorMode();

        DrawText(indices.size() + " pieces", (int) x, (int) (panel.y() + panelH - 28), 13, UI.phosphorDim());
        DrawText("E close", (int) (panel.x() + panelW - 80), (int) (panel.y() + panelH - 28), 13, UI.phosphor());

        // Drag ghost
        if (state.draggedPieceIndex >= 0) {
            if (IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
                Rectangle dragRect = Helpers.newRectangle(m.x() - 28, m.y() - 28, 56, 56);
                DrawRectangleRec(dragRect, Helpers.newColor(18, 20, 24, 220));
                DrawRectangleLinesEx(dragRect, 2f, UI.phosphor());
                drawIcon(defs.get(state.draggedPieceIndex), dragRect);
            } else {
                for (int i = 0; i < SLOT_COUNT; i++) {
                    if (CheckCollisionPointRec(m, slotRect(i))) {
                        hotbar.slots[i] = state.draggedPieceIndex;
                        break;
                    }
                }
                state.draggedPieceIndex = -1;
            }
        }
    }
}