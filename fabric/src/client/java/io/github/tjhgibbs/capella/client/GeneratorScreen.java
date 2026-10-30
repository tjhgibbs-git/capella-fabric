package io.github.tjhgibbs.capella.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.params.Param;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.mod.Generation;
import io.github.tjhgibbs.capella.mod.PlaceBuildingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The generator GUI. One screen, three columns, one footer:
 *
 * <pre>
 * +-----------+--------------------------------+------------------------+
 * | Building  | Cathedral        131x118x116   | [Basic][All]  [Reset]  |
 * |  Chapel   |                                |  Length (bays)    7    |
 * |  Parish   |   the building on a sky        |  Aisles           1    |
 * |  ...      |   background                   |  (All: every setting,  |
 * |           |  [<] [Above][Level][Plan]  [>] |   by section, scrolls) |
 * +-----------+--------------------------------+------------------------+
 * | [New seed] [Variations]         [Save...] [Build here] [Done]       |
 * +---------------------------------------------------------------------+
 * </pre>
 *
 * The preview shows fixed camera angles ({@link BuildingViews}): only the current one is rendered
 * until the player switches, then the rest render in the background. "Variations" swaps the
 * preview for a grid of re-seeded variants ({@link VariationGrid}); click one to use it, or
 * right- or shift-click several and save them together. Saving (one building, N copies, or picked variants,
 * to every checked format) happens in {@link SaveScreen}.
 */
public final class GeneratorScreen extends Screen {
    private static final int PAD = 6;
    private static final int ROW = 20;
    private static final int FOOTER = 20;
    private static final int VIEW_BAR = 22;
    private static final long DEBOUNCE_MS = 150;
    private static final int VARIATIONS = 6;
    private static final int SKY_TOP = 0xFFB4D8F4, SKY_BOTTOM = 0xFFEAF4FC;
    private static final int INK = 0xFF27394A, INK_SOFT = 0xFF5B6E80;

    /** The type the settings column was built for; a change rebuilds it with that type's settings and ranges. */
    private Params.BuildingType shownType;

    // Remembered across openings so the screen comes back the way it was left.
    private static BuildingParams lastParams;
    private static String lastPreset = "parish";
    private static boolean showAll;
    private static BuildingViews.Angle angle = BuildingViews.Angle.ABOVE;
    private static int turn;
    private static VariationGrid.Variety variety = VariationGrid.Variety.SUBTLE;
    private static int presetScroll = 0, settingsScroll = 0;

    private final Screen parent;
    private BuildingParams params;
    private String presetId;

    private final ScrollColumn presets = new ScrollColumn();
    private final ScrollColumn settings = new ScrollColumn();
    private final List<AbstractWidget> viewButtons = new ArrayList<>();
    private final List<AbstractWidget> gridButtons = new ArrayList<>();
    private Button variationsButton, saveButton, buildButton, resetButton;

    private GeneratedBuilding building;
    private BuildingViews views;
    /** The last building's views, kept on screen until the new building's first view is ready (no blank flash). */
    private BuildingViews previousViews;
    private boolean lookedAround;
    private final VariationGrid grid = new VariationGrid();
    private boolean gridMode;

    private CompletableFuture<?> pending;
    private boolean closed;
    private int generation;
    private boolean dirty = true;
    private long changedAt;
    private Component toast = Component.empty();
    private long toastUntil;

    public GeneratorScreen(Screen parent) {
        super(Component.translatable("capella.screen.title"));
        this.parent = parent;
        this.presetId = lastPreset;
        this.params = lastParams != null ? lastParams.copy() : Presets.get(presetId).params(newSeed());
    }

    private static long newSeed() {
        return ThreadLocalRandom.current().nextLong(1, 1L << 40);
    }

    // ---- layout --------------------------------------------------------------------------------

    private int leftW() {
        return Math.clamp(width / 5, 96, 130);
    }

    private int rightW() {
        return Math.clamp(width * 3 / 10, 136, 200);
    }

    private int bodyTop() {
        return PAD;
    }

    private int bodyBottom() {
        return height - FOOTER - PAD * 2;
    }

    private int previewX() {
        return PAD * 2 + leftW();
    }

    private int previewW() {
        return width - leftW() - rightW() - PAD * 4;
    }

    private int previewH() {
        return bodyBottom() - bodyTop();
    }

    /** The image area: the preview minus the info line on top and the view bar at the bottom. */
    private int imageY() {
        return bodyTop() + 12;
    }

    private int imageH() {
        return previewH() - 12 - VIEW_BAR;
    }

    private int rightX() {
        return width - rightW() - PAD;
    }

    @Override
    protected void init() {
        viewButtons.clear();
        gridButtons.clear();
        buildPresetList();
        buildSettings();
        buildViewBar();
        buildFooter();
        updateModeWidgets();
        if (views != null && !views.sized(viewWidth(), viewHeight())) {
            showBuilding(building); // window resized: re-render at the new resolution
        }
    }

    private void buildPresetList() {
        presets.clear();
        presets.bounds(PAD + 2, bodyTop() + 12, leftW() - 2, bodyBottom() - bodyTop() - 12);
        for (Presets.Preset p : Presets.all()) {
            ListRow row = new ListRow(Component.translatableWithFallback("capella.preset." + p.id(), p.name()), presets.innerWidth(),
                    () -> p.id().equals(presetId), () -> selectPreset(p));
            row.setTooltip(Tooltip.create(Component.translatableWithFallback("capella.preset." + p.id() + ".desc", p.description())));
            presets.add(addRenderableWidget(row), ListRow.HEIGHT);
        }
        presets.scroll(presetScroll);
        presets.layout();
    }

    private void buildSettings() {
        int x = rightX(), w = rightW();
        // Bottom row: progressive disclosure and reset.
        int by = bodyBottom() - ROW + 2, half = (w - 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable(showAll ? "capella.button.basic" : "capella.button.all"), b -> setShowAll(!showAll))
                .bounds(x, by, w - half - 2, ROW - 2)
                .tooltip(Tooltip.create(Component.translatable(showAll ? "capella.tooltip.basic" : "capella.tooltip.all"))).build());
        resetButton = addRenderableWidget(Button.builder(Component.translatable("capella.button.reset"), b -> resetToPreset())
                .bounds(x + w - half, by, half, ROW - 2)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.reset"))).build());

        shownType = params.get(Params.TYPE);
        settings.clear();
        settings.bounds(x + 2, bodyTop() + 12, w - 2, by - 2 - bodyTop() - 12);
        int iw = settings.innerWidth();
        if (!showAll) {
            for (Param<?> p : Params.basic(params.get(Params.TYPE))) {
                settings.add(addRenderableWidget(ParamWidgets.create(p, params, font, x, 0, iw, ROW - 2, this::paramsChanged)), ROW);
            }
        } else {
            settings.header(Component.translatable("capella.label.seed"));
            EditBox seedBox = new EditBox(font, x, 0, iw, ROW - 2, Component.translatable("capella.label.seed"));
            seedBox.setValue(Long.toString(params.seed()));
            seedBox.setResponder(s -> {
                try {
                    long seed = Long.parseLong(s.trim());
                    if (seed != params.seed()) {
                        params.seed(seed);
                        paramsChanged();
                    }
                } catch (NumberFormatException ignored) {
                    // Keep the old seed until the box holds a number again.
                }
            });
            settings.add(addRenderableWidget(seedBox), ROW);
            for (Param.Category c : Param.Category.values()) {
                List<Param<?>> shown = Params.in(c).stream().filter(p -> Params.applies(p, params.get(Params.TYPE))).toList();
                if (shown.isEmpty()) continue;
                settings.header(Component.translatable("capella.tab." + c.name().toLowerCase(Locale.ROOT)));
                for (Param<?> p : shown) {
                    settings.add(addRenderableWidget(ParamWidgets.create(p, params, font, x, 0, iw, ROW - 2, this::paramsChanged)), ROW);
                }
            }
        }
        settings.scroll(showAll ? settingsScroll : 0);
        settings.layout();
    }

    private void buildViewBar() {
        int y = bodyBottom() - VIEW_BAR + 2, x = previewX() + 2, w = previewW() - 4, h = 16;
        // Single-building mode: [<] [Above] [Level] [Below] [>]
        int arrow = 16, seg = Math.min(64, (w - arrow * 2 - 8) / 3);
        int sx = x + (w - (arrow * 2 + seg * 3 + 8)) / 2;
        viewButtons.add(addRenderableWidget(Button.builder(Component.literal("◀"), b -> turn(-1)).bounds(sx, y, arrow, h)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.turn_left"))).build()));
        int cx = sx + arrow + 2;
        for (BuildingViews.Angle a : BuildingViews.Angle.values()) {
            Button b = Button.builder(Component.translatable("capella.view." + a.name().toLowerCase(Locale.ROOT)), btn -> setAngle(a))
                    .bounds(cx, y, seg, h).build();
            b.setOverrideRenderHighlightedSprite(() -> angle == a); // selected = lit, like a focused button
            viewButtons.add(addRenderableWidget(b));
            cx += seg + 2;
        }
        viewButtons.add(addRenderableWidget(Button.builder(Component.literal("▶"), b -> turn(1)).bounds(cx, y, arrow, h)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.turn_right"))).build()));

        // Grid mode: [Shuffle] [Variety: Subtle]
        int half = Math.min(110, (w - 2) / 2), gx = x + (w - half * 2 - 2) / 2;
        gridButtons.add(addRenderableWidget(Button.builder(Component.translatable("capella.button.shuffle"), b -> shuffle())
                .bounds(gx, y, half, h).build()));
        gridButtons.add(addRenderableWidget(CycleButton.builder((VariationGrid.Variety v) ->
                        Component.translatable("capella.variety." + v.name().toLowerCase(Locale.ROOT)), variety)
                .withValues(VariationGrid.Variety.values())
                .withTooltip(v -> Tooltip.create(Component.translatable("capella.variety." + v.name().toLowerCase(Locale.ROOT) + ".desc")))
                .create(gx + half + 2, y, half, h, Component.translatable("capella.label.variety"), (b, v) -> {
                    variety = v;
                    shuffle();
                })));
    }

    private void buildFooter() {
        int y = height - FOOTER - PAD;
        int x = PAD;
        addRenderableWidget(Button.builder(Component.translatable("capella.button.reseed"), b -> reseed()).bounds(x, y, 66, FOOTER)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.reseed"))).build());
        x += 68;
        variationsButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleGrid()).bounds(x, y, 70, FOOTER)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.variations"))).build());

        int right = width - PAD;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 46, y, 46, FOOTER).build());
        buildButton = addRenderableWidget(Button.builder(Component.translatable("capella.button.place"), b -> placeHere())
                .bounds(right - 46 - 2 - 68, y, 68, FOOTER).build());
        saveButton = addRenderableWidget(Button.builder(Component.empty(), b -> openSave())
                .bounds(right - 46 - 2 - 68 - 2 - 72, y, 72, FOOTER)
                .tooltip(Tooltip.create(Component.translatable("capella.tooltip.save"))).build());
        Minecraft mc = Minecraft.getInstance();
        buildButton.active = mc.player != null && mc.player.isCreative();
        buildButton.setTooltip(Tooltip.create(Component.translatable(buildButton.active ? "capella.tooltip.place" : "capella.tooltip.place_disabled")));
    }

    /** Shows the widgets for the current mode and refreshes labels that depend on state. */
    private void updateModeWidgets() {
        viewButtons.forEach(b -> b.visible = !gridMode);
        gridButtons.forEach(b -> b.visible = gridMode);
        variationsButton.setMessage(Component.translatable(gridMode ? "capella.button.single" : "capella.button.variations"));
        int picked = gridMode ? grid.picked().size() : 0;
        saveButton.setMessage(picked > 0 ? Component.translatable("capella.button.save_picked", picked)
                : gridMode ? Component.translatable("capella.button.save_all") : Component.translatable("capella.button.save_dots"));
        saveButton.active = gridMode || building != null && !dirty;
        resetButton.active = !params.equals(Presets.get(presetId).params(params.seed()));
    }

    // ---- actions -------------------------------------------------------------------------------

    private static void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private void selectPreset(Presets.Preset p) {
        if (p.id().equals(presetId)) {
            return;
        }
        presetId = p.id();
        lastPreset = presetId;
        params = p.params(params.seed());
        paramsChanged();
        rememberScroll();
        rebuildWidgets();
    }

    private void resetToPreset() {
        params = Presets.get(presetId).params(params.seed());
        paramsChanged();
        rememberScroll();
        rebuildWidgets();
    }

    private void setShowAll(boolean all) {
        rememberScroll();
        showAll = all;
        rebuildWidgets();
    }

    private void rememberScroll() {
        presetScroll = presets.scroll();
        if (showAll) {
            settingsScroll = settings.scroll();
        }
    }

    private void reseed() {
        params.seed(newSeed());
        sound(SoundEvents.BONE_MEAL_USE, 1.0f, 0.8f);
        paramsChanged();
        if (gridMode) {
            shuffle();
        }
        rememberScroll();
        rebuildWidgets(); // the seed box shows the new seed
    }

    private void paramsChanged() {
        Params.BuildingType type = params.get(Params.TYPE);
        if (shownType != null && type != shownType) {
            Params.changeType(params, type);
            shownType = type;
            dirty = true;
            changedAt = System.currentTimeMillis();
            lastParams = params.copy();
            rememberScroll();
            rebuildWidgets();
            return;
        }
        dirty = true;
        changedAt = System.currentTimeMillis();
        lastParams = params.copy();
        if (saveButton != null) {
            updateModeWidgets();
        }
    }

    private void setAngle(BuildingViews.Angle a) {
        angle = a;
        lookAround();
    }

    private void turn(int by) {
        turn = Math.floorMod(turn + by, BuildingViews.TURNS);
        lookAround();
    }

    /** The player asked for another angle: render it now and the rest in the background. */
    private void lookAround() {
        sound(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f);
        lookedAround = true;
        if (views != null) {
            views.request(angle, turn);
            views.prefetch();
        }
    }

    private void toggleGrid() {
        gridMode = !gridMode;
        if (gridMode && grid.isEmpty()) {
            shuffle();
        }
        updateModeWidgets();
    }

    private void shuffle() {
        int[] tile = tileSize();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        grid.shuffle(params, variety, VARIATIONS, (int) Math.min(320, tile[0] * s), (int) Math.min(320, tile[1] * s));
        updateModeWidgets();
    }

    private void adopt(VariationGrid.Tile tile) {
        params = tile.params.copy();
        lastParams = params.copy();
        sound(SoundEvents.BONE_MEAL_USE, 1.2f, 0.8f);
        gridMode = false;
        if (tile.building != null) {
            dirty = false;
            showBuilding(tile.building);
        } else {
            paramsChanged();
        }
        rememberScroll();
        rebuildWidgets();
    }

    private void placeHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        sound(SoundEvents.BONE_MEAL_USE, 0.8f, 1.0f);
        ClientPlayNetworking.send(new PlaceBuildingPayload(params.encode(), buildTarget(mc), mc.player.getDirection().get2DDataValue()));
        onClose();
    }

    /**
     * The block the crosshair is on (the west door's threshold goes on the face you look at),
     * else the player's feet. The church is built facing away from the player.
     */
    private static BlockPos buildTarget(Minecraft mc) {
        if (mc.player.pick(128, 0, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos().relative(hit.getDirection());
        }
        return mc.player.blockPosition();
    }

    private void openSave() {
        List<SaveScreen.Item> items = new ArrayList<>();
        String name;
        List<VariationGrid.Tile> picked = !gridMode ? List.of() : grid.picked().isEmpty() ? grid.tiles() : grid.picked();
        if (!picked.isEmpty()) {
            picked.forEach(t -> items.add(new SaveScreen.Item(t.params, t.building)));
            name = presetId + "_set";
        } else {
            items.add(new SaveScreen.Item(params.copy(), building));
            name = presetId + "_" + params.seed();
        }
        minecraft.setScreenAndShow(new SaveScreen(this, items, name, msg -> {
            toast = msg;
            toastUntil = System.currentTimeMillis() + 5000;
        }, this::onClose));
    }

    // ---- generation & preview --------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        boolean idle = pending == null || pending.isDone();
        if (dirty && idle && System.currentTimeMillis() - changedAt >= DEBOUNCE_MS) {
            dirty = false;
            int gen = ++generation;
            pending = Generation.generate(params.copy()).whenComplete((t, err) -> Minecraft.getInstance().execute(() -> {
                if (gen != generation || closed) {
                    return;
                }
                if (err != null) {
                    toast = Component.translatable("capella.msg.failed", String.valueOf(err.getMessage()));
                    toastUntil = System.currentTimeMillis() + 5000;
                    return;
                }
                showBuilding(t);
            }));
        }
    }

    private void showBuilding(GeneratedBuilding t) {
        building = t;
        if (previousViews != null) {
            previousViews.close();
        }
        previousViews = views;
        views = new BuildingViews(t, viewWidth(), viewHeight());
        views.request(angle, turn);
        if (lookedAround) {
            views.prefetch();
        }
        updateModeWidgets();
    }

    /** Preview resolution in physical pixels, so the image is crisp at any GUI scale. */
    private int viewWidth() {
        return (int) Math.min(512, Math.max(32, previewW() * Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private int viewHeight() {
        return (int) Math.min(512, Math.max(32, imageH() * Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private void closeViews() {
        if (views != null) {
            views.close();
            views = null;
        }
        if (previousViews != null) {
            previousViews.close();
            previousViews = null;
        }
    }

    private int[] tileSize() {
        int cols = cols();
        int rows = (VARIATIONS + cols - 1) / cols;
        return new int[]{(previewW() - (cols - 1) * 3) / cols, (imageH() - (rows - 1) * 3) / rows};
    }

    private int cols() {
        return previewW() >= imageH() * 1.1 ? 3 : 2;
    }

    // ---- rendering & input -----------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Panels behind the lists, sky behind the building.
        g.fill(PAD - 1, bodyTop() - 1, PAD + leftW() + 1, bodyBottom() + 1, 0x80000000);
        g.fill(rightX() - 1, bodyTop() - 1, rightX() + rightW() + 1, bodyBottom() + 1, 0x80000000);
        int px = previewX(), py = bodyTop(), pw = previewW(), ph = previewH();
        g.fillGradient(px, py, px + pw, py + ph, SKY_TOP, SKY_BOTTOM);
        g.outline(px - 1, py - 1, pw + 2, ph + 2, 0xFF000000);

        g.text(font, Component.translatable("capella.label.buildings"), PAD + 3, bodyTop() + 2, 0xFFFFD866);
        g.text(font, Component.translatable("capella.label.settings"), rightX() + 3, bodyTop() + 2, 0xFFFFD866);
        if (gridMode) {
            extractGrid(g, mouseX, mouseY);
        } else {
            extractSingle(g);
        }
        presets.extract(g, font);
        settings.extract(g, font);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (System.currentTimeMillis() < toastUntil) {
            int tw = font.width(toast) + 10;
            int tx = px + (pw - tw) / 2, ty = imageY() + imageH() - 16;
            g.fill(tx, ty, tx + tw, ty + 14, 0xC0000000);
            g.centeredText(font, toast, px + pw / 2, ty + 3, 0xFFFFFFFF);
        }
    }

    private void extractSingle(GuiGraphicsExtractor g) {
        int px = previewX(), pw = previewW();
        String name = Component.translatableWithFallback("capella.preset." + presetId, presetId).getString();
        boolean busy = dirty || pending != null && !pending.isDone();
        String info = building == null ? "" : String.format(Locale.ROOT, "%d×%d×%d · %,d", building.volume().width, building.volume().height,
                building.volume().length, building.volume().blockCount());
        int infoW = font.width(info);
        int room = pw - infoW - 12;
        String label = font.width(name) <= room ? name : font.plainSubstrByWidth(name, room - font.width("…")) + "…";
        g.text(font, label, px + 4, bodyTop() + 3, INK, false);
        g.text(font, info, px + pw - 4 - infoW, bodyTop() + 3, INK_SOFT, false);
        boolean drawn = views != null && views.draw(g, angle, turn, px, imageY(), pw, imageH());
        if (drawn && previousViews != null) {
            previousViews.close();
            previousViews = null;
        } else if (!drawn && previousViews != null) {
            previousViews.draw(g, angle, turn, px, imageY(), pw, imageH());
        }
        if (busy || !drawn) {
            Component msg = Component.translatable("capella.msg.generating");
            int mw = font.width(msg) + 10, mx = px + (pw - mw) / 2, my = imageY() + 2;
            g.fill(mx, my, mx + mw, my + 13, 0x90000000);
            g.centeredText(font, msg, px + pw / 2, my + 3, 0xFFFFFFFF);
        }
    }

    private void extractGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int px = previewX(), pw = previewW();
        g.text(font, font.plainSubstrByWidth(Component.translatable("capella.hint.grid").getString(), pw - 8), px + 4, bodyTop() + 3, INK_SOFT, false);
        List<VariationGrid.Tile> tiles = grid.tiles();
        for (int i = 0; i < tiles.size(); i++) {
            int[] r = tileRect(i);
            VariationGrid.Tile t = tiles.get(i);
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0x30FFFFFF : 0x18000000);
            if (t.ready()) {
                t.texture.draw(g, r[0], r[1], r[2], r[3]);
            } else {
                g.centeredText(font, Component.literal("…"), r[0] + r[2] / 2, r[1] + r[3] / 2 - 4, INK_SOFT);
            }
            if (t.picked) {
                g.outline(r[0], r[1], r[2], r[3], 0xFFFFC21A);
                g.outline(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 0xFFFFC21A);
                g.fill(r[0] + r[2] - 11, r[1] + 2, r[0] + r[2] - 2, r[1] + 11, 0xFFFFC21A);
                g.text(font, "✔", r[0] + r[2] - 10, r[1] + 3, 0xFF000000, false);
            } else if (hover) {
                g.outline(r[0], r[1], r[2], r[3], 0xFFFFFFFF);
            }
        }
    }

    private int[] tileRect(int i) {
        int cols = cols();
        int[] size = tileSize();
        int c = i % cols, r = i / cols;
        return new int[]{previewX() + c * (size[0] + 3), imageY() + r * (size[1] + 3), size[0], size[1]};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (gridMode) {
            List<VariationGrid.Tile> tiles = grid.tiles();
            for (int i = 0; i < tiles.size(); i++) {
                int[] r = tileRect(i);
                if (event.x() >= r[0] && event.x() < r[0] + r[2] && event.y() >= r[1] && event.y() < r[1] + r[3]) {
                    VariationGrid.Tile t = tiles.get(i);
                    if (event.hasShiftDown() || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                        t.picked = !t.picked;
                        sound(SoundEvents.UI_BUTTON_CLICK.value(), t.picked ? 1.2f : 0.9f, 0.5f);
                        updateModeWidgets();
                    } else {
                        adopt(t);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (presets.contains(x, y) && presets.mouseScrolled(scrollY)) {
            return true;
        }
        if (settings.contains(x, y)) {
            // The wheel always scrolls the panel; it never cycles a button the list scrolls past.
            settings.mouseScrolled(scrollY);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        closed = true;
        closeViews();
        grid.close();
        rememberScroll();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
