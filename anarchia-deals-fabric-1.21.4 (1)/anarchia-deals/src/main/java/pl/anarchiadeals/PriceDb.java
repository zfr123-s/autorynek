package pl.anarchiadeals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Baza cen jednostkowych: klucz przedmiotu -> ostatnie N cen. */
public final class PriceDb {
    private PriceDb() {}

    private static Map<String, ArrayList<Double>> data = new HashMap<>();
    private static boolean dirty = false;
    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anarchiadeals_prices.json");

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Map<String, ArrayList<Double>> m = GSON.fromJson(Files.readString(FILE),
                        new TypeToken<HashMap<String, ArrayList<Double>>>() {}.getType());
                if (m != null) data = m;
            }
        } catch (Exception e) {
            AnarchiaDeals.LOGGER.warn("Nie udalo sie wczytac bazy cen", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(data));
            dirty = false;
        } catch (Exception e) {
            AnarchiaDeals.LOGGER.warn("Nie udalo sie zapisac bazy cen", e);
        }
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    public static void add(String key, double unitPrice) {
        ArrayList<Double> l = data.computeIfAbsent(key, k -> new ArrayList<>());
        l.add(unitPrice);
        int max = Math.max(1, Config.get().maxSamples);
        while (l.size() > max) l.remove(0);
        dirty = true;
    }

    public static int count(String key) {
        ArrayList<Double> l = data.get(key);
        return l == null ? 0 : l.size();
    }

    public static double avg(String key) {
        ArrayList<Double> l = data.get(key);
        if (l == null || l.isEmpty()) return 0;
        double s = 0;
        for (double d : l) s += d;
        return s / l.size();
    }

    public static int items() {
        return data.size();
    }

    public static void clear() {
        data.clear();
        dirty = true;
        save();
    }
}
