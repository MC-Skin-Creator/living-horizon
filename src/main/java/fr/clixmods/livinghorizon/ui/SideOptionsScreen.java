package fr.clixmods.livinghorizon.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
//?} elif >=1.20.5 {
/*import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
*///?} else {
/*import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
*///?}
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * An options screen that keeps the game in view: everything sits in a column on the left,
 * and in a world there is no blur or darkening behind it, so that a change can be judged
 * as it is made.
 */
public abstract class SideOptionsScreen extends OptionsSubScreen {
    private static final int PANEL_WIDTH = 360;

    protected SideOptionsScreen(@Nullable Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    //? if <1.21 {
    /*/^* The rows of options, which the game's screen has made itself since 1.21. ^/
    protected @Nullable OptionsList list;

    @Override
    protected void init() {
        addTitle();
        //? if >=1.20.5 {
        list = layout.addToContents(new OptionsList(minecraft, width, height, this));
        //?} elif >=1.20.3 {
        /^list = layout.addToContents(new OptionsList(minecraft, width, height, layout.getHeaderHeight()));
        ^///?} else {
        /^// The list was no widget before 1.20.3, which a layout could hold.
        list = addRenderableWidget(new OptionsList(minecraft, width, height, layout.getHeaderHeight()));
        ^///?}
        addOptions();
        addFooter();
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    protected abstract void addOptions();
    *///?}

    //? if <1.20.5 {
    /*/^* The title, the rows and the Done button, which the game's screen has laid out since 1.20.5. ^/
    public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    protected void addTitle() {
        layout.addToHeader(new StringWidget(title, font));
    }

    protected void addFooter() {
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
    }
    *///?}

    /** A title over a group of settings. The list has had its own since 1.21.11; before, a label in a row. */
    protected final void header(Component title) {
        if (list == null) return;
        //? if >=1.21.11 {
        list.addHeader(title);
        //?} else {
        /*list.addSmall(new StringWidget(150, 20, title, font), null);
        *///?}
    }

    /** The width of the column on the left, which the rows of options are centred on. */
    public int columnWidth() {
        return Math.min(PANEL_WIDTH, width);
    }

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // On the title screen the panorama is the background; in a world, nothing.
        if (minecraft.level == null) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
    //?} else {
    /*// Before 1.20.2 a screen draws its own background, in render.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///?}

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        int panel = columnWidth();
        if (list != null) list.updateSize(panel, layout);
        // The layout centres the header and footer on the screen: slide them all onto the column by
        // the same amount, so that a row of several buttons keeps its spacing.
        int shift = (width - panel) / 2;
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && widget != (Object) list) {
                widget.setX(widget.getX() - shift);
            }
        }
    }
}
