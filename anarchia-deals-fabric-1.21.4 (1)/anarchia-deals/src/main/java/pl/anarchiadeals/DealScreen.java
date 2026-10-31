package pl.anarchiadeals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class DealScreen extends Screen {
    public DealScreen() {
        super(Text.literal("Anarchia Deals"));
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int lx = width / 2 - 155, rx = width / 2 + 5, w = 150, y0 = 40, st = 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Start skanowania"), b -> {
            Config.save();
            close();
            Scanner.start();
        }).dimensions(lx, y0, w, 20).build());

        toggle(lx, y0 + st, w, "Auto-kupowanie", () -> c.autoBuy, v -> c.autoBuy = v);
        addDrawableChild(new ValueSlider(lx, y0 + 2 * st, w, 20, "Min. okazja %", 5, 90, c.discountPercent, v -> c.discountPercent = v));
        addDrawableChild(new ValueSlider(lx, y0 + 3 * st, w, 20, "Min. próbek ceny", 1, 30, c.minSamples, v -> c.minSamples = v));
        addDrawableChild(new ValueSlider(lx, y0 + 4 * st, w, 20, "Stron/kategorię", 1, 10, c.pages, v -> c.pages = v));
        addDrawableChild(new ValueSlider(lx, y0 + 5 * st, w, 20, "Opóźnienie (ticki)", 3, 40, c.actionDelay, v -> c.actionDelay = v));

        toggle(rx, y0, w, "Narzędzia", () -> c.catTools, v -> c.catTools = v);
        toggle(rx, y0 + st, w, "Uzbrojenia", () -> c.catArmor, v -> c.catArmor = v);
        toggle(rx, y0 + 2 * st, w, "Ksiąg", () -> c.catBooks, v -> c.catBooks = v);
        toggle(rx, y0 + 3 * st, w, "Specjalne", () -> c.catSpecial, v -> c.catSpecial = v);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zrzut GUI /ah (debug)"), b -> {
            Scanner.requestDump();
            b.setMessage(Text.literal("Zrzut przy następnym skanie"));
        }).dimensions(rx, y0 + 4 * st, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść bazę cen"), b -> {
            PriceDb.clear();
            b.setMessage(Text.literal("Baza wyczyszczona"));
        }).dimensions(rx, y0 + 5 * st, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(rx, y0 + 6 * st, w, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(onOff(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(label, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build());
    }

    private static Text onOff(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "§aTAK" : "§cNIE"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Anarchia Deals - okazje na /ah"), width / 2, 15, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Znane przedmioty w bazie cen: " + PriceDb.items() + "  |  " + Scanner.status()),
                width / 2, height - 16, 0xAAAAAA);
    }

    @Override
    public void removed() {
        Config.save();
        PriceDb.saveIfDirty();
    }

    private static class ValueSlider extends SliderWidget {
        private final int min, max;
        private final String label;
        private final IntConsumer setter;

        ValueSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer setter) {
            super(x, y, w, h, Text.empty(), (Math.min(max, Math.max(min, value)) - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
