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
        // Simple drag state replacing ImGui drag-and-drop
        int draggedPieceIndex = -1;
    }

    private static final float SLOT_SIZE = 56f;
    private static final float PADDING = 6f;

    private Hotbar() {}

    /** Which category a piece belongs to: 1 Lincoln Logs, 2 Magnetix, 3 Tech, 4 Lolo. */
    public static int categoryOf(Piece.PieceType t) {
        switch (t) {
            case StraightLog: case NotchedLog: case CornerLog: case Roof: case Window:
                return 1;
            case MagnetixBall: case MagnetixRod: case MagnetixTriangle:
            case MagnetixSquare: case MagnetixFlag:
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

            Color border = isSelected ? Helpers.newColor(255, 217, 51, 255)
                                      : Helpers.newColor(102, 102, 102, 255);
            Color bg = Helpers.newColor(13, 13, 13, 140);
            DrawRectangleRec(r, bg);
            DrawRectangleLinesEx(r, isSelected ? 3f : 1.5f, border);

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

    /** Call only while InventoryState.isOpen: categorized piece grid. */
    public static void updateFullInventory(InventoryState state, HotbarState hotbar, List<Piece.PieceDef> defs) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        Rectangle panel = Helpers.newRectangle((sw - 560) * 0.5f, (sh - 420) * 0.5f, 560, 420);
        UI.drawSteelPanel(panel, "INVENTORY");
        float x = panel.x() + 16, y = panel.y() + 40;

        DrawText("Click a piece to equip it, or drag it onto a hotbar slot.",
                 (int) x, (int) y, 14, UI.phosphorDim());
        y += 22;

        // Category tabs
        for (int c = 0; c <= 4; c++) {
            Rectangle tab = Helpers.newRectangle(x + c * 106, y, 100, 24);
            boolean active = state.filterCategory == c;
            if (UI.drawSteelButton(tab, categoryName(c), active)) state.filterCategory = c;
        }
        y += 30;

        // Grid of piece icons (6 per row)
        int columns = 6;
        float cell = 80f;
        int col = 0;
        Vector2 m = GetMousePosition();

        for (int i = 0; i < defs.size(); i++) {
            Piece.PieceDef def = defs.get(i);
            if (state.filterCategory != 0 && categoryOf(def.type) != state.filterCategory) continue;

            Rectangle cellRect = Helpers.newRectangle(x + col * cell, y, 64, 64);
            DrawRectangleRec(cellRect, Helpers.newColor(30, 32, 36, 255));
            DrawRectangleLinesEx(cellRect, 1.5f, Helpers.newColor(70, 75, 85, 255));
            drawIcon(def, cellRect);

            if (CheckCollisionPointRec(m, cellRect)) {
                DrawRectangleLinesEx(cellRect, 2f, UI.phosphor());
                // Click equips into the selected hotbar slot (click-to-equip)
                if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                    hotbar.slots[hotbar.selected] = i;
                }
                // Hold to drag: pick the piece up
                if (IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
                    state.draggedPieceIndex = i;
                }
            }

            DrawText(def.name, (int) cellRect.x(), (int) (cellRect.y() + cellRect.height() + 2), 12, UI.white());

            col++;
            if (col >= columns) { col = 0; y += 96; }
        }

        // Active drag: icon follows cursor; release over a hotbar slot assigns it.
        if (state.draggedPieceIndex >= 0) {
            if (IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
                Rectangle dragRect = Helpers.newRectangle(m.x() - 24, m.y() - 24, 48, 48);
                drawIcon(defs.get(state.draggedPieceIndex), dragRect);
                DrawText(defs.get(state.draggedPieceIndex).name,
                         (int) m.x() - 24, (int) m.y() + 26, 12, UI.phosphor());
            } else {
                // Released — check drop over the hotbar
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