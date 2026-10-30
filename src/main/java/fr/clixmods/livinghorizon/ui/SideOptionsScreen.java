package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.ModVersion;
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

    /** A title over part of a group of settings, in grey under the group's own. */
    protected final void subheader(Component title) {
        header(title.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** A title over a group of settings. The list has had its own since 1.21.11; before, a label in a row. */
    protected final void header(Component title) {
        if (list == null) return;
        //? if >=1.21.11 {
        list.addHeader(title);
        //?} else {
        /*list.addSmall(new StringWidget(150, 20, title, font), null);
        *///?}
    }

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (typing != null) return typingKey(event.key(), () -> typing.field() != null && typing.field().keyPressed(event));
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && shouldCloseOnEsc() && SodiumReturn.below(lastScreen)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (typing != null && typing.field() != null) return typing.field().charTyped(event);
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return clicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing != null) return typingKey(keyCode, () -> typing.field() != null && typing.field().keyPressed(keyCode, scanCode, modifiers));
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && shouldCloseOnEsc() && SodiumReturn.below(lastScreen)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (typing != null && typing.field() != null) return typing.field().charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    /** The slider typed in, which takes the keys until Enter, Escape or a click. */
    private @Nullable NumberSlider typing;

    /** Escape leaves the field as it was, Enter keeps what is typed when it reads as a value. */
    private boolean typingKey(int key, java.util.function.BooleanSupplier field) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            stopTyping(false);
        } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            if (typing != null && typing.typedIsValid()) stopTyping(true);
        } else {
            field.getAsBoolean();
        }
        return true;
    }

    private void stopTyping(boolean keep) {
        NumberSlider slider = typing;
        typing = null;
        if (slider != null) slider.stopTyping(keep);
    }

    /**
     * A click while typing keeps what is typed (and is spent on the field itself when it lands
     * there); a right click on a slider of numbers opens its field.
     */
    private boolean clicked(double x, double y, int button) {
        if (typing != null) {
            boolean onField = typing.isMouseOver(x, y);
            stopTyping(typing.typedIsValid());
            if (onField || minecraft.screen != this) return true;
        }
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT || list == null || !list.isMouseOver(x, y)) {
            return false;
        }
        NumberSlider slider = sliderAt(list, x, y);
        if (slider == null) return false;
        typing = slider;
        slider.startTyping();
        return true;
    }

    private static @Nullable NumberSlider sliderAt(GuiEventListener listener, double x, double y) {
        if (listener instanceof NumberSlider slider) return slider.shown() && slider.isMouseOver(x, y) ? slider : null;
        if (listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                NumberSlider found = sliderAt(child, x, y);
                if (found != null) return found;
            }
        }
        return null;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NumberSlider.nextFrame();
        super.render(graphics, mouseX, mouseY, partialTick);
        drawVersion(graphics);
    }
    //?} else {
    /*// Before 1.20.2 a screen draws its own background, in render.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NumberSlider.nextFrame();
        if (minecraft.level == null) renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawVersion(graphics);
    }
    *///?}

    /** The mod's version in grey, centred under the title of the screens of the mod's settings. */
    private void drawVersion(GuiGraphics graphics) {
        if (showsVersion()) graphics.drawCenteredString(font, ModVersion.label(), columnWidth() / 2, 23, 0xFFAAAAAA);
        drawOver(graphics);
    }

    /** Whether the version stands under the title: a screen with something else there says no. */
    protected boolean showsVersion() {
        return true;
    }

    /** Drawn over the widgets, after everything: a picture on a button. */
    protected void drawOver(GuiGraphics graphics) {}

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
