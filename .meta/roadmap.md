# Roadmapa Projektu: Piracy Deluxe Remake

**Projekt:** Piracy Deluxe Remake
**Platforma:** Android (telefony & tablety)
**Technologia:** Kotlin, Android Studio, libGDX (Gdx-Liftoff)
**Ścieżka pliku:** `D:\moje\Piracy game\.meta\roadmap.md`

---

## 🚩 Faza 1: Inicjalizacja i Architektura (Zrobione / W trakcie)

### 1.1 Środowisko i Struktura Projektu
* [x] Wygenerowanie projektu bazowego za pomocą **gdx-liftoff** z modułami `core`, `android` oraz `desktop` (dla szybkiego testowania na PC).
* [ ] Wybór i integracja kontenera wstrzykiwania zależności (Koin).
* [ ] Konfiguracja obsługi wielkości ekranów i proporcji (FitViewport 16:9 / Landscape) w `AndroidLauncher` oraz klasach ekranów.
* [ ] Podział modułu `core` na pakiety Clean Architecture (`domain`, `data`, `presentation`, `core`).

---

## 🌊 Faza 2: Prototyp Core Gameplayu – Siatka Hexagonalna i Żegluga (Tydzień 2 – 4)

Główny koncept gry zakłada dwie odrębne, ale powiązane płaszczyzny rozgrywki:
- pierwsza płaszczyzna: żeglowanie po mapie świata, eksploracja, nawigacja, inicjowanie starć i interakcje z portami;
- druga płaszczyzna: właściwa bitwa morska, rozgrywana w osobnym, taktycznym środowisku, po wykryciu wroga lub inicjacji abordażu.

Ta separacja ma pozwolić na odrębny design zarówno eksploracji, jak i walki, bez mieszania mechanik mapy świata z mechanikami taktycznymi.

### 2.1 System Mapy Hexagonalnej (HexGrid)
* [ ] Zaimplementowanie struktury danych dla układu współrzędnych axialnych $(q, r)$ i cube $(x, y, z)$.
* [ ] Matematyczna przelicznica: układy współrzędnych hex $\leftrightarrow$ pozycje pikselowe $(x, y)$ ekranu.
* [ ] Generator testowej mapy morskiej (Woda, Wysepki, Płytka woda, Porty).
* [ ] Implementacja algorytmu A* dla wyszukiwania ścieżek na siatce z uwzględnieniem kosztów poruszania się.

### 2.2 Kamery, Renderowanie i Input
* [ ] Renderowanie kafelków hex za pomocą `SpriteBatch` / `TextureAtlas`.
* [ ] Obsługa gestów dotykowych dla Androida: płynne przesuwanie palcem (Pan) oraz zbliżanie/oddalanie (Zoom / Pinch-to-zoom).
* [ ] Wskaźnik zaznaczenia pola (Highlight tile) i rysowanie ścieżki planowanego ruchu statku.
* [ ] System Mgły Wojny (Fog of War) – zasłanianie nieodkrytych kafelków i odkrywanie ich w zasięgu wzroku statku.

### 2.3 Mechanika Pływania i Pętla Turowa
* [ ] Klasa statku gracza (punkty ruchu/punkty akcji, zdrowie kadłuba, załoga, zaopatrzenie).
* [ ] Animacja płynnego przemieszczania się statku po wyznaczonej ścieżce hexów.
* [ ] Przycisk "Koniec Tury" / automatyczne kończenie ruchu – aktualizacja wskaźników (upływ dni, zużycie racji żywnościowych i rumu).

---

## ⚓ Faza 3: Porty, Gospodarka i System Handlu (Tydzień 5 – 7)

### 3.1 Interfejs Użytkownika Portu (Scene2D / VisUI)
* [ ] Stworzenie menedżera stref/widoków (przełączanie z `SailingScreen` do `PortScreen` po wpłynięciu do portu).
* [ ] Dedykowany HUD widoku portu z przyciskami: *Targ / Rynek*, *Tawerna*, *Stocznia*, *Gubernator / Zlecenia*.

### 3.2 Ekonomia i Targ Morski
* [ ] Model towarów: Rum, Cukier, Tytoń, Przyprawy, Kule armatnie, Drewno.
* [ ] Algorytm cenowy uzależniony od podaży/popytu w danym porcie oraz losowych zdarzeń (np. susza, zaraza, wojna).
* [ ] Okno dialogowe transakcji kupna/sprzedaży (suwaki ilości, wskaźniki pojemności ładowni, koszt całkowity).

### 3.3 Tawerna i Stocznia
* [ ] **Tawerna:** Werbowanie marynarzy (zwiększanie załogi), zbieranie plotek o szlakach handlowych, odnawianie morali.
* [ ] **Stocznia:** Naprawa uszkodzeń kadłuba i żagli, ulepszanie pojemności/dział, zakup nowych, większych statków (np. Sloop, Frigate, Galleon).

---

## ⚔️ Faza 4: Taktyczny System Bitwy Morskiej (Tydzień 8 – 11)

### 4.1 Arena Taktyczna Bitwy
* [ ] Inicjalizacja dedykowanego ekranu `BattleScreen` po wykryciu wrogiego statku na mapie świata.
* [ ] Generowanie mniejszej mapy taktycznej bitwy.
* [ ] System wiatru: losowanie kierunku i siły wiatru na początku bitwy; wpływ wiatru na liczbę punktów ruchu w zależności od ustawienia żagli.

### 4.2 Mechanika Salw Armatnich i Uszkodzeń
* [ ] Wyznaczanie stref ostrzału (lewa burta, prawa burta, przód).
* [ ] Wybór typu amunicji:
  * *Kule standardowe* – niszczenie kadłuba.
  * *Łańcuchy* – niszczenie żagli i odbieranie punktów ruchu.
  * *Kartonusze / Śrut* – eliminowanie załogi przeciwnika.
* [ ] Obliczanie szansy na trafienie i obrażeń w oparciu o dystans, wyszkolenie załogi i kąt.

### 4.3 Abordaż i Finał Bitwy
* [ ] Opcja podpłynięcia bezpośrednio do wroga i zainicjowania abordażu.
* [ ] Automatyczna lub pół-taktyczna rozdzielczość walki wręcz (porównanie liczebności załogi i morale).
* [ ] Ekran podsumowania wygranej bitwy: przejmowanie złota, towarów z ładowni oraz możliwość przejęcia/zatopienia statku wroga.

---

## 🤖 Faza 5: Sztuczna Inteligencja (SI) i Zapis Stanu Gry (Tydzień 12 – 14)

### 5.1 Sztuczna Inteligencja (AI)
* [ ] **SI na Mapie Świata:** Statki handlowe pływające między portami, statki pirackie/patrole wojskowe polujące na gracza lub siebie nawzajem.
* [ ] **SI w Bitwie Taktycznej:** Podejmowanie decyzji o manewrowaniu w celu uderzenia pełną salwą burtową, ucieczka przy znacznych uszkodzeniach lub dążenie do abordażu.

### 5.2 System Zapisów i Odczytu (Persistence)
* [ ] Integracja z **Kotlinx.Serialization** lub bazą danych **Room**.
* [ ] Serializacja stanu świata: pozycja gracza, stan statków, historia odkrytych kafelków (Fog of War), ekonomia portów i stan sakiewki.
* [ ] System slotów zapisu oraz niezawodny Auto-Save aktywowany przy zawieszeniu aplikacji (Android `onPause`) oraz przy wejściu do portu.

---

## 🎨 Faza 6: Dźwięk, UI/UX, Polish i Wydanie (Tydzień 15 – 17)

### 6.1 Oprawa Graficzna i Audio
* [ ] Integracja spójnego zestawu grafik (spritesheet dla statków, ikony towarów, kafle wysp i wody).
* [ ] Efekty cząsteczkowe w libGDX (ślad wody za statkiem, dym z armat, wybuchy).
* [ ] Efekty dźwiękowe (strzały, szum fal, odgłosy tawerny) i podkład muzyczny.

### 6.2 Optymalizacja i Testy
* [ ] Profilowanie zużycia pamięci RAM oraz płynności (stałe 60 FPS na urządzeniach mobilnych).
* [ ] Dostosowanie interfejsu (skalowanie czcionek i przycisków) pod ekrany smartfonów oraz tabletów.
* [ ] Testy jednostkowe kluczowych mechanik (TradeUseCase, HexMath, Pathfinding).

### 6.3 Przygotowanie do Publikacji
* [ ] Wygenerowanie podpisanej paczki wydaniowej (Android App Bundle - `.aab`).
* [ ] Przygotowanie grafik sklepowych (zrzuty ekranu, ikona aplikacji, baner).
* [ ] Publikacja na Google Play Store.