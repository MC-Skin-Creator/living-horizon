package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the impostors look like: every figure baked so far, one row each, its eight views
 * at full size and the front one at the sizes it has far away, in real screen pixels.
 * A button throws every picture away and bakes them again. Opened from the settings, or
 * {@code /livinghorizon impostors}.
 */
public final class ImpostorScreen extends Screen {
    private static final String KEY = "livinghorizon.impostors.";

    /** Screen pixels of the front view drawn small: a figure 250, 500 and 1000 blocks away. */
    private static final int[] FAR = {32, 16, 8};
    private static final int TOP = 40, NAME = 110, GAP = 2;
    /** A sky behind the pictures, as they are seen in the world. */
    private static final int SKY = 0xFF9DB8E8, NAME_COLOR = 0xFFFFFFFF, DIM = 0xFFA0A0A0;

    private final @Nullable Screen parent;
    private List<ImpostorAtlas.Baked> rows = List.of();
    private double scroll;

    public ImpostorScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "regenerate"), b -> {
                    ImpostorAtlas.regenerate();
                    scroll = 0;
                })
                .tooltip(Tooltip.create(Component.translatable(KEY + "regenerate.tooltip")))
                .bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 4, y, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Pixels of a view in a row: as large as fits, never larger than the picture itself. */
    private int tile() {
        int room = width - 20 - NAME - farWidth() - 8;
        return Mth.clamp(room / ImpostorViews.VIEWS - GAP, 16, ImpostorViews.TILE);
    }

    private int farWidth() {
        int sum = 0;
        for (int size : FAR) sum += Mth.ceil(size / (float) minecraft.getWindow().getGuiScale()) + GAP;
        return sum;
    }

    private int rowHeight() {
        return tile() + 6;
    }

    private int bottom() {
        return height - 32;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Before 1.20.2 a screen draws its own background.
        //? if <1.20.2
        /*renderBackground(graphics);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, NAME_COLOR);
        graphics.drawCenteredString(font, status(), width / 2, 22, DIM);

        rows = ImpostorAtlas.baked();
        rows.sort(Comparator.comparing(row -> name(row).getString().toLowerCase(Locale.ROOT)));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() * rowHeight() - (bottom() - TOP)));

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty"), width / 2, TOP + 20, DIM);
            return;
        }
        // There are pictures to show from 1.21.9 only; before, the list is always empty.
        //? if >=1.21.9 {
        int tile = tile(), left = 10, scale = minecraft.getWindow().getGuiScale();
        graphics.enableScissor(0, TOP, width, bottom());
        int y = TOP - (int) scroll;
        for (ImpostorAtlas.Baked row : rows) {
            if (y + rowHeight() > TOP && y < bottom()) drawRow(graphics, row, left, y, tile, scale);
            y += rowHeight();
        }
        graphics.disableScissor();
        //?}
    }

    //? if >=1.21.9 {
    private void drawRow(GuiGraphics graphics, ImpostorAtlas.Baked row, int x, int y, int tile, int scale) {
        graphics.drawString(font, name(row), x, y + tile / 2 - 4, NAME_COLOR);
        x += NAME;
        ImpostorAtlas.Sheet sheet = row.sheet();
        Identifier page = ImpostorAtlas.id(sheet.page());
        for (int view = 0; view < ImpostorViews.VIEWS; view++) {
            graphics.fill(x, y, x + tile, y + tile, SKY);
            blitView(graphics, page, sheet, view, x, y, tile);
            x += tile + GAP;
        }
        x += 8;
        // Real screen pixels, whatever the GUI scale: drawn scaled down from GUI units.
        for (int size : FAR) {
            int room = Mth.ceil(size / (float) scale);
            graphics.fill(x, y, x + room, y + room, SKY);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(1f / scale);
            blitView(graphics, page, sheet, 0, 0, 0, size);
            graphics.pose().popMatrix();
            x += room + GAP;
        }
    }

    private static void blitView(GuiGraphics graphics, Identifier page, ImpostorAtlas.Sheet sheet, int view, int x, int y,
                                 int size) {
        float u0 = (sheet.column() * ImpostorViews.VIEWS + view) / (float) ImpostorAtlas.TILES_ACROSS;
        float u1 = u0 + 1f / ImpostorAtlas.TILES_ACROSS;
        float v0 = sheet.row() / (float) ImpostorAtlas.TILES_DOWN;
        float v1 = v0 + 1f / ImpostorAtlas.TILES_DOWN;
        graphics.blit(page, x, y, x + size, y + size, u0, u1, v0, v1);
    }
    //?}

    private static Component name(ImpostorAtlas.Baked row) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(row.key().type())).map(EntityType::getDescription)
                .orElse(Component.literal(row.key().type()));
    }

    private Component status() {
        FarConfig config = FarConfig.get();
        if (!config.impostors) return Component.translatable(KEY + "off");
        if (ImpostorAtlas.failed()) return Component.translatable("livinghorizon.debug.f3.impostorsFailed");
        return Component.translatable(KEY + "status", ImpostorAtlas.sheets(), ImpostorAtlas.waiting());
    }

    @Override
    //? if >=1.20.2 {
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //?} else
    /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {*/
        scroll -= scrollY * rowHeight();
        return true;
    }
}
