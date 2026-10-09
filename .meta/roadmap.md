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

* [x] Siec miedzy statkami na ekranie bitwy. Plansza nie jest juz szachownica pol - jest nia lina rozciagnieta nad morzem i niebem, a przez oczka przeswieca woda, wiec maluja sie tylko kolumny kadluba z lukami (`HULL_COLUMN_COLOR`). `drawNet` kladzie pasmo liny na kazdej linii siatki (poziome na `bottom + line * cell` dla `line 0..HEIGHT`, pionowe na `cellLeft(line)` dla `line 1..WIDTH-1`), dzieki czemu oczko jest dokladnie jednym polem, a jednostka stoi w jego srodku, nigdy na skrzyzowaniu. Pasmo rysuje `strand` - ciemniejszy obrys `ROPE_SHADE` szerszy o `STRAND_EDGE = 0.012` komorki pod spodem i wlasciwa lina `ROPE_COLOR`, gruba `STRAND_THICKNESS = 0.04` komorki, wiec miedzy pasmami zostaje cienka linia oddzielajaca. Pasma wystaja `STRAND_REACH = 0.45` komorki poza skrajne linie siatki, a ich konce chowaja sie pod kadlubem (siec rysuje sie przed kolumnami kadluba), wiec lina jest cumowana do burty, a nie wisi w wodzie. Rozdarte oczko to `drawHole`: kolo `GAP_COLOR` o promieniu `HOLE_RADIUS = 0.38` komorki, w srodku przesiane tlem z `backdropAt` (morze ponizej horyzontu, niebo powyzej - te same gradienty co w tle, wiec dziura nie odcina sie plaska plama) i szesc luznych zeber liny co 60 stopni (`HOLE_SPOKES`). Znacznik wejscia na luke korzysta z tego samego `backdropAt` zamiast usunietego `setTerrainColor`. Zweryfikowane numerycznie i na urzadzeniu: maska liny na tle nieba w porcie Javy pokrywa 96.5% maski makiety, a na emulatorze (2992x1344, `cell = 121.2 px`) pasma wychodza co do piksela w odstepach jednej komorki - 7 pasm pionowych w odstepach 120.5-122 px i 6 poziomych w 121-122 px, grubosc pasma 5 px - a cztery wylosowane przez `BattleState.create()` rozdarcia trafily dokladnie w srodki oczek (1222, 634 wobec srodka siatki 1225, 631), nie na skrzyzowania.

* [x] Wieksza siatka miedzy statkami na ekranie bitwy: oczko sieci rysowane jest o 20% wieksze od zwyklego dopasowania (`NET_SCALE = 1.2f`). `computeGeometry` liczy najpierw jak dotad `fit` dla calej sceny (minimum z dopasowania do wysokosci `usableH / (HEIGHT + MAST_BAND + SEA_DEPTH)` i do szerokosci `usableW / (WIDTH + 2 * SHIP_ROOM)`), a dopiero potem skaluje samo oczko: `netScale = max(1, min(NET_SCALE, usableW / ((WIDTH + 2 * SHIP_ROOM) * fit)))`, wiec ekran zbyt waski na szersza siec zostaje przy starym dopasowaniu, a `cell = fit * netScale`. Miejsce na powiekszenie oddaja niebo nad masztami i woda przed kadlubami, a nie pasma tekstu: `bandCells = (HEIGHT + MAST_BAND + SEA_DEPTH) / netScale - HEIGHT` dzieli sie na `mastCells` i `seaCells` w proporcji `MAST_BAND : SEA_DEPTH`, dzieki czemu `sceneTop - sceneBottom` nadal wynosi dokladnie `usableH` i scena nie wchodzi pod panel statusu ani pod log. Horyzont trzyma sie tej samej wysokosci ekranu (`horizonY = sceneBottom + cell * (HORIZON_CELLS + SEA_DEPTH) / netScale`), wiec niebo zweza sie rownomiernie, a nie ucieka w gore; `MAST_BAND`, `SEA_DEPTH`, `SHIP_ROOM` i `HORIZON_CELLS` zostaja jako wagi i wartosci zrodlowe. Weryfikacja A/B na emulatorze (2992x1344): przy `NET_SCALE = 1.0` kolumny kadlubow zajmowaly 973 px (`cell = 121.625 px`, zgodne z 121.2 px zapisanym wyzej), a przy `NET_SCALE = 1.2` zajmuja 1168 px (`cell = 146.0 px`) - stosunek 1.2004, czyli dokladnie +20%; zmierzone `x = 703..1870` trafia w wyliczone `boardX = 703.23` i `boardX + 8 * cell = 1869.89` co do piksela. Niebo nad plansza zwezilo sie ze 170 px do 96 px, woda przed kadlubami ze 110 px do 62 px, gorna krawedz planszy poszla o 74 px w gore, a dolna o 48 px w dol; pasma tekstu zostaly na miejscu (gorna konczy sie na `y = 279`, dolna zaczyna na `y = 1166`), a plansza o wysokosci 730 px miesci sie miedzy nimi.

* [x] Statki na ekranie bitwy: kadlub, zagle i bandera. Dotad po bokach sieci byly tylko kolumny kadluba z lukami, teraz kazda z nich jest pelnym statkiem. `drawShips()` (wolane z `drawBoard()` tuz po `drawNet()`, a przed kolumnami kadluba) stawia dwa lustrzane statki; `drawShip(player)` liczy geometrie od krawedzi sieci na zewnatrz (`near = cellLeft(1)` / `cellLeft(WIDTH - 1)` cofniete o `0.03` komorki, `span = min(cell * HULL_TOP_CELLS, room - 0.05 * cell)`, wiec na waskim ekranie kadlub zweza sie zamiast wyjsc za margines). Kadlub to dziesiec pasow poszycia (`PLANK_BANDS`) malowanych przez `hullBand` jako trapezy, ktorych dalsza krawedz zsuwa sie ku stepce po `slope` wyliczonym z `HULL_KEEL_CELLS / HULL_TOP_CELLS`; wysokosci pasow wyznacza `PLANK_STOPS` od `DECK_LIFT_CELLS = 0.10` komorki nad plansza (poklad `PLANK_TAN`, burta i dolna listwa w `HULL_COLUMN_COLOR`, poszycie na przemian `PLANK_MID` / `PLANK_LIGHT` z pasem cienia `PLANK_SHADOW`) az do linii wody, a pod nia lezy granatowa stepka `KEEL_COLOR` (`KEEL_DROP_CELLS = 0.50` komorki ponizej planszy, z dolnym limitem `sceneBottom + 0.02` komorki, zeby na niskim ekranie nie weszla w pasmo logu). Olinowanie ma wysokosc `RIGGING_CELLS = 1.15` komorki zwezana do `sky / cell * 0.90`, gdy niebo nad plansza jest nizsze, wiec maszt nie wchodzi w pasmo tekstu: reja na `0.40`, maszt na `0.94`, dwa rejowe panele plotna (`drawSailPanel`, szesciokat ze szczytami w osi masztu) na `0.06..0.42` i `0.46..0.76` nad pokladem, a na szczycie bandera w `PLAYER_COLOR` / `ENEMY_COLOR`. Weryfikacja na emulatorze (2992x1344, `cell = 146 px`): kadluby zajmuja `x = 532..849` i `1725..2040`, szczyty od `y = 221` (bandera) i `229` (maszt) do `y = 1177` (stepka); pas pokladu wypada na `y = 360..397`, a granice kolejnych pasow poszycia trafily co do piksela w wyliczone `deckY + (deckY - boardY) * stop` (486, 575, 642, 680, 754, 829, 903, 970, 1104), stepka na `1105..1177`, a panele zagli na `254..296` i `301..351` (131 px szerokosci, czyli dokladnie `2 * 0.21 * span`). Poprawiony blad pierwszego podejscia: zagle wisialy pod pokladem, bo panele liczyly sie od `deckY - rig * 0.58`, a nie od pokladu w gore.

* [x] Statki odsuniete od sieci o pol oczka. Nowa stala `SHIP_GAP_CELLS = 0.50` komorki to odstep miedzy skrajna linia siatki a burta, a pomocnicze `shipShift(side)` (znak minus dla gracza, plus dla wroga) przelicza go na piksele, wiec razem z kadlubem przesuwa sie takze kolumna pokladu z lukami (`drawBoard` stawia ja teraz na `cellLeft(x) + shipShift(...)`, a nie na `cellLeft(x)`) oraz znaczniki oczekujacej zalogi (`drawHatch` liczy je od przekazanego `left`, a nie od `boardX`/`boardRight`). `drawShip` startuje od `near = edge + shipShift(...)` i od tej samej krawedzi mierzy `room` (kadlub zweza sie tylko na ekranie zbyt waskim, zeby zmiescic statek), a `STRAND_REACH = SHIP_GAP_CELLS + 0.06` komorki wydluza wylacznie poziome liny - `drawNet` liczy `reach` tylko dla pasm `line 0..HEIGHT`, wiec pionowe pasma i cale oczko zostaja bez zmian. Liny przechodza przez szczeline i wchodza 0.06 komorki pod burte, dzieki czemu koniec liny nie wisi w wodzie. Weryfikacja na emulatorze (2992x1344, `cell = 146 px`): kolumny pokladu przesunely sie z `x = 703..849` i `1725..1871` na `630..776` i `1798..1944`, czyli dokladnie o 73 px = 0.5 komorki, kadluby z `532..849` / `1725..2040` na `459..776` / `1798..2111`, a skrajne linie siatki zostaly na `849` i `1725`; szerokosc kadluba nie zmienila sie (`span = 312.4 px = 2.14` komorki, bo ograniczenie `room` dziala dopiero na wezszym ekranie). Sonda pikselowa w szczelinie `x = 776..845` pokazuje wode i niebo, a na kazdym z szesciu poziomow liny (`y = 372, 518, 664, 810, 956, 1102`) lina biegnie nieprzerwanie od burty do siatki (6 px `ROPE_COLOR` plus 2 px obwodki `ROPE_SHADE` z kazdej strony). Roznica klatek wobec poprzedniego zrzutu obejmuje wylacznie statki i konce lin (`x = 463..848` i `1725..2109`, `y = 221..1177`); pasma tekstu, kompas i przycisk ucieczki sa co do piksela identyczne. Efekt uboczny: znaczniki rezerwy wroga przy dwoch dolnych lukach wchodza pod nieprzezroczysty przycisk ucieczki (`x >= 1925`, `y = 934..1104`), ktory jest rysowany po planszy.

### 5.3 Przygotowanie do Publikacji
* [ ] Wygenerowanie podpisanej paczki wydaniowej (Android App Bundle - `.aab`).
* [ ] Przygotowanie grafik sklepowych (zrzuty ekranu, ikona aplikacji, baner).
* [ ] Publikacja na Google Play Store.