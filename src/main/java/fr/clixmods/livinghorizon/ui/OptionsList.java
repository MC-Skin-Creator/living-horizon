package fr.clixmods.livinghorizon.ui;

//? if <1.20.5 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/^*
 * The rows of the mod's option screens before 1.20.5, where the game's own list only held
 * options: here any widget, one or two to a row, as the game's list takes them from 1.20.5.
 ^/
public class OptionsList extends ContainerObjectSelectionList<OptionsList.Entry> {
    private static final int ROW_WIDTH = 310;

    public OptionsList(Minecraft minecraft, int width, int height, int y) {
        //? if >=1.20.3 {
        super(minecraft, width, height, y, 25);
        //?} else
        /^super(minecraft, width, height, y, y + height, 25);^/
        centerListVertically = false;
        // The world stays in view behind the screen, as on later versions.
        setRenderBackground(false);
    }

    public void addSmall(OptionInstance<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            addSmall(button(options[i]), i + 1 < options.length ? button(options[i + 1]) : null);
        }
    }

    public void addSmall(List<AbstractWidget> widgets) {
        for (int i = 0; i < widgets.size(); i += 2) {
            addSmall(widgets.get(i), i + 1 < widgets.size() ? widgets.get(i + 1) : null);
        }
    }

    public void addSmall(AbstractWidget first, @Nullable AbstractWidget second) {
        addEntry(new Entry(second == null ? List.of(first) : List.of(first, second)));
    }

    private AbstractWidget button(OptionInstance<?> option) {
        return option.createButton(minecraft.options, 0, 0, 150);
    }

    /^* Between the layout's header and footer, this wide. ^/
    public void updateSize(int width, HeaderAndFooterLayout layout) {
        int height = layout.getHeight() - layout.getHeaderHeight() - layout.getFooterHeight();
        //? if >=1.20.3 {
        setRectangle(width, height, 0, layout.getHeaderHeight());
        //?} else
        /^updateSize(width, height, layout.getHeaderHeight(), layout.getHeaderHeight() + height);^/
    }

    //? if <1.20.3 {
    /^// The list was no widget before 1.20.3: its edges, as a widget's.
    public int getX() {
        return x0;
    }

    public int getY() {
        return y0;
    }

    public int getRight() {
        return x1;
    }

    public int getBottom() {
        return y1;
    }
    ^///?}

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int getScrollbarPosition() {
        return getX() + width / 2 + ROW_WIDTH / 2 + 6;
    }

    static final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final List<AbstractWidget> widgets;

        Entry(List<AbstractWidget> widgets) {
            this.widgets = widgets;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                           int mouseY, boolean hovered, float partialTick) {
            int x = left;
            for (AbstractWidget widget : widgets) {
                widget.setX(x);
                widget.setY(top);
                widget.render(graphics, mouseX, mouseY, partialTick);
                x += widget.getWidth() + 10;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
*///?}
