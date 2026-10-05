# Roadmapa Projektu: Piracy Deluxe Remake

**Projekt:** Piracy Deluxe Remake
**Platforma:** Android (telefony & tablety)
**Technologia:** Kotlin, Android Studio, libGDX (Gdx-Liftoff)
**Ścieżka pliku:** `D:\moje\Piracy game\.meta\roadmap.md`

---

## 🚩 Faza 1: Inicjalizacja i Architektura (Zrobione)

### 1.1 Środowisko i Struktura Projektu
* [x] Wygenerowanie projektu bazowego za pomocą **gdx-liftoff** z modułami `core`, `android` oraz `desktop` (dla szybkiego testowania na PC).
* [x] Wybór i integracja prostego kontenera zależności / modułu bootstrapu gry (`GameModule` i podstawowy model stanu świata).
* [x] Konfiguracja obsługi wielkości ekranów i proporcji (FitViewport 16:9 / Landscape) w `AndroidLauncher` oraz klasach ekranów.
* [x] Podział modułu `core` na pakiety Clean Architecture (`domain`, `data`, `presentation`, `core`).

---

## 🌊 Faza 2: Prototyp Core Gameplayu – Siatka Hexagonalna i Żegluga (Zrobione w prototypie)

Główny koncept gry zakłada dwie odrębne, ale powiązane płaszczyzny rozgrywki:
- pierwsza płaszczyzna: żeglowanie po mapie świata, eksploracja i nawigacja; to jest warstwa "poruszania się statkiem" i inicjacji starcia;
- druga płaszczyzna: statyczna gra logiczna rozstrzygająca bitwę po wejściu na ekran walki; tutaj nie ma dynamicznych akcji typu salwa, żagiel, kurs w sensie real-time, lecz rozstrzygające decyzje logiczne w turach.

Ta separacja ma pozwolić na odrębny design zarówno eksploracji, jak i walki, bez mieszania mechanik mapy świata z mechanikami taktycznymi. Warstwa 1 odpowiada za ruch i wstępne spotkanie, warstwa 2 odpowiada wyłącznie za rozstrzygnięcie konfliktu.

### 2.1 System Mapy Hexagonalnej (HexGrid)
* [x] Zaimplementowanie struktury danych dla układu współrzędnych axialnych $(q, r)$ i cube $(x, y, z)$.
* [x] Matematyczna przelicznica: układy współrzędnych hex $\leftrightarrow$ pozycje pikselowe $(x, y)$ ekranu.
* [x] Generator testowej mapy morskiej (Woda, Wyspy, Płytka woda, płycizny).
* [x] Implementacja algorytmu A* dla wyszukiwania ścieżek na siatce z uwzględnieniem kosztów poruszania się.

### 2.2 Kamery, Renderowanie i Input
* [x] Renderowanie kafelków hex w prototypowej wersji przy użyciu `ShapeRenderer`.
* [x] Obsługa kamery i sterowania w trybie desktopowym.
* [x] Wskaźnik zaznaczenia pola (Highlight tile) i rysowanie ścieżki planowanego ruchu statku.
* [ ] System Mgły Wojny (Fog of War) – zasłanianie nieodkrytych kafelków i odkrywanie ich w zasięgu wzroku statku.

### 2.3 Mechanika Pływania i Pętla Turowa
* [x] Klasa statku gracza (punkty ruchu/punkty akcji, zdrowie kadłuba, załoga, zaopatrzenie).
* [x] Prototyp ruchu statku po wyznaczonej ścieżce hexów.
* [ ] Przycisk "Koniec Tury" / automatyczne kończenie ruchu – aktualizacja wskaźników (upływ dni, zużycie racji żywnościowych i rumu).

---

## ⚠️ Obecny zakres projektu

Na tym etapie projekt skupia się wyłącznie na dwóch podstawowych płaszczyznach rozgrywki:
- żeglowanie po mapie świata i eksploracja;
- właściwa bitwa morska w osobnym, taktycznym środowisku.

Porty, handel i ekonomia są odłożone na późniejsze, opcjonalne rozszerzenie i nie są częścią obecnego etapu rozwoju.

---

## ⚔️ Faza 3: Bitwa Morska w stylu Piracy Deluxe (statyczna, turowa gra logiczna)

### 3.0 Założenie designowe: bitwa jest statyczna i logiczna
* [x] Odrzucenie klasycznego, płynnego „real-time combat” na rzecz statycznej, turowej gry logicznej inspirowanej układem z **Piracy Deluxe**.
* [ ] Po wejściu w konflikt gra przechodzi do osobnego ekranu walki, który jest statycznym panelem rozstrzygającym wynik starcia. W tej warstwie nie ma ruchu jednostek po planszy ani akcji typu salwa / kurs / żagiel w sensie real-time.
* [ ] Rozgrywka opiera się na turach: wybór akcji, rozstrzygnięcie logiki, następna tura, bez szybkiej akcji na czas.
* [ ] Mapa bitwy ma charakter panelu decyzyjnego: pozycje statków, burty, niszczenie kadłuba, straty załogi i dostępne decyzje są odczytywane jako dane logiczne, a nie fizyczne.
* [ ] Działania w tej warstwie są jedynie narzędziem rozstrzygnięcia starcia; nie pełnią funkcji „walki w czasie”.

### 3.1 Arena Taktyczna Bitwy
* [ ] Inicjalizacja dedykowanego ekranu `BattleScreen` po wykryciu wrogiego statku na mapie świata.
* [ ] Generowanie statycznej, mniejszej planszy bitwy z prostą geometrią i widokiem podobnym do ekranu z referencji.
* [ ] System wiatru jako statyczny parametr rundy: kierunek i siła wiatru wpływają na wynik działań i dostępność ruchu, ale nie powodują płynnej mechaniki z odrywaniem od planszy.

### 3.2 Mechanika Rozstrzygająca Bitwę
* [ ] Wyznaczanie stref ostrzału (lewa burta, prawa burta, przód, tył) jako danych logicznych dla rozliczenia tur.
* [ ] Wybór akcji w turze z użyciem prostych, statycznych parametrów konfliktu, np. atak, obrona, manewr, abordaż, zrezygnowanie z walki.
* [ ] Obliczanie szansy na trafienie i obrażeń w oparciu o odległość, pozycję względem wroga, stan statku i aktualne morale załogi.

### 3.3 Abordaż i Finał Bitwy
* [ ] Wybór akcji abordażu jako oddzielnej, logicznej decyzji w turze, zamiast dynamicznej kolizji na mapie.
* [ ] Rozstrzygnięcie walki wręcz na podstawie liczebności załogi, morale i aktualnego stanu jednostek.
* [ ] Ekran podsumowania wygranej bitwy: przejmowanie łupu, zniszczenie lub zdobycie przeciwnika, z zastosowaniem prostego, statycznego rozliczenia logicznego.

---

## 🤖 Faza 4: Sztuczna Inteligencja (SI) i Zapis Stanu Gry (Tydzień 9 – 12)

### 4.1 Sztuczna Inteligencja (AI)
* [ ] **SI na Mapie Świata:** Statki pirackie i patrolowe polujące na gracza lub inne jednostki, z zachowaniem ruchu po open ocean i w pobliżu wysp.
* [ ] **SI w Bitwie Taktycznej:** Podejmowanie decyzji o manewrowaniu w celu uderzenia pełną salwą burtową, ucieczka przy znacznych uszkodzeniach lub dążenie do abordażu.

### 4.2 System Zapisów i Odczytu (Persistence)
* [ ] Integracja z **Kotlinx.Serialization** lub bazą danych **Room**.
* [ ] Serializacja stanu świata: pozycja gracza, stan statków, historia odkrytych kafelków (Fog of War) i stan sakiewki.
* [ ] System slotów zapisu oraz niezawodny Auto-Save aktywowany przy zawieszeniu aplikacji (Android `onPause`).

---

## 🎨 Faza 5: Dźwięk, UI/UX, Polish i Wydanie (Tydzień 13 – 15)

### 5.1 Oprawa Graficzna i Audio
* [ ] Integracja spójnego zestawu grafik (spritesheet dla statków, ikony towarów, kafle wysp i wody).
* [ ] Efekty cząsteczkowe w libGDX (ślad wody za statkiem, dym z armat, wybuchy).
* [ ] Efekty dźwiękowe (strzały, szum fal, odgłosy tawerny) i podkład muzyczny.

### 5.2 Optymalizacja i Testy
* [ ] Profilowanie zużycia pamięci RAM oraz płynności (stałe 60 FPS na urządzeniach mobilnych).
* [ ] Dostosowanie interfejsu (skalowanie czcionek i przycisków) pod ekrany smartfonów oraz tabletów.
* [ ] Testy jednostkowe kluczowych mechanik (HexMath, Pathfinding, Battle resolution).

### 5.3 Przygotowanie do Publikacji
* [ ] Wygenerowanie podpisanej paczki wydaniowej (Android App Bundle - `.aab`).
* [ ] Przygotowanie grafik sklepowych (zrzuty ekranu, ikona aplikacji, baner).
* [ ] Publikacja na Google Play Store.