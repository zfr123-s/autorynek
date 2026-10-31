# Anarchia Deals (Fabric, Minecraft 1.21.4)

Mod kliencki: klawisz **Y** otwiera menu. Skaner wchodzi w `/ah`, wybiera kategorię
(narzędzia / uzbrojenia / ksiąg / specjalne), ustawia sortowanie na **Najnowsze**, czyta
oferty (cena z lore), liczy średnią cenę za sztukę dla każdego przedmiotu (id + zaklęcia)
i wyłapuje oferty tańsze od średniej o co najmniej X%. Opcjonalnie kupuje.

## Budowanie
1. Zainstaluj JDK 21 oraz Gradle 8.12 (albo otwórz folder w IntelliJ IDEA).
2. W folderze projektu: `gradle build`
3. Gotowy mod: `build/libs/anarchiadeals-1.0.0.jar` (NIE plik `-sources`).
4. Wrzuć do `mods/` razem z Fabric Loader (1.21.4) i Fabric API.

## Użycie
- Y -> menu. Najpierw zostaw auto-kupowanie wyłączone i pozwól modowi zebrać ceny
  (minimum "Min. próbek ceny" ofert danego przedmiotu), potem włącz je.
- Y podczas skanowania zatrzymuje skaner.
- Config: `config/anarchiadeals.json` (m.in. maxPrice, maxBuys, cycleDelay).
- Baza cen: `config/anarchiadeals_prices.json`.

## Diagnostyka
Układ GUI serwera nie jest znany z góry (nazwy przycisków, format ceny, okno potwierdzenia).
Przycisk "Zrzut GUI" (oraz każdy start) zapisuje zawartość /ah do pliku
`anarchiadeals_dump.txt` w folderze gry - jeśli coś nie działa, to z niego da się to poprawić.
