# Roadmapa Projektu: Corsair Deluxe Remake

**Projekt:** Corsair Deluxe Remake
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
* [x] System Mgły Wojny (Fog of War) – zasłanianie nieodkrytych kafelków i odkrywanie ich w zasięgu wzroku statku.
* [x] HUD ekranu mapy: przycisk lupy (rozglądanie się) oraz przycisk wyjścia do menu głównego, działający tak samo jak systemowy przycisk wstecz.

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

## ⚔️ Faza 3: Bitwa w stylu Piracy Deluxe (turowa potyczka na „sieci")

**Referencja designowa:** [Piracy Deluxe (Amiga, 1994)](https://www.gamesthatwerent.com/2025/12/piracy-deluxe/) – klasyczna, turowa potyczka abordażowa rozgrywana na planszy-sieci.

### 3.0 Założenie designowe
* [x] Bitwa to statyczna, turowa gra logiczna (bez akcji w czasie rzeczywistym), rozgrywana na osobnym ekranie po wejściu statku gracza na pole przeciwnika.
* [x] Dwie strony stoją na przeciwnych krawędziach siatce 6x5 (gracz po lewej, wróg po prawej; każdy statek ma 5 luków, po jednym na rząd); załoga czeka w rezerwie za lukami – gracz startuje z 2 piratami na luk (10), wróg z 1 (5).

### 3.1 Zasady (zaimplementowane w `domain.battle.BattleState`)
* [x] Otwieranie luków jest darmowe i działa jak przełącznik (toggle) - można mieć otwartych kilka luków naraz, bez zużywania tury; po wyprowadzeniu załogi wszystkie otwarte luki zamykają się automatycznie (kolejna strzałka to znów skok).
* [x] Tura = jedna z dwóch akcji: **wyprowadzenie załogi** (każdy otwarty luk wypuszcza po 1 piracie na pole wejściowe, maks. 5 naraz; nic, co już jest na sieci, się nie rusza) albo **skok** całej załogi na sieci o 1 pole: do przodu albo na ukos do przodu (w górę lub w dół).
* [x] Pirat, który dojdzie do kolumny luków przeciwnika, zawsze atakuje luk na wprost (w swoim rzędzie) i jest przy tym zużyty - niezależnie od tego, czy zadano skok prosto, czy na ukos; pozostała załoga wykonuje zadany skok normalnie.
* [x] Dziury w sieci generowane symetrycznie punktowo (uczciwy start); ich liczba rośnie z uszkodzeniem kadłuba gracza.
* [x] Wygrywa strona, która pozbawi przeciwnika załogi lub użytecznych luków; po 80 turach decyduje liczebność załogi.
* [x] Skala sił zależy od załogi statku (1 jednostka = 3 marynarzy, 3–9 jednostek).

### 3.2 Ekran bitwy (`BattleScreen`)
* [x] Kółka przy lukach pokazują piratów pozostałych w luku (po jego zewnętrznej stronie, od góry); brak kółka = pirat już wyszedł na siatkę; otwarty luk świeci na złoto i pokazuje pierścień na polu wejściowym.
* [x] Dotykowy interfejs: tapnięcie własnego luku przełącza go (otwarty/zamknięty), trzy strzałki (do przodu / ukos w górę / ukos w dół) = wyprowadzenie załogi z otwartych luków, a gdy wszystkie są zamknięte - skok; po wyprowadzeniu luki zamykają się same; przycisk ucieczki.
* [x] Panel statusu: rezerwa, załoga na sieci, sprawne luki, numer tury, log ostatniej akcji.
* [x] Czcionka bitwy rysowana w skali co najmniej 2x (panel statusu u góry, log u dołu), z filtrem liniowym i automatycznym zmniejszeniem na bardzo małych ekranach; układ planszy i przycisków liczy się od zmierzonej wysokości tekstu.
* [x] Powrót na mapę świata: straty załogi, łup za zwycięstwo (rum, żywność), usunięcie pokonanego przeciwnika, cofnięcie przy ucieczce/remisie; porażka wraca do menu głównego.

### 3.3 Dalszy rozwój bitwy
* [ ] Animacje skoków i efekty (dźwięk, cząsteczki).
* [ ] Wpływ cech statku/załogi na bitwę (morale, bonusy kapitana, rodzaje przeciwników).
* [ ] Ekran podsumowania z łupem (ładunek, złoto, przejęcie statku).
* [ ] Poziomy trudności SI (głębokość przeszukiwania).

---

## 🤖 Faza 4: Sztuczna Inteligencja (SI) i Zapis Stanu Gry (Tydzień 9 – 12)

### 4.1 Sztuczna Inteligencja (AI)
* [ ] **SI na Mapie Świata:** Statki pirackie i patrolowe polujące na gracza lub inne jednostki, z zachowaniem ruchu po open ocean i w pobliżu wysp.
* [x] **SI w Bitwie:** przeciwnik oparty na minimaksie z cięciami alfa-beta (`BattleAI`), wybierający załadunek lub kierunek skoku.

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
* [x] Panel "Opcje" w menu głównym (`GameSettings` + `AndroidSettings`): przełączniki dźwięku i muzyki (gotowe pod przyszłą ścieżkę audio), trójstanowy poziom trudności (steruje głębokością przeszukiwania AI i liczbą piratów przeciwnika w lukach) oraz tryb debug (współrzędne kafelków na mapie i tymczasowy przycisk skrótu do bitwy). Ustawienia zapisywane w `SharedPreferences`.
* [ ] Testy jednostkowe kluczowych mechanik (HexMath, Pathfinding, Battle resolution).

### 5.3 Przygotowanie do Publikacji
* [ ] Wygenerowanie podpisanej paczki wydaniowej (Android App Bundle - `.aab`).
* [ ] Przygotowanie grafik sklepowych (zrzuty ekranu, ikona aplikacji, baner).
* [ ] Publikacja na Google Play Store.