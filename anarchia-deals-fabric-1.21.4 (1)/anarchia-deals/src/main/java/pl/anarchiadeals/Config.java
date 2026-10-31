package pl.anarchiadeals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public boolean autoBuy = false;      // domyslnie wylaczone - wlacz w menu (Y), gdy sprawdzisz ze parsowanie dziala
    public int discountPercent = 30;     // minimalna okazja wzgledem sredniej (%)
    public int minSamples = 5;           // ile ofert danego przedmiotu trzeba znac, zeby liczyc srednia
    public int maxSamples = 60;          // ile ostatnich cen pamietac na przedmiot
    public int pages = 3;                // ile stron skanowac w kazdej kategorii
    public int actionDelay = 8;          // opoznienie miedzy akcjami (ticki, 20 = 1 s)
    public int cycleDelay = 100;         // przerwa miedzy pelnymi cyklami (ticki)
    public int maxBuys = 20;             // limit zakupow na jedno uruchomienie
    public double maxPrice = 0;          // maks. cena jednego zakupu, 0 = bez limitu
    public boolean catTools = true;
    public boolean catArmor = true;
    public boolean catBooks = true;
    public boolean catSpecial = true;

    private static Config instance = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anarchiadeals.json");

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            AnarchiaDeals.LOGGER.warn("Nie udalo sie wczytac configu", e);
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            AnarchiaDeals.LOGGER.warn("Nie udalo sie zapisac configu", e);
        }
    }
}
