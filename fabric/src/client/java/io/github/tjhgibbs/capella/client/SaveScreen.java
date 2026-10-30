package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.export.ExportMeta;
import io.github.tjhgibbs.capella.core.export.Exporter;
import io.github.tjhgibbs.capella.core.export.SchematicFormat;
import io.github.tjhgibbs.capella.mod.ExportPaths;
import io.github.tjhgibbs.capella.mod.Generation;
import io.github.tjhgibbs.capella.mod.McBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Save one building, or the variations picked in the grid, to every checked format at once.
 * Saving runs in the background with a progress bar and can be stopped. (The tree mod's "copies
 * with fresh seeds" is gone: a pristine church does not change with its seed.)
 */
final class SaveScreen extends Screen {
    /** One building to write. {@code building} is reused when it is already grown. */
    record Item(BuildingParams params, GeneratedBuilding building) {
    }

    private enum State { EDIT, SAVING, DONE }


    private static final int PANEL_W = 260;
    /** The two defaults first. */
    private static final List<SchematicFormat> ORDER = List.of(SchematicFormat.byId("sponge"), SchematicFormat.byId("14eredit"), SchematicFormat.byId("structure"));
    private static final int ROW = 22;

    private final Screen parent;
    private final List<Item> sources;
    private final String defaultName;
    private final Consumer<Component> onSaved;

    private State state = State.EDIT;
    private EditBox nameBox;
    private String name;
    private final List<Checkbox> formatBoxes = new ArrayList<>();
    private Button saveButton, cancelButton;
    private final Runnable closeAll;

    private final AtomicInteger done = new AtomicInteger();
    private final AtomicBoolean stop = new AtomicBoolean();
    private volatile int total;
    private volatile Component result = Component.empty();
    private final Map<Path, Integer> written = new LinkedHashMap<>();

    SaveScreen(Screen parent, List<Item> sources, String defaultName, Consumer<Component> onSaved, Runnable closeAll) {
        super(Component.translatable(sources.size() > 1 ? "capella.save.title_many" : "capella.save.title", sources.size()));
        this.parent = parent;
        this.sources = sources;
        this.defaultName = defaultName;
        this.name = defaultName;
        this.onSaved = onSaved;
        this.closeAll = closeAll;
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int panelW() {
        return Math.min(PANEL_W, width - 20);
    }

    private int top() {
        return Math.max(4, (height - 220) / 2);
    }

    @Override
    protected void init() {
        formatBoxes.clear();
        int x = left(), w = panelW(), y = top() + 26;
        nameBox = new EditBox(font, x, y, w, 18, Component.translatable("capella.label.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(name);
        nameBox.setHint(Component.literal(defaultName));
        nameBox.setResponder(s -> name = s);
        addRenderableWidget(nameBox);
        y += ROW + 12;
        for (SchematicFormat f : ORDER) {
            Checkbox box = Checkbox.builder(Component.translatable("capella.format." + f.id()), font)
                    .pos(x, y)
                    .selected(ClientPrefs.formats.contains(f.id()))
                    .onValueChange((cb, on) -> {
                        if (on) {
                            ClientPrefs.formats.add(f.id());
                        } else {
                            ClientPrefs.formats.remove(f.id());
                        }
                        updateState();
                    })
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("capella.save.folder",
                            Minecraft.getInstance().gameDirectory.toPath().relativize(ExportPaths.dirFor(f).toAbsolutePath()).toString())))
                    .build();
            formatBoxes.add(addRenderableWidget(box));
            y += 19;
        }
        y += 6 + 16;
        int bw = (w - 4) / 2;
        cancelButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (state == State.DONE) {
                stop.set(true);
                closeAll.run();
            } else {
                onClose();
            }
        }).bounds(x, y, bw, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("capella.button.save"), b -> start()).bounds(x + w - bw, y, bw, 20).build());
        updateState();
        setInitialFocus(saveButton);
    }

    private int count() {
        return sources.size();
    }

    private void updateState() {
        boolean edit = state == State.EDIT;
        nameBox.visible = edit;
        formatBoxes.forEach(b -> b.visible = edit);
        saveButton.visible = state != State.SAVING;
        saveButton.active = state == State.DONE || !ClientPrefs.formats.isEmpty();
        cancelButton.setMessage(Component.translatable(switch (state) {
            case EDIT -> "gui.cancel";
            case SAVING -> "capella.button.stop";
            case DONE -> "gui.done";
        }));
        saveButton.setMessage(state == State.DONE ? Component.translatable("capella.button.keep_editing")
                : count() > 1 ? Component.translatable("capella.button.save_n", count()) : Component.translatable("capella.button.save"));
    }

    // ---- saving ----------------------------------------------------------------------------------

    private void start() {
        if (state == State.DONE) {
            onClose();
            return;
        }
        List<SchematicFormat> targets = new ArrayList<>();
        for (SchematicFormat f : SchematicFormat.all()) {
            if (ClientPrefs.formats.contains(f.id())) {
                targets.add(f);
            }
        }
        ClientPrefs.save(); // next time the dialog opens with exactly these choices
        List<Item> items = new ArrayList<>(sources);
        String base = Exporter.safeName(name.isBlank() ? defaultName : name);
        String author = Minecraft.getInstance().getUser().getName();
        int dataVersion = McBlocks.dataVersion();
        total = items.size();
        done.set(0);
        stop.set(false);
        written.clear();
        state = State.SAVING;
        updateState();

        Thread t = new Thread(() -> {
            String error = null;
            int buildings = 0;
            for (int i = 0; i < items.size() && !stop.get(); i++) {
                Item item = items.get(i);
                try {
                    GeneratedBuilding building = item.building() != null ? item.building() : Generation.generate(item.params()).join();
                    String file = unique(items.size() == 1 ? base : base + "_" + (i + 1), targets);
                    ExportMeta meta = new ExportMeta(file, author, dataVersion, item.params().encode(), System.currentTimeMillis());
                    for (SchematicFormat f : targets) {
                        Path out = Exporter.write(f, building.volume(), meta, ExportPaths.dirFor(f));
                        synchronized (written) {
                            written.merge(out.getParent(), 1, Integer::sum);
                        }
                    }
                    buildings++;
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    break;
                }
                done.incrementAndGet();
            }
            int saved = buildings;
            String err = error;
            Minecraft.getInstance().execute(() -> finish(saved, err));
        }, "Capella save");
        t.setDaemon(true);
        t.start();
    }

    /** A file name that none of the target formats has used yet: name, name_2, name_3... */
    private static String unique(String name, List<SchematicFormat> targets) {
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? name : name + "_" + n;
            boolean free = true;
            for (SchematicFormat f : targets) {
                if (Files.exists(ExportPaths.dirFor(f).resolve(candidate + f.extension()))) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return candidate;
            }
        }
    }

    private void finish(int buildings, String error) {
        state = State.DONE;
        if (error != null) {
            result = Component.translatable("capella.msg.failed", error);
        } else {
            result = buildings == 1 ? Component.translatable("capella.save.done_one") : Component.translatable("capella.save.done_many", buildings);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f));
            onSaved.accept(result);
        }
        updateState();
    }

    // ---- rendering ---------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = left(), w = panelW(), y = top();
        g.centeredText(font, title, width / 2, y, 0xFFFFFFFF);
        switch (state) {
            case EDIT -> {
                g.text(font, Component.translatable("capella.label.name"), x, y + 16, 0xFFFFD866);
                g.text(font, Component.translatable("capella.save.to"), x, y + 26 + ROW + 2, 0xFFFFD866);
                Component hint = sources.size() > 1 ? Component.translatable("capella.save.picked_hint", sources.size())
                        : Component.translatable("capella.save.single_hint");
                g.text(font, hint, x, y + 26 + ROW + 12 + 19 * 3 + 6, 0xFFA0A0A0);
            }
            case SAVING -> {
                int by = y + 70;
                int n = done.get();
                g.centeredText(font, Component.translatable("capella.save.progress", n, total), width / 2, by - 14, 0xFFFFFFFF);
                g.fill(x, by, x + w, by + 8, 0xFF000000);
                g.outline(x, by, w, 8, 0xFF808080);
                g.fill(x + 1, by + 1, x + 1 + (w - 2) * n / Math.max(1, total), by + 7, 0xFF5BBF4A);
            }
            case DONE -> {
                int ly = y + 40;
                g.centeredText(font, result, width / 2, ly, 0xFFFFFFFF);
                ly += 16;
                synchronized (written) {
                    for (Map.Entry<Path, Integer> e : written.entrySet()) {
                        String dir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().relativize(e.getKey().toAbsolutePath()).toString();
                        g.centeredText(font, Component.translatable("capella.save.files_in", e.getValue(), dir), width / 2, ly, 0xFFA0A0A0);
                        ly += 11;
                    }
                }
            }
        }
    }

    @Override
    public void onClose() {
        stop.set(true);
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
