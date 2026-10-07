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
* [x] Losowość ruchów przeciwnika zależna od poziomu trudności: `Difficulty` niesie `randomMoveChance`, którą `BattleAI` losuje przed każdym ruchem kapitana - im łatwiejszy poziom, tym więcej pomyłek (Łatwy 40%, Normalny 20%, Trudny 5%).
* [x] Numer wersji ("Wersja 1.0.0") w prawym dolnym narożniku menu głównego (`@string/version_label`); `versionName` w `android/build.gradle` podniesiony do `1.0.0` i utrzymywany w zgodzie z tą etykietą.
* [x] Nowy wygląd mapy świata: neutralnie szare tło, heksy wypełnione kolorem kategorii terenu (ląd / płytka woda / głęboka woda) oraz kolorowe granice przejść - piaskowy brzeg tam, gdzie ląd spotyka płytką wodę, jasna rafa między płytką a głęboką wodą i ciemne urwisko tam, gdzie ląd opada wprost w głębinę. Generator celowo zostawia część linii brzegowej bez pasa płytkiej wody, żeby klify występowały (decyzja hashowana po współrzędnych, więc układa się w ciągi wybrzeża, a nie pojedyncze heksy). Tryb debug zachowuje dawny, konturowy widok heksów z palety debugowej. Kafelki lądu otoczone lądem z każdej strony to gęsta roślinność (ciemna zieleń), a linia styku dwóch różnych terenów jest rysowana podwójną grubością.
* [x] Przycisk wyjścia do menu głównego przeniesiony na dół ekranu (prawy dolny narożnik); rysowanie i obsługa dotyku korzystają z tej samej funkcji pozycji, więc nie rozjeżdżają się.
* [ ] Testy jednostkowe kluczowych mechanik (HexMath, Pathfinding, Battle resolution).
* [x] Komunikat HUD "Mielizna!" ("Statek nie wplynie na lad") na górze ekranu przez ~2 s, gdy odmówiony ruch trafi w ląd; obsługuje tapnięcie, strzałki i `moveTo` (wspólna funkcja `isBlockedMove`), komunikat sam wygasa z płynnym zanikaniem, a tekst jest bez znaków diakrytycznych (domyślny `BitmapFont` nie ma polskich glifów).
* [x] Statek pływa tylko po wodzie: po wyłączeniu trybu debug heksy lądu są nieprzekraczalne dla `HexPathfinder` (zarówno jako cel, jak i jako skrót przez wyspę), a tapnięcie/strzałka w ląd są ignorowane. Statek stojący na lądzie (pozostałość po zabawie w trybie debug) może z niego odpłynąć, bo heks startowy nigdy nie jest blokowany. Z włączonym debugiem pływanie po lądzie działa jak dotąd.
* [x] Naprawa przygaszania heksów poza zasięgiem wzroku podczas wyświetlania komunikatu: libGDX' `SpriteBatch.end()` wyłącza blending (`glDisable(GL_BLEND)`), a `begin()` go nie włącza z powrotem (blending zapala dopiero `flush()`, czyli faktyczne rysowanie), więc klatka kończąca się napisem zostawiała mapę bez przezroczystości. `WorldScreen` ma teraz pomocnicze `enableAlphaBlending()` wołane po każdym `spriteBatch.end()` i na starcie `render()` (zaraz po `ScreenUtils.clear`), dzięki czemu heksy zawsze rysują się z zadaną alfą.
* [x] Heks z własnym statkiem jest malowany jak każdy inny kafelek (teren, granice przejść, mgła), a wyróżnia go czerwony pierścień `SHIP_FRAME_RADIUS = 2 * HEX_SPACING - TILE_RADIUS`: jego krawędź trafia dokładnie w krawędź sąsiednich heksów, więc wypełnia szczelinę między kaflami wokół statku (weryfikacja numeryczna: 0 próbek szczeliny w promieniu 0.80 bez pokrycia, 0 nakładania na wypełnienia sąsiadów). Pierścień rysowany jest po całej mapie, żeby żadna granica sąsiada go nie przebijała, i zastępuje żółtą obwódkę zaznaczenia, gdy zaznaczony jest kafelek statku.
* [x] Statek na mapie to model rysowany od góry (kadłub, pokład, nadbudówka rufowa, dwa żagle rejowe z pasami, sztaksiel, maszty, bukszpryt, bandera) w palecie ikony aplikacji i splash screenu: granatowy kadłub `#3A3C6F`, kremowy pokład, białe płótno; wskaźnikiem gracza jest złota bandera, a statek wroga ma czarne płótno i czerwone akcenty. Kształty trzymają się w promieniu 0.6062 (inradius obrysu heksa, czyli wewnętrzna krawędź czerwonego pierścienia), więc pierścień nigdy nie jest zasłaniany - sprawdzone dla 360 obrotów (najmniejszy zapas 0.056). Kadłub obraca się zgodnie z kursem (`shipHeadingDeg`, wygładzane `MathUtils.lerpAngleDeg`), a wróg celuje dziobem w gracza. Rysowanie przeniesione z trybu `Line` (kółka) do `Filled` (przez `translate`/`rotate`/`identity`), trasa pozostaje w trybie `Line`.

* [x] Dopracowanie warstwy prezentacji mapy: (1) granice heksów rysowane są jako trapezy (`drawHexEdge`) z końcami wydłużonymi o połowę szerokości pasa, więc pasy dwóch krawędzi schodzących się w wierzchołku zachodzą na siebie i nie zostawiają niedomknięć na styku (prostokąt z `rectLine` kończył się dokładnie w wierzchołku; weryfikacja numeryczna: 0 niepokrytych próbek wokół wierzchołka dla szerokości 1x/2x/4x); (2) czerwony pierścień wokół statku jest węższy (`SHIP_FRAME_WIDTH = 0.12`) i kotwiczony do paska łączenia - jego wewnętrzna krawędź pokrywa się z wewnętrzną krawędzią pasa przejścia (przeliczenie przez `HEX_APOTHEM_RATIO = cos 30°`, bo pierścień jest heksem), z ograniczeniem `SHIP_FRAME_MAX_INSET`, żeby przy mocnym oddaleniu kamery nie wcinał się w statek; (3) rufowa nadbudówka jest deskowana tym samym drewnem co resztą pokładu (`SHIP_CASTLE` rysowany kolorem `deck`) - zniknęła granatowa plama na rufie.

* [x] Jednolita szerokość granic heksów: `EDGE_SCALE = 3.0` obowiązuje teraz wszystkie krawędzie (wcześniej 2x dla styku tego samego terenu i 4x dla przejścia między terenami). Jasne pasy przejść (rafa, piasek, urwisko) były dwa razy grubsze od ciemnych granic wody, przez co styk wyglądał nierówno - teraz szerokość jest wspólna, a różnicę robi wyłącznie kolor. Czerwony pierścień statku jest kotwiczony do tej samej wartości, więc jego wewnętrzna krawędź nadal pokrywa się z pasem granicy.

* [x] Granice heksów leżą w całości poza obrysem kafla: `drawHexEdge` stawia wewnętrzną ścianę pasa dokładnie na krawędzi heksa i wypuszcza całą szerokość (`EDGE_SCALE * lineThickness`) na zewnątrz, więc pas nie zjada już koloru terenu od środka (wcześniej był wyśrodkowany na obrysie). Kierunek na zewnątrz wyznacza obrót wersora krawędzi o 90 stopni zgodnie z ruchem wskazówek zegara, bo wierzchołki heksa idą przeciwnie do wskazówek. Końce pasa pozostają wydłużone o połowę szerokości, żeby narożnik nie zostawiał niedomknięcia. Szerokość ogranicza stała `MAX_EDGE_BAND = 0.173` - dokładnie odstęp między obrysami dwóch sąsiednich kafli - bo pasy są w jednostkach świata, a przy oddalonej kamerze pas szerszy niż szczelina wchodziłby na wypełnienie sąsiadów. Weryfikacja numeryczna (próbkowanie trójkątów pasa): 0 próbek wewnątrz własnego heksa i 0 wewnątrz któregokolwiek z sześciu sąsiadów dla szerokości 0.0366-0.173 (najmniejszy zapas do obrysu sąsiada 0.0002 przy szerokości granicznej), a przy 0.20 pas wchodzi już na sąsiadów (2586 próbek), co potwierdza sens limitu.

* [x] Naprawa "zdeformowanych" heksow. Granice byly pasami kotwiczonymi poza wlasnym obrysem, wiec oba sasiadujace kafle malowaly ten sam styk - kazdy wlasnym kolorem i wlasna alfa - a pasy nie spotykaly sie w polowie szczeliny. Na zrzucie zostawalo 2-5 px tla i niedopasowana para kolorow, przy wierzcholkach pas mial 30 px zamiast 15 px, a czerwony pierscien statku wyszedl 250x230 px (szerszy niz wyzszy, choc heks pointy-top jest wyzszy niz szerszy) - dlatego heksy czytaly sie jako przesuniete i rozciagniete. Teraz `drawHexEdge` rysuje granice jako wycinek pierscienia miedzy wypelnieniem kafla (promien `TILE_RADIUS`) a krawedzia jego komorki siatki, czyli `BORDER_WIDTH = HEX_SPACING - TILE_RADIUS = 0.10`. Sasiadujace kafle pokrywaja wspolny styk dokladnie po polowie, wiec nie ma ani szczeliny, ani nakladania, a szerokosc granicy jest stala i nie zmienia sie z przyblizeniem kamery (zbedne zrobily sie `EDGE_SCALE`, `MAX_EDGE_BAND`, `SHIP_FRAME_MAX_INSET` i `HEX_APOTHEM_RATIO`). Czerwony pierscien statku ma dokladnie szerokosc granicy (`SHIP_FRAME_WIDTH = BORDER_WIDTH`) i wypelnia rim wlasnego heksa, nie wchodzac na sasiadow. Zaznaczony kafelek jest teraz malowany terenowo i dodatkowo obrysowany na zolto (wczesniej obwodka zastepowala malowanie i zostawiala w mapie dziure bez pasa granicy). Weryfikacja numeryczna (probkowanie rima co 0.001): 388808 z 389406 probek pokryte dokladnie raz, 0 pokrytych dwukrotnie, 1 probka (szum floatow na wspolnej krawedzi) w komorce sasiada.

* [x] Cienka linia oddzielajaca sasiadujace heksy: pas granicy nie dochodzi juz do krawedzi swojej komorki siatki, lecz konczy sie `HEX_SEPARATOR = 0.01` wczesniej (`BORDER_WIDTH = HEX_SPACING - TILE_RADIUS - HEX_SEPARATOR = 0.09`). Kazdy z dwoch sasiadow zostawia te sama rezerwe, wiec miedzy ich pasami biegnie pasek tla (w poprzek wspolnej krawedzi 2 * 0.01 * cos 30 = 0.0173 jednostki swiata), dzieki czemu pojedyncze heksy sa czytelne i nie zlewaja sie w jedna plame. Szerokosc czerwonego pierscienia statku nadaza za pasem (`SHIP_FRAME_WIDTH = BORDER_WIDTH`), wiec jego zasieg tez konczy sie przed komorka sasiada. Weryfikacja numeryczna (probkowanie rima co 0.0015): 0 probek pokrytych dwukrotnie, niepokryte 18869 z 173735 - dokladnie paski separatora, ktorych pole 0.0425 zgadza sie z geometria 6 krawedzi * 0.79 * 0.0087.

* [x] Tlo ekranu bitwy: niebo i morze. Niebo i wode maluje sie pasmami wysokosci jednego piksela (`drawSeaAndSky`), bo grubsze pasma skacza o cala roznice koloru i czytaja sie jako prazki; paleta to `SKY_POS`/`SKY_RGB` i `SEA_POS`/`SEA_RGB`. Nad horyzontem lezy nieprzezroczysta, wstepnie zmiksowana mgielka (30% bieli zanikajacej do gory) domknieta jasna krawedzia horyzontu, wyzej slonce (26 pierscieni blasku, 5 pierscieni poswiaty i rdzen) oraz cztery chmury (kazda z 4 elips: ciemniejszy spod i jasniejszy wierzch) wygaszane dwoma pierscieniami, bo libGDX nie ma rozmycia Gaussa. Na wodzie leza fale, blask slonca i piana, losowane z ustalonego ziarna (`SEA_SEED`), wiec woda stoi w miejscu przez cala bitwe. Geometria sceny (`computeGeometry`) rezerwuje jedna komorke masztow nad plansza (`MAST_BAND`), `SEA_DEPTH = 1.30` komorki wody przed kadlubem i `SHIP_ROOM = 1.10` komorki na polowe kadluba z boku, a horyzont wypada `2.55` komorki nad dolna krawedzia planszy; scena jest centrowana w oknie i skaluje sie razem z plansza. Zweryfikowane sonda na makiecie PIL (19/19: brak prazkow, horyzont, mgielka, slonce, chmury, fale, pokrycie kadru) oraz numerycznym porownaniem portu z zaakceptowana makieta (srednia roznica kanalow 0.9 na niebie, 0.9 na mgielce i sloncu, 1.2 w rdzeniu slonca; rozjazd na wodzie 17.9 pochodzi z innego generatora liczb losowych - Python `random.Random` kontra `java.util.Random` - a profil i srednie kolorow wody zgadzaja sie co do 1).

### 5.3 Przygotowanie do Publikacji
* [ ] Wygenerowanie podpisanej paczki wydaniowej (Android App Bundle - `.aab`).
* [ ] Przygotowanie grafik sklepowych (zrzuty ekranu, ikona aplikacji, baner).
* [ ] Publikacja na Google Play Store.