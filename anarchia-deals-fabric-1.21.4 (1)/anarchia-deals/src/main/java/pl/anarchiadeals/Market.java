package pl.anarchiadeals;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsowanie ofert z GUI /ah: cena, klucz przedmiotu, wykrywanie wybranego sortowania. */
public final class Market {
    private Market() {}

    public static final class Listing {
        public int slot;
        public String name, key, fp;
        public double price, unit, avg, disc;
        public int count;
    }

    private static final String NUM = "(\\d[\\d\\s.,]*)(?:\\s*([kKmM])(?!\\p{L}))?";
    private static final Pattern P_WORD = Pattern.compile("(?i)(?:cen[aąy]|price|koszt)\\D{0,25}?" + NUM);
    private static final Pattern P_DOLLAR_AFTER = Pattern.compile(NUM + "\\s*\\$");
    private static final Pattern P_DOLLAR_BEFORE = Pattern.compile("\\$\\s*" + NUM);

    /** Zwraca cene calej oferty (lub null, jesli przedmiot nie wyglada na oferte). */
    public static Double parsePrice(ItemStack st) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        for (Pattern p : new Pattern[]{P_WORD, P_DOLLAR_AFTER, P_DOLLAR_BEFORE}) {
            for (Text t : lore.lines()) {
                Matcher m = p.matcher(t.getString());
                if (m.find()) {
                    double v = num(m.group(1), m.group(2));
                    if (v > 0) return v;
                }
            }
        }
        return null;
    }

    static double num(String s, String suffix) {
        s = s.trim().replaceAll("\\s+", "").replaceAll("[.,]+$", "");
        if (s.isEmpty()) return -1;
        int lc = s.lastIndexOf(','), ld = s.lastIndexOf('.');
        if (lc >= 0 && ld >= 0) {
            char dec = lc > ld ? ',' : '.';
            char th = dec == ',' ? '.' : ',';
            s = s.replace(String.valueOf(th), "").replace(dec, '.');
        } else if (lc >= 0 || ld >= 0) {
            char sep = lc >= 0 ? ',' : '.';
            int cnt = 0;
            for (char ch : s.toCharArray()) if (ch == sep) cnt++;
            int last = s.lastIndexOf(sep);
            boolean thousands = cnt > 1 || s.length() - last - 1 == 3;
            if (thousands) s = s.replace(String.valueOf(sep), "");
            else s = s.replace(sep, '.');
        }
        try {
            double v = Double.parseDouble(s);
            if (suffix != null) {
                switch (suffix.toLowerCase(Locale.ROOT)) {
                    case "k" -> v *= 1_000;
                    case "m" -> v *= 1_000_000;
                    default -> { }
                }
            }
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Listing parse(int slot, ItemStack st) {
        Double p = parsePrice(st);
        if (p == null) return null;
        Listing l = new Listing();
        l.slot = slot;
        l.name = st.getName().getString();
        l.count = Math.max(1, st.getCount());
        l.price = p;
        l.unit = p / l.count;
        l.key = key(st);
        StringBuilder sb = new StringBuilder();
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore != null) for (Text t : lore.lines()) sb.append(t.getString().replaceAll("\\d+", "#")).append('\n');
        l.fp = l.key + "|" + p + "|" + sb.toString().hashCode();
        return l;
    }

    /** Klucz przedmiotu do liczenia sredniej: id + zaklecia. */
    public static String key(ItemStack st) {
        StringBuilder sb = new StringBuilder(Registries.ITEM.getId(st.getItem()).toString());
        List<String> en = new ArrayList<>();
        collect(st.get(DataComponentTypes.ENCHANTMENTS), en);
        collect(st.get(DataComponentTypes.STORED_ENCHANTMENTS), en);
        Collections.sort(en);
        if (!en.isEmpty()) sb.append('|').append(String.join(",", en));
        return sb.toString();
    }

    private static void collect(ItemEnchantmentsComponent c, List<String> out) {
        if (c == null) return;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : c.getEnchantmentEntries()) {
            String id = e.getKey().getKey().map(k -> k.getValue().toString()).orElse("?");
            out.add(id + ":" + e.getIntValue());
        }
    }

    /** Czy przycisk sortowania ma aktualnie wybrane "Najnowsze"? (kilka heurystyk) */
    public static boolean newestSelected(ItemStack st) {
        if (st.getName().getString().toLowerCase(Locale.ROOT).contains("najnowsz")) return true;
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        List<Text> ls = lore.lines();
        int idx = -1;
        for (int i = 0; i < ls.size(); i++) {
            if (ls.get(i).getString().toLowerCase(Locale.ROOT).contains("najnowsz")) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        String s = ls.get(idx).getString().trim();
        if (!s.isEmpty() && "▶►»>✔✓●•→➤➜☑".indexOf(s.charAt(0)) >= 0) return true;
        Integer c = color(ls.get(idx));
        if (c == null) return false;
        Map<Integer, Integer> freq = new HashMap<>();
        for (int i = 0; i < ls.size(); i++) {
            if (i == idx || ls.get(i).getString().isBlank()) continue;
            Integer o = color(ls.get(i));
            if (o != null) freq.merge(o, 1, Integer::sum);
        }
        if (freq.isEmpty()) return false;
        int common = 0, best = -1;
        for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                common = e.getKey();
            }
        }
        return c != common;
    }

    private static Integer color(Text t) {
        if (t.getStyle().getColor() != null) return t.getStyle().getColor().getRgb();
        for (Text s : t.getSiblings()) {
            Integer c = color(s);
            if (c != null) return c;
        }
        return null;
    }
}
