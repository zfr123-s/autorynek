package pl.anarchiadeals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Automat: /ah -> kategoria -> sortowanie "najnowsze" -> skan stron -> (opcjonalnie) zakup. */
public final class Scanner {
    private Scanner() {}

    private enum State { IDLE, START, WAIT_GUI, CATEGORY, SORT, SCAN, NEXT_PAGE, CONFIRM, COOLDOWN }

    private static final String[] CAT_STEMS = {"narz", "uzbroj", "ksi", "specjal"};
    private static final String[] CAT_NAMES = {"narzędzia", "uzbrojenia", "ksiąg", "specjalne"};

    private static State state = State.IDLE;
    private static final List<Integer> activeCats = new ArrayList<>();
    private static final Set<String> recorded = new HashSet<>();
    private static final Set<String> notified = new HashSet<>();
    private static int delay, wait, catIdx, page, sortTries, bought, saveTimer, dumpLeft, clickSync;
    private static boolean yDown;
    private static int dealsSeen;

    public static boolean running() {
        return state != State.IDLE;
    }

    public static void requestDump() {
        dumpLeft = 2;
    }

    public static String status() {
        if (state == State.IDLE) return "Zatrzymany";
        String cat = activeCats.isEmpty() || catIdx >= activeCats.size() ? "-" : CAT_NAMES[activeCats.get(catIdx)];
        return "Działa [" + state + "] kategoria: " + cat + ", strona " + (page + 1)
                + ", zakupy: " + bought + ", okazje: " + dealsSeen;
    }

    public static void start() {
        buildCats();
        if (activeCats.isEmpty()) {
            msg("Włącz przynajmniej jedną kategorię w menu (Y).");
            return;
        }
        catIdx = 0;
        page = 0;
        bought = 0;
        dealsSeen = 0;
        delay = 0;
        dumpLeft = 2;
        yDown = true;
        notified.clear();
        state = State.START;
        msg("Start skanowania. Naciśnij Y, aby zatrzymać.");
    }

    public static void stop(String why) {
        state = State.IDLE;
        PriceDb.saveIfDirty();
        msg(why);
    }

    private static void buildCats() {
        activeCats.clear();
        Config c = Config.get();
        if (c.catTools) activeCats.add(0);
        if (c.catArmor) activeCats.add(1);
        if (c.catBooks) activeCats.add(2);
        if (c.catSpecial) activeCats.add(3);
    }

    public static void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        boolean okScreen = mc.currentScreen == null || mc.currentScreen instanceof GenericContainerScreen;
        boolean y = InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_Y);
        if (y && !yDown && okScreen) {
            yDown = true;
            if (mc.currentScreen != null) p.closeHandledScreen();
            stop("Zatrzymano (Y).");
            return;
        }
        yDown = y;
        if (!okScreen) return; // menu / czat otwarte - pauza

        if (delay > 0) {
            delay--;
            return;
        }
        if (++saveTimer > 1200) {
            saveTimer = 0;
            PriceDb.saveIfDirty();
        }

        Config cfg = Config.get();
        GenericContainerScreenHandler h = handler(mc);

        switch (state) {
            case START -> {
                if (mc.currentScreen != null) p.closeHandledScreen();
                p.networkHandler.sendChatCommand("ah");
                state = State.WAIT_GUI;
                wait = 0;
                delay = 10;
            }
            case WAIT_GUI -> {
                if (h != null && hasContent(h)) {
                    state = State.CATEGORY;
                    delay = cfg.actionDelay;
                } else if (++wait > 100) {
                    msg("Nie udało się otworzyć /ah - ponawiam za chwilę.");
                    state = State.COOLDOWN;
                    delay = cfg.cycleDelay;
                }
            }
            case CATEGORY -> {
                if (h == null) { state = State.START; return; }
                if (dumpLeft > 0) { dump(mc, h, "przed wyborem kategorii"); dumpLeft--; }
                int cat = activeCats.get(catIdx);
                int s = findButton(h, CAT_STEMS[cat]);
                if (s >= 0) click(mc, h, s);
                else msg("Nie widzę przycisku kategorii: " + CAT_NAMES[cat]);
                state = State.SORT;
                sortTries = 0;
                page = 0;
                delay = cfg.actionDelay;
            }
            case SORT -> {
                if (h == null) { state = State.START; return; }
                int s = findButton(h, "sortowan");
                if (s < 0) { state = State.SCAN; return; }
                if (Market.newestSelected(h.slots.get(s).getStack())) {
                    state = State.SCAN;
                    return;
                }
                if (sortTries >= 6) {
                    msg("Nie wykryłem sortowania 'Najnowsze' - skanuję bieżącą kolejność.");
                    state = State.SCAN;
                    return;
                }
                click(mc, h, s);
                sortTries++;
                delay = cfg.actionDelay;
            }
            case SCAN -> {
                if (h == null) { state = State.START; return; }
                scan(mc, h);
            }
            case CONFIRM -> {
                if (h == null) { state = State.START; return; }
                boolean newGui = h.syncId != clickSync;
                int c = findConfirm(h, newGui);
                if (c >= 0) {
                    click(mc, h, c);
                    state = State.SCAN;
                    delay = cfg.actionDelay * 2;
                } else if (++wait > 15) {
                    state = State.SCAN;
                    delay = cfg.actionDelay;
                }
            }
            case NEXT_PAGE -> {
                if (h == null) { state = State.START; return; }
                page++;
                int nx = page < cfg.pages ? findNext(h) : -1;
                if (nx >= 0) {
                    click(mc, h, nx);
                    state = State.SCAN;
                    delay = cfg.actionDelay;
                } else {
                    catIdx++;
                    if (catIdx >= activeCats.size()) {
                        catIdx = 0;
                        p.closeHandledScreen();
                        state = State.COOLDOWN;
                        delay = cfg.cycleDelay;
                        buildCats();
                        if (activeCats.isEmpty()) stop("Brak aktywnych kategorii.");
                    } else {
                        state = State.CATEGORY;
                        delay = cfg.actionDelay;
                    }
                }
            }
            case COOLDOWN -> state = State.START;
            default -> { }
        }
    }

    private static void scan(MinecraftClient mc, GenericContainerScreenHandler h) {
        Config c = Config.get();
        if (dumpLeft > 0) { dump(mc, h, "po wyborze kategorii i sortowania"); dumpLeft--; }

        List<Market.Listing> ls = new ArrayList<>();
        int n = h.getRows() * 9;
        for (int i = 0; i < n; i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (st.isEmpty()) continue;
            Market.Listing l = Market.parse(i, st);
            if (l != null) ls.add(l);
        }

        Market.Listing best = null;
        for (Market.Listing l : ls) {
            if (PriceDb.count(l.key) < c.minSamples) continue;
            double avg = PriceDb.avg(l.key);
            if (avg <= 0) continue;
            double disc = (1 - l.unit / avg) * 100;
            if (disc < c.discountPercent) continue;
            if (c.maxPrice > 0 && l.price > c.maxPrice) continue;
            l.avg = avg;
            l.disc = disc;
            if (best == null || disc > best.disc) best = l;
            if (notified.add(l.fp)) {
                dealsSeen++;
                msg("§aOkazja: §f" + l.name + " x" + l.count + " za " + fmt(l.price)
                        + " (średnia za szt. " + fmt(l.avg) + ", -" + Math.round(l.disc) + "%)");
                if (mc.player != null) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }

        if (recorded.size() > 5000) recorded.clear();
        for (Market.Listing l : ls) {
            if (recorded.add(l.fp)) PriceDb.add(l.key, l.unit);
        }

        if (best != null && c.autoBuy) {
            if (bought < c.maxBuys) {
                msg("Kupuję: " + best.name + " za " + fmt(best.price));
                clickSync = h.syncId;
                click(mc, h, best.slot);
                bought++;
                state = State.CONFIRM;
                wait = 0;
                delay = c.actionDelay;
                return;
            } else if (bought == c.maxBuys) {
                bought++;
                msg("Osiągnięto limit zakupów na to uruchomienie (" + c.maxBuys + ").");
            }
        }
        state = State.NEXT_PAGE;
        delay = c.actionDelay;
    }

    // ---- pomocnicze ----

    private static GenericContainerScreenHandler handler(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen gs) return gs.getScreenHandler();
        return null;
    }

    private static boolean hasContent(GenericContainerScreenHandler h) {
        int n = h.getRows() * 9;
        for (int i = 0; i < n; i++) if (!h.slots.get(i).getStack().isEmpty()) return true;
        return false;
    }

    private static void click(MinecraftClient mc, GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private static String lname(ItemStack st) {
        return st.getName().getString().toLowerCase(Locale.ROOT);
    }

    /** Przycisk (nie oferta) o nazwie zawierajacej fragment. */
    private static int findButton(GenericContainerScreenHandler h, String stem) {
        int n = h.getRows() * 9;
        for (int i = 0; i < n; i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (st.isEmpty() || Market.parsePrice(st) != null) continue;
            if (lname(st).contains(stem)) return i;
        }
        return -1;
    }

    private static int findNext(GenericContainerScreenHandler h) {
        int n = h.getRows() * 9;
        for (int i = 0; i < n; i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (st.isEmpty() || Market.parsePrice(st) != null) continue;
            String nm = lname(st);
            if (nm.contains("nast") || nm.contains("dalej") || nm.contains("next")
                    || nm.contains("→") || nm.contains("»") || nm.contains("➡")) return i;
        }
        return -1;
    }

    private static int findConfirm(GenericContainerScreenHandler h, boolean newGui) {
        int n = h.getRows() * 9;
        for (int i = 0; i < n; i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (st.isEmpty()) continue;
            String nm = lname(st);
            if (nm.contains("potwierd") || nm.contains("confirm") || nm.contains("akcept")
                    || nm.contains("kupuj") || nm.trim().equals("tak")) return i;
        }
        if (newGui) {
            for (int i = 0; i < n; i++) {
                ItemStack st = h.slots.get(i).getStack();
                if (st.isEmpty()) continue;
                if (st.isOf(Items.LIME_STAINED_GLASS_PANE) || st.isOf(Items.GREEN_STAINED_GLASS_PANE)
                        || st.isOf(Items.LIME_CONCRETE) || st.isOf(Items.GREEN_CONCRETE)
                        || st.isOf(Items.LIME_WOOL) || st.isOf(Items.GREEN_WOOL)
                        || st.isOf(Items.EMERALD_BLOCK)) return i;
            }
        }
        return -1;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§6[Okazje] §f" + s), false);
    }

    /** Zrzut zawartosci GUI do pliku anarchiadeals_dump.txt (w folderze gry) - do diagnozy. */
    private static void dump(MinecraftClient mc, GenericContainerScreenHandler h, String when) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("==== ").append(java.time.LocalDateTime.now()).append(" | ").append(when)
                    .append(" | tytul: ").append(mc.currentScreen == null ? "?" : mc.currentScreen.getTitle().getString())
                    .append(" | rzedy: ").append(h.getRows()).append('\n');
            for (int i = 0; i < h.getRows() * 9; i++) {
                ItemStack st = h.slots.get(i).getStack();
                if (st.isEmpty()) continue;
                sb.append("slot ").append(i).append(": ").append(Registries.ITEM.getId(st.getItem()))
                        .append(" x").append(st.getCount()).append(" | ").append(st.getName().getString()).append('\n');
                LoreComponent lore = st.get(DataComponentTypes.LORE);
                if (lore != null) for (Text t : lore.lines()) sb.append("        ").append(t.getString()).append('\n');
                sb.append("        -> cena=").append(Market.parsePrice(st)).append('\n');
            }
            Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("anarchiadeals_dump.txt");
            Files.writeString(f, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            AnarchiaDeals.LOGGER.warn("Dump nieudany", e);
        }
    }
}
