package dev.alien2003.structurecompass.client.screen;

import dev.alien2003.structurecompass.client.ClientConfig;
import dev.alien2003.structurecompass.client.ClientNetwork;
import dev.alien2003.structurecompass.client.ClientSettings;
import dev.alien2003.structurecompass.client.StructureInfo;
import dev.alien2003.structurecompass.client.data.ClientStructureData;
import dev.alien2003.structurecompass.client.data.TrackedTarget;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.math.NavMath;
import dev.alien2003.structurecompass.common.math.TextFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Full list of structure types and their nearest instances: search, filters, per-type on/off, sorting, tracking,
 * copying coordinates and (with cheats) teleporting.
 */
public final class StructureScreen extends Screen {
    enum Filter { ALL, ENABLED, DISABLED, FOUND, NOT_FOUND }

    enum Sort { DISTANCE, NAME }

    private static final int HEADER_COLOR = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C8C8C;

    // State lives in static fields so it survives re-opening the screen and window resizes.
    private static String search = "";
    private static Filter filter = Filter.ALL;
    private static Sort sort = Sort.DISTANCE;
    private static @Nullable ResourceKey<Level> viewDimension;

    private @Nullable EditBox searchBox;
    private @Nullable StructureList list;
    private int lastRevision = -1;
    private int ticks;

    public StructureScreen() {
        super(Component.translatable("structurecompass.screen.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        ResourceKey<Level> current = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
        if (viewDimension == null || mc.player == null || !mc.player.connection.levels().contains(viewDimension)) {
            viewDimension = current;
        }
        int contentWidth = Math.min(this.width - 20, 460);
        int left = (this.width - contentWidth) / 2;

        int top = 20;
        int searchWidth = contentWidth - 3 * 104;
        this.searchBox = new EditBox(this.font, left, top, searchWidth, 20, Component.translatable("structurecompass.screen.search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setValue(search);
        this.searchBox.setHint(Component.translatable("structurecompass.screen.search_hint").setStyle(EditBox.SEARCH_HINT_STYLE));
        this.searchBox.setResponder(value -> {
            search = value;
            rebuild();
        });
        this.addRenderableWidget(this.searchBox);

        int x = left + searchWidth + 4;
        this.addRenderableWidget(CycleButton.builder(StructureScreen::filterName, filter)
                .withValues(Filter.values())
                .create(x, top, 100, 20, Component.translatable("structurecompass.screen.filter"), (b, v) -> {
                    filter = v;
                    rebuild();
                }));
        x += 104;
        this.addRenderableWidget(CycleButton.builder(StructureScreen::sortName, sort)
                .withValues(Sort.values())
                .create(x, top, 100, 20, Component.translatable("structurecompass.screen.sort"), (b, v) -> {
                    sort = v;
                    rebuild();
                }));
        x += 104;
        List<ResourceKey<Level>> dims = dimensions(mc, current);
        this.addRenderableWidget(CycleButton.builder(StructureScreen::dimensionName, viewDimension)
                .withValues(dims)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("structurecompass.screen.dimension.tooltip")))
                .create(x, top, 100, 20, Component.translatable("structurecompass.screen.dimension"), (b, v) -> {
                    viewDimension = v;
                    requestScan(false);
                    rebuild();
                }));

        int listTop = top + 26;
        int listBottom = this.height - 30;
        this.list = new StructureList(mc, this.width, listBottom - listTop, listTop, contentWidth);
        this.addRenderableWidget(this.list);

        int by = this.height - 25;
        int bw = (contentWidth - 12) / 4;
        this.addRenderableWidget(Button.builder(Component.translatable("structurecompass.screen.enable_all"), b -> {
            ClientConfig.setAll(visibleTypes(), true);
            rebuild();
        }).bounds(left, by, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("structurecompass.screen.disable_all"), b -> {
            ClientConfig.setAll(visibleTypes(), false);
            rebuild();
        }).bounds(left + bw + 4, by, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("structurecompass.screen.rescan"), b -> requestScan(true))
                .tooltip(Tooltip.create(Component.translatable("structurecompass.screen.rescan.tooltip")))
                .bounds(left + 2 * (bw + 4), by, bw, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(left + 3 * (bw + 4), by, bw, 20).build());

        rebuild();
    }

    @Override
    protected void setInitialFocus() {
        if (this.searchBox != null) {
            this.setInitialFocus(this.searchBox);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (ClientStructureData.revision() != lastRevision && ticks % 5 == 0) {
            rebuild();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the integrated server keeps searching while the screen is open
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 6, HEADER_COLOR);
        g.drawCenteredString(this.font, statusLine(), this.width / 2, this.height - 37 + 2, MUTED);
    }

    private Component statusLine() {
        ClientStructureData.DimensionData data = viewDimension == null ? null : ClientStructureData.get(viewDimension);
        if (data == null) {
            return Component.translatable("structurecompass.screen.status.waiting");
        }
        if (data.types.isEmpty() && !data.scanning) {
            return Component.translatable("structurecompass.screen.status.none");
        }
        String key = data.scanning ? "structurecompass.screen.status.scanning" : "structurecompass.screen.status.done";
        return Component.translatable(key, data.foundTypes(), data.types.size(), TextFormat.groupThousands(data.radius));
    }

    private static void requestScan(boolean force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || viewDimension == null) {
            return;
        }
        if (viewDimension.equals(mc.level.dimension())) {
            if (force) {
                ClientNetwork.sendSettings(Optional.empty(), true);
            }
        } else {
            ClientNetwork.sendSettings(Optional.of(viewDimension), true);
        }
    }

    private static List<ResourceKey<Level>> dimensions(Minecraft mc, ResourceKey<Level> current) {
        List<ResourceKey<Level>> dims = new ArrayList<>();
        if (mc.player != null) {
            dims.addAll(mc.player.connection.levels());
        }
        if (!dims.contains(current)) {
            dims.add(current);
        }
        dims.sort(Comparator.comparing((ResourceKey<Level> k) -> !k.equals(Level.OVERWORLD))
                .thenComparing(k -> !k.equals(Level.NETHER))
                .thenComparing(k -> !k.equals(Level.END))
                .thenComparing(k -> k.identifier().toString()));
        return dims;
    }

    private static Component filterName(Filter f) {
        return Component.translatable("structurecompass.screen.filter." + f.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component sortName(Sort s) {
        return Component.translatable("structurecompass.screen.sort." + s.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component dimensionName(ResourceKey<Level> key) {
        Identifier id = key.identifier();
        String langKey = "structurecompass.dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Component.translatableWithFallback(langKey, TextFormat.humanize(id.getPath()));
    }

    // ---------------------------------------------------------------------------------------------------------

    private List<Identifier> visibleTypes() {
        List<Identifier> out = new ArrayList<>();
        for (TypeView view : collect()) {
            out.add(view.id);
        }
        return out;
    }

    /** One structure type with its instances, prepared for display. */
    record TypeView(Identifier id, String name, boolean enabled, List<StructureEntry> entries, double nearest) {
    }

    private List<TypeView> collect() {
        List<TypeView> views = new ArrayList<>();
        ClientStructureData.DimensionData data = viewDimension == null ? null : ClientStructureData.get(viewDimension);
        if (data == null) {
            return views;
        }
        double[] origin = origin(data);
        String needle = TextFormat.normalizeForSearch(search);
        for (Identifier id : data.types) {
            String name = StructureInfo.nameWithNamespace(id);
            if (!needle.isEmpty() && !TextFormat.normalizeForSearch(name).contains(needle)
                    && !id.toString().contains(needle)) {
                continue;
            }
            boolean enabled = ClientConfig.isTypeEnabled(id);
            List<StructureEntry> entries = new ArrayList<>(data.entries(id));
            entries.sort(Comparator.comparingDouble(e -> distance(origin, e)));
            double nearest = entries.isEmpty() ? Double.MAX_VALUE : distance(origin, entries.get(0));
            boolean found = !entries.isEmpty();
            boolean keep = switch (filter) {
                case ALL -> true;
                case ENABLED -> enabled;
                case DISABLED -> !enabled;
                case FOUND -> found;
                case NOT_FOUND -> !found;
            };
            if (keep) {
                views.add(new TypeView(id, name, enabled, entries, nearest));
            }
        }
        if (sort == Sort.NAME) {
            views.sort(Comparator.comparing(TypeView::name, String.CASE_INSENSITIVE_ORDER));
        } else {
            views.sort(Comparator.comparingDouble(TypeView::nearest).thenComparing(TypeView::name, String.CASE_INSENSITIVE_ORDER));
        }
        return views;
    }

    /** Position distances are measured from: the player, or the scan center for another dimension. */
    private static double[] origin(ClientStructureData.DimensionData data) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && mc.level.dimension().equals(data.dimension)) {
            return new double[]{player.getX(), player.getZ()};
        }
        return new double[]{data.centerX, data.centerZ};
    }

    private static double distance(double[] origin, StructureEntry e) {
        return NavMath.horizontalDistance(e.x() + 0.5 - origin[0], e.z() + 0.5 - origin[1]);
    }

    private void rebuild() {
        if (this.list == null) {
            return;
        }
        lastRevision = ClientStructureData.revision();
        ClientStructureData.DimensionData data = viewDimension == null ? null : ClientStructureData.get(viewDimension);
        double[] origin = data == null ? new double[]{0, 0} : origin(data);
        List<StructureList.Row> rows = new ArrayList<>();
        for (TypeView view : collect()) {
            rows.add(new TypeRow(view));
            int index = 1;
            for (StructureEntry entry : view.entries) {
                rows.add(new InstanceRow(view.id, entry, index++, distance(origin, entry)));
            }
            if (view.entries.isEmpty()) {
                rows.add(new EmptyRow(data != null && data.scanning));
            }
        }
        this.list.setRows(rows);
    }

    // ---------------------------------------------------------------------------------------------------------
    // List

    static final class StructureList extends ContainerObjectSelectionList<StructureList.Row> {
        private final int rowWidth;

        StructureList(Minecraft mc, int width, int height, int y, int rowWidth) {
            super(mc, width, height, y, 22);
            this.rowWidth = rowWidth;
        }

        void setRows(List<Row> rows) {
            double scroll = this.scrollAmount();
            this.replaceEntries(rows);
            this.setScrollAmount(scroll);
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        }
    }

    /** Header of a structure type: color, name, id and the on/off checkbox. */
    private final class TypeRow extends StructureList.Row {
        private final TypeView view;
        private final Checkbox toggle;
        private final int color;

        TypeRow(TypeView view) {
            this.view = view;
            this.color = StructureInfo.color(view.id);
            this.toggle = Checkbox.builder(Component.empty(), StructureScreen.this.font)
                    .selected(view.enabled)
                    .tooltip(Tooltip.create(Component.translatable("structurecompass.screen.toggle.tooltip")))
                    .onValueChange((box, value) -> {
                        ClientConfig.setTypeEnabled(view.id, value);
                        StructureScreen.this.rebuild();
                    })
                    .build();
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = StructureScreen.this.font;
            int x = getContentX();
            int y = getContentY();
            int mid = getContentYMiddle();
            this.toggle.setPosition(x, mid - 8);
            this.toggle.render(g, mouseX, mouseY, partialTick);
            int textX = x + 22;
            g.fill(textX, mid - 4, textX + 8, mid + 4, color);
            int nameColor = view.enabled ? HEADER_COLOR : MUTED;
            g.drawString(font, view.name, textX + 12, mid - 4, nameColor, true);
            String id = view.id.toString();
            int idWidth = font.width(id);
            g.drawString(font, id, getContentRight() - idWidth, mid - 4, MUTED, false);
            if (hovering) {
                g.fill(getX(), y + getContentHeight() + 1, getX() + getWidth(), y + getContentHeight() + 2, 0x30FFFFFF);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(toggle);
        }
    }

    /** One instance: number, distance, coordinates, generated flag, Track / Copy / TP buttons. */
    private final class InstanceRow extends StructureList.Row {
        private final Identifier type;
        private final StructureEntry entry;
        private final String text;
        private final String coords;
        private final Button track;
        private final Button copy;
        private final @Nullable Button teleport;
        private final List<Button> buttons = new ArrayList<>(3);

        InstanceRow(Identifier type, StructureEntry entry, int index, double distance) {
            this.type = type;
            this.entry = entry;
            String unit = Component.translatable("structurecompass.unit.meters").getString();
            this.text = "#" + index + "  " + TextFormat.meters(NavMath.roundMeters(distance), unit);
            this.coords = TextFormat.coordinates(entry.x(), entry.y(), entry.yKnown(), entry.z())
                    + (entry.generated() ? "" : "  " + Component.translatable("structurecompass.screen.predicted").getString());
            boolean tracked = isTracked();
            this.track = Button.builder(Component.translatable(tracked ? "structurecompass.screen.untrack" : "structurecompass.screen.track"),
                            b -> toggleTrack())
                    .tooltip(Tooltip.create(Component.translatable("structurecompass.screen.track.tooltip")))
                    .size(64, 20).build();
            String clip = TextFormat.clipboardCoordinates(entry.x(), entry.y(), entry.yKnown(), entry.z());
            this.copy = Button.builder(Component.translatable("structurecompass.screen.copy"), b -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(clip);
                        b.setMessage(Component.translatable("structurecompass.screen.copied"));
                    })
                    .tooltip(Tooltip.create(Component.translatable("structurecompass.screen.copy.tooltip", clip)))
                    .size(64, 20).build();
            this.buttons.add(track);
            this.buttons.add(copy);
            if (canCheat()) {
                String command = teleportCommand(entry);
                this.teleport = Button.builder(Component.translatable("structurecompass.screen.teleport"), b -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player != null) {
                                player.connection.sendCommand(command);
                                StructureScreen.this.onClose();
                            }
                        })
                        .tooltip(Tooltip.create(Component.literal("/" + command)))
                        .size(40, 20).build();
                this.buttons.add(teleport);
            } else {
                this.teleport = null;
            }
        }

        private boolean isTracked() {
            TrackedTarget t = TrackedTarget.get();
            return t != null && viewDimension != null && t.matches(viewDimension, type, entry);
        }

        private void toggleTrack() {
            if (isTracked()) {
                TrackedTarget.clear();
            } else if (viewDimension != null) {
                TrackedTarget.set(new TrackedTarget(viewDimension, type, entry));
            }
            StructureScreen.this.rebuild();
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = StructureScreen.this.font;
            int x = getContentX() + 34;
            int mid = getContentYMiddle();
            int bx = getContentRight();
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Button b = buttons.get(i);
                bx -= b.getWidth();
                b.setPosition(bx, mid - 10);
                b.render(g, mouseX, mouseY, partialTick);
                bx -= 3;
            }
            boolean tracked = isTracked();
            int color = tracked ? ClientSettings.trackedColor : HEADER_COLOR;
            g.drawString(font, text, x, mid - 4, color, true);
            int coordsX = x + 70;
            String shown = font.plainSubstrByWidth(coords, Math.max(0, bx - coordsX - 4));
            g.drawString(font, shown, coordsX, mid - 4, entry.generated() ? 0xFFB8E0B8 : 0xFFC8C8C8, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return buttons;
        }
    }

    /** "Nothing found within the radius" / "searching" placeholder under a type header. */
    private final class EmptyRow extends StructureList.Row {
        private final Component message;

        EmptyRow(boolean scanning) {
            this.message = Component.translatable(scanning ? "structurecompass.screen.searching" : "structurecompass.screen.none_in_radius");
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            g.drawString(StructureScreen.this.font, message, getContentX() + 34, getContentYMiddle() - 4, MUTED, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Teleport commands are offered only when the player may use them (cheats / op level 2). */
    static boolean canCheat() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /**
     * {@code tp} to the real position when Y is known; otherwise {@code spreadplayers}, which puts the player on the
     * surface at X/Z (below the bedrock roof in the Nether).
     */
    static String teleportCommand(StructureEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        boolean sameDimension = mc.level != null && viewDimension != null && viewDimension.equals(mc.level.dimension());
        String prefix = sameDimension || viewDimension == null ? "" : "execute in " + viewDimension.identifier() + " run ";
        if (entry.yKnown()) {
            return prefix + "tp @s " + entry.x() + " " + (entry.y() + 1) + " " + entry.z();
        }
        boolean ceiling = sameDimension ? mc.level.dimensionType().hasCeiling() : Level.NETHER.equals(viewDimension);
        if (ceiling) {
            return prefix + "spreadplayers " + entry.x() + " " + entry.z() + " 0 1 under 120 false @s";
        }
        return prefix + "spreadplayers " + entry.x() + " " + entry.z() + " 0 1 false @s";
    }
}
