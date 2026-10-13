# Ekran bitwy - projekt wygladu i parametry

Dokument opisuje **caly** wyglad ekranu bitwy (desant na sieci) razem z nazwami parametrow, tak aby
kolejna zmiana nie wymagala odkrywania ukladu od nowa. Wszystkie nazwy ponizej istnieja w kodzie jako
pola `private static final` - zmiana wartosci w kodzie zmienia wyglad, bez szukania "magicznych liczb"
po metodach rysujacych.

Plik zrodlowy (jedyny): `core/src/main/java/com/corsair/deluxe/presentation/BattleScreen.java`
Blok parametrow: sekcje `--- 1.` ... `--- 10.`, konczy go znacznik `--- end of look parameters ---`.

Model planszy (osobny plik, bez wygladu): `core/src/main/java/com/corsair/deluxe/domain/battle/BattleState.java`

> Uwaga: dokument jest celowo bez polskich znakow diakrytycznych. Pliki `.meta` w tym repozytorium
> sa odczytywane jako CP1250, wiec UTF-8 z ogonkami wyswietla sie jako krzaki.

## 1. Czym jest ekran i jak sie go osiaga

Ekran jest ekranem libGDX (`ScreenAdapter`) i **nie ma zadnego pliku layoutu**: ani XML, ani JSON,
ani atlasu tekstur. Cala scena to kod rysujacy prymitywy (`ShapeRenderer`: prostokaty, trojkaty,
kola, elipsy) plus jeden `SpriteBatch` z domyslna czcionka `BitmapFont` do napisow. Z tego wynikaja
dwie rzeczy:

* kazdy element jest sterowany parametrem w kodzie, nie atrybutem w pliku danych;
* nie ma tu warstw Scene2D - napisy, kompas i luki sa obslugiwane wlasnym kodem i wlasnym
  testowaniem dotyku (`handleInput`), a nie `Actor`ami.

Do ekranu prowadzi walka: `CorsairGame` otwiera `BattleScreen` z `BattleState.create(...)` oraz
ekranem powrotu (`returnScreen`). Wyjscie z ekranu idzie przez `BattleResult` (patrz sekcja 7).

## 2. Uklad wspolrzednych

* Piksele, `(0, 0)` w **lewym dolnym** narozniku, os Y rosnie do gory (`camera.setToOrtho(false, w, h)`).
* `cell` - bok jednego oczka siatki (i bok jednego pola planszy) w pikselach. Wszystko, co ma
  zachowac proporcje przy zmianie rozmiaru ekranu, jest wyrazone w ulamkach `cell`.
* `boardX`, `boardY` - lewy dolny naroznik **pola (0,0)** planszy, czyli kolumny z lukami gracza.
* `cellLeft(x) = boardX + x * cell` - lewa krawedz kolumny `x`.
* `cellBottom(y) = boardY + (HEIGHT - 1 - y) * cell` - dolna krawedz wiersza `y`. Uwaga: **wiersz
  y = 0 jest najwyzej**, a y = HEIGHT - 1 (= 4) najnizej, czyli Y rosnie w gore, a indeks wiersza
  w dol.
* `outward(side) = PLAYER ? -1 : +1` - po ktorej stronie siatki stoi statek; `edge` to krawedz
  siatki od strony tego statku (`boardX` dla gracza, `cellLeft(WIDTH)` dla wroga).
* `hullNear(side) = edge + outward * cell * (SHIP_GAP_CELLS + HULL_BOW_CELLS)` - gdzie stoi
  **srodek** kadluba (nie jego krawedz!). Dziob jest punktem, ktory rzut obraca ku siatce, wiec
  srodek statku stoi o `HULL_BOW_CELLS` dalej od planszy; dzieki temu dziob wypada dokladnie
  `SHIP_GAP_CELLS` od siatki. `SHIP_GAP_CELLS` jest **ujemne** (`-2.60 cell`): statki stoja tak
  blisko, ze ich dzioby wchodza na skrajne kolumny planszy (te, na ktore otwieraja sie luki),
  a drewno lezy pod siatka. `SHIP_GAP_CELLS = -(HULL_BOW_CELLS + HATCH_REAR_CELLS +
  SHIP_STANDOFF_CELLS)`, gdzie `HATCH_REAR_CELLS = -hullX(HATCH_COLUMN_UNITS, HULL_BEAM_UNITS)`
  (~0.882) to odleglosc tylnej krawedzi luku od dziobu, a `SHIP_STANDOFF_CELLS = 0.5 cell` - o tyle
  tylna krawedz luku stoi za skrajna linia siatki. Suma
  `SHIP_GAP_CELLS + HULL_BOW_CELLS + HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS = 0`, czyli **tylna
  krawedz luku trafia w punkt pol oczka od skrajnej linii siatki niezaleznie od ksztaltu kadluba**.
  Koniec liny liczy sie z tej samej pary stalej i luku: `STRAND_LASH_CELLS = SHIP_STANDOFF_CELLS -
  HATCH_SPAN_CELLS` (~0.216 cell), gdzie `HATCH_SPAN_CELLS` to szerokosc luku na ekranie - dzieki
  temu **koniec liny trafia w dziobowa krawedz luku**: luk zostaje wolny w calosci, a jednostka
  wychodzi tuz obok konca liny.
* `hullScreenX/hullScreenY` - jedyne zrodlo pozycji czegokolwiek na statku: kadluba, lukow, masztu
  i obszarow dotyku. Statek jest rysowany w **rzucie perspektywicznym** pudelka (patrz punkt 7),
  a nie jako plaskie wielokaty, wiec jego pozycja i ksztalt zaleza od rozdzielczosci tylko przez
  `cell` (i `hullNear`, ktory z `cell` sie liczy).
* `sceneBottom`, `sceneTop` - dol i gora calej sceny (morze + plansza + niebo na maszty).
* `horizonY` - linia horyzontu; dzieli scene na morze (ponizej) i niebo (powyzej).
* `margin` - margines ramki ekranu.

## 3. Potok ukladu: `layout` -> `computeGeometry`

`layout(w, h)` jest wolane przy starcie i przy `resize`. Wybiera skale tekstu, a potem
`computeGeometry(skala)` zamienia parametry na piksele.

1. `margin = max(MARGIN_MIN, min(w, h) * MARGIN_FRACTION)`
2. Skala tekstu: `max(MIN_TEXT_SCALE, h / TEXT_SCALE_HEIGHT)` razy kolejne `TEXT_SCALE_STEPS`
   (`2, 1.6, 1.3, 1`); pierwszy krok, dla ktorego `cell >= MIN_CELL`, wygrywa. Ostatni krok zawsze
   przechodzi, wiec na bardzo malym ekranie tekst jest maly, ale plansza nadal istnieje.
3. `textWidth = max(40, width - 2 * margin)` - szerokosc zawijania napisow.
4. Pasy tekstu liczone sa **dla najdluzszego mozliwego napisu**, nigdy dla aktualnego, zeby uklad
   nie skakal w trakcie bitwy:
   * `topBand = playerLine + enemyLine + max(turnLine, WORST_TURN_LINE) + hintLine + 3 * TEXT_GAP * lineHeight`
   * `bottomBand = max(lineHeight * LOG_BAND_LINES, measure(LOG_RESERVE) + lineHeight * LOG_BAND_PAD)`
5. `arrowZone = max(width * ARROW_ZONE_WIDTH, lineHeight * ARROW_ZONE_LINES)` - kolumna sterowania po
   prawej stronie planszy.
6. `usableW = width - 2 * margin - arrowZone`, `usableH = height - 2 * margin - topBand - bottomBand`.
7. Skala sceny. Pudelko sceny w oczkach to `span = WIDTH + 2 * SHIP_ROOM` (8 + 10.99 = 18.99,
   czyli pol kadluba z kazdej strony planszy - patrz punkt 7 w sekcji 5),
   a `PANEL_RESERVE_CELLS` oczek z niego idzie do prawego panelu, wiec plansza liczy sie z wezszego
   pudelka `boxW`:
   * `sceneCells = HEIGHT + MAST_BAND + SEA_DEPTH` (5 + 2 + 1.3 = 8.3)
   * `boxW = usableW * span / (span + PANEL_RESERVE_CELLS)`
   * `fit = max(1, min(usableH / sceneCells, boxW / span))`
   * `netScale = max(1, min(NET_SCALE, boxW / (span * fit)))`
   * `cell = fit * netScale`
   * **wyjatek**: gdy `fit * netScale < MIN_CELL`, rezerwa jest oddawana (`boxW = usableW`, a `fit`
     i `netScale` liczone od nowa). Inaczej zaplate za panel zaplacilby krok skali tekstu, a na
     waskim ekranie czytelna plansza jest wazniejsza niz luzny panel.
8. `bandCells = sceneCells / netScale - HEIGHT` - nadwyzka sceny dzielona miedzy niebo (`MAST_BAND`)
   i morze (`SEA_DEPTH`) proporcjonalnie.
9. `sceneBottom = margin + bottomBand + (usableH - cell * (HEIGHT + bandCells)) / 2`
   `boardX = margin + (boxW - cell * span) / 2 + cell * SHIP_ROOM`
   `boardY = sceneBottom + cell * seaCells`
   `sceneTop = boardY + cell * (HEIGHT + mastCells)`
   `SHIP_ROOM` jest teraz **polowa kadluba**: `SHIP_GAP_CELLS + HULL_BOW_CELLS + HULL_STERN_CELLS +
   SHIP_AIR_CELLS`, wiec `boardX` odsuwa plansze od krawedzi sceny o caly statek - i o tyle samo
   rosnie `span` (punkt 7), zeby statek zmiescil sie w scenie bez zwezania planszy. Kontrola:
   daleka krawedz rufy gracza wypada `SHIP_ROOM - (SHIP_GAP_CELLS + HULL_BOW_CELLS) - HULL_STERN_CELLS
   = SHIP_AIR_CELLS = 3.45 cell` od lewej krawedzi sceny, a symetrycznie po prawej - zadna czesc
   statku nie wychodzi za scene. Plansza natomiast jest **pod statkami**: dziob gracza siega
   `SHIP_GAP_CELLS + HULL_BOW_CELLS + HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS = 0` od skrajnej linii
   siatki, wiec drewno przykrywa skrajna kolumne planszy (2.60 cell liczone od dziobu do rufy),
   a rysowana pozniej siatka idzie po drewnie (patrz sekcja 4). **`SHIP_ROOM` jest zapasem, ktory
   trzyma skale sceny**: `SHIP_GAP_CELLS` i `SHIP_AIR_CELLS` dziela go miedzy siebie (statki blizej
   siatki = wiecej powietrza z tylu), wiec zmiana jednego bez drugiego przelicza `cell`, plansze
   i caly uklad. `-2.60 + 3.4507` daje te same `5.495 cell`, co dawniej `0.25 + 0.60` - na 1920x1080
   `cell = 80.4 px` i `boardX = 463.6 px` zostaly bez zmian, przesunely sie tylko statki.
10. **Horyzont jest przypiety do stolu, nie do siatki**: `horizonY = sceneBottom + cell *
    (HORIZON_HEIGHT_CELLS + SEA_DEPTH) / netScale`. Dzieki temu powiekszenie siatki zabiera
    miejsce niebu i morzu, a nie przesuwa horyzontu.
11. Prawa kolumna. `rightWidth` mierzy sie **od prawej krawedzi planszy do `width - margin`**, wiec
    cala rezerwa z punktu 7 trafia wlasnie do panelu. Tarcza kompasu nie stoi jednak w srodku panelu,
    tylko w srodku **wolnej wody**: liczy sie od dalekiej krawedzi kadluba wroga do linii marginesu,
    bo tylko ten pas jest naprawde pusty:
    `enemyFar = enemyHullFar() = hullNear(false) + cell * HULL_STERN_CELLS`,
    `compassX = (enemyFar + width - margin) / 2`, `compassY = srodek planszy`.
    Promien tarczy to `compassRadius = min(cell * COMPASS_RADIUS_CELLS, min(wolna woda / 2,
    wolny pas nad, wolny pas pod) / COMPASS_ARROW_TIP)`, gdzie `wolna woda = width - margin - enemyFar`,
    `wolny pas nad = (fleeY - fleeHalfHeight) - compassY`, a `wolny pas pod = compassY - (margin + topBand)`.
    Dzielenie przez `COMPASS_ARROW_TIP` jest po to, ze strzalki kierunkow wystaja poza tarcze i siegaja
    `1.28` promienia: pas ma zostac wolny nie dla samej tarczy, a dla **calej rozy kompasu**. Na
    czterech z osmiu badanych rozdzielczosci ograniczeniem jest `cell * COMPASS_RADIUS_CELLS`, wiec
    tarcza ma srednice `3.2` oczka, a groty strzalek siegaja `4.1` oczka; na 400x800, 1080x1920
    i 1440x2960 zweza ja polowa wolnej wody (grot prawej strzalki trafia wowczas dokladnie na linie
    marginesu), a na 800x480 - wolny pas nad tarcza. Pas pod nigdy nie wiaze w praktyce (srodek
    planszy jest duzo wyzej niz plytka ucieczki), ale zostaje w `min`, zeby roza nie mogla wyjsc za
    ekran. Roza nigdy nie wchodzi na kadlub wroga ani na plytke ucieczki.
12. **Przycisk ucieczki nie nalezy do kolumny kompasu.** To mala plytka w prawym gornym rogu
    obszaru gry: `fleeHalfWidth = min(rightWidth / 2, lineHeight * FLEE_HALF_WIDTH_LINES)`,
    `fleeHalfHeight = lineHeight * FLEE_HALF_HEIGHT_LINES`,
    `fleeX = width - margin - fleeHalfWidth`,
    `fleeY = height - margin - topBand - lineHeight * FLEE_CORNER_GAP_LINES - fleeHalfHeight`.
    Dzieki `min` z polowa panelu plytka nigdy nie wchodzi na plansze, a dzieki `topBand` w wzorze
    nie zaslania napisow (patrz sekcja 5.10). Liczy sie **przed** kompasem, bo jego promien
    korzysta z `fleeY` i `fleeHalfHeight`.

## 4. Kolejnosc rysowania

```
ScreenUtils.clear(BACKDROP)
shapes.begin(Filled)
  drawSeaAndSky()   gradient nieba (1 px na wiersz) -> gradient morza -> drawHaze -> drawSun
                    -> drawClouds -> drawWaves -> drawSunGlitter -> drawFoam
  drawBoard()       drawShips -> drawHatchColumn (gracz, wrog) -> drawNet -> kola zalogi
  drawSceneMask()   maska sceny: tlo od sceneTop do gory ekranu
  drawControls()    kompas (tarcza, obwodka, 3 strzalki kierunkow poza obwodka, igla, plytka GO!)
                    -> przycisk ucieczki
shapes.end()
batch.begin()
  drawTexts()       linia gracza, linia wroga, linia tury, podpowiedz, log (dolem), litery N/E/S/W
                    i "GO!" na tarczy, "UCIECZKA"
batch.end()
shapes.begin(Filled)
  drawNeedle()      igla jeszcze raz, juz na literach N/E/S/W
shapes.end()
```

Kolejnosc w `drawBoard` nie jest przypadkowa:

* statki ida **pierwsze**, bo sa **polozone na planszy**: `SHIP_GAP_CELLS` jest ujemne, wiec dzioby
  wchodza `-(SHIP_GAP_CELLS) + HULL_BOW_CELLS = 1.38 cell` na plansze i drewno przykrywa skrajny
  pas kazdej strony. Siatka musi wiec pojsc **na drewno** - inaczej liny znikalyby pod kadlubem
  dokladnie tam, gdzie sa przywiazane;
* luki ida na kadluby (kazdy luk jest otworem w burcie, ktora statek pokazuje przeciwnikowi),
  a siatka dopiero **na wszystko**: konce lin leza na drewnie, a nie pod nim, wiec widac, ze lina
  jest zamocowana na burcie. Kazda sciana kadluba (i kazdy luk) jest wypelniana i **od razu**
  obwiedziona ciemnym obrysem (sekcja 7), wiec obrys nie jest osobnym przebiegiem i nic go nie
  zamalowuje; luk dostaje obrys po wszystkich czterech bokach, wiec jego skos po burcie widac nawet
  wtedy, gdy wypelnienie luku ma ten sam kolor co sciana;
* w samym `drawShip` **takielunek idzie pierwszy, a kadlub na niego** (`drawSailPanel` -> maszt ->
  burta -> rufa). Maszt stoi na srodku kadluba, czyli duzo dalej od oka niz rufa, wiec w rzucie
  z 15 stopni kadlub zaslania stope zagla - widoczna dolna krawedz zagla to **linia pokladu**,
  a nie wlasna stopa plachty. Odwrotna kolejnosc (kadlub, potem zagiel) kladla plachte **na**
  poklad i na naroznik planszy;
* zaloga idzie na koncu, po lukach.

`drawSceneMask()` idzie zaraz po `drawBoard()`, czyli **po** takielunku, a **przed** kompasem
i napisami. Zamalowuje tlem (`BACKDROP`) wszystko od `sceneTop` do gornej krawedzi ekranu, wiec
takielunek - rysowany wyzej, niz siega niebo - jest ucinany plasko na gornej krawedzi sceny.
Niebo konczy sie dokladnie na `sceneTop`, wiec maska nie przykrywa niczego poza czystym tlem;
kompas, przycisk ucieczki i napisy leza ponizej tej linii albo ida juz innym potokiem (`batch`).
Kompas idzie **po** statkach, wiec jego nieprzezroczysta tarcza przykrywalaby statek, ktory pod nia
wpada - dlatego tarcza jest wysrodkowana w wolnej wodzie i do kadluba wroga jej nie brakuje
(patrz punkt 11 w sekcji 3).

Igla idzie **trzecim** przebiegiem, po napisach: przecina litere `E` (i kazda inna, na ktora
wskazuje), wiec musi byc namalowana na nich. Ten przebieg rysuje tylko odcinek igly od krawedzi
plytki "GO!" (`COMPASS_GO_RADIUS`) do jej grota, wiec plytka i napis `GO!` zostaja nietkniete -
inaczej zamalowalby je kawalek igly. Kolor bierze z `controlShade()`, tak samo jak reszta
sterowania, zeby przygaszona igla nie rozjasniala sie w cudzej turze.

## 5. Katalog parametrow

Wartosci w nawiasach to stan obecny. "Jedn." mowi, jak czytac liczbe: `cell` = ulamek oczka,
`px` = piksele, `linie` = wielokrotnosc wysokosci wiersza tekstu, `0..1` = udzial, `RGB` = kolor.

### 1. Ramka ekranu i pasy tekstu

| Parametr | Jedn. | Wartosc | Co robi |
|---|---|---|---|
| `MIN_CELL` | px | 26 | mniejsze oczko = plansza nieczytelna, wiec zamiast tego zmniejsza sie skala tekstu |
| `TEXT_SCALE_HEIGHT` | px | 760 | skala tekstu = `h / to` |
| `MIN_TEXT_SCALE` | - | 0.8 | tekst nigdy mniejszy niz to |
| `TEXT_SCALE_STEPS` | - | 2, 1.6, 1.3, 1 | proby skal, pierwsza mieszczaca sie wygrywa |
| `TEXT_GAP` | linie | 0.12 | odstep miedzy blokami napisow |
| `MARGIN_MIN` / `MARGIN_FRACTION` | px / 0..1 | 12 / 0.02 | margines ramki ekranu |
| `PIXEL_CENTRE` | px | 0.5 | srodek jednopikselowego wiersza przy probkowaniu gradientow |
| `MIN_LINE_PX` | px | 1 | dolna granica grubosci **kazdej** cienkiej linii: fal, polysku, piany, szwu zagla, obrysu kadluba i lukow oraz lin siatki. `ShapeRenderer` chodzi bez antyaliasingu, wiec kreska wezsza niz piksel nie rozmywa sie, tylko wypada albo na jednym pikselu, albo na zadnym - zalezy, po ktorej stronie srodka piksela trafi jej srodek. Bez tej podlogi na 400x800 (`cell` 16.85 px) lina siatki ma `0.67 px`, a obrys kadluba `0.51 px` |
| `WORST_TURN_LINE` | tekst | - | najdluzszy wariant linii tury (paso gory liczy sie dla niego) |
| `LOG_RESERVE` | tekst | - | najdluzszy wariant logu (paso dolu liczy sie dla niego) |
| `LOG_BAND_LINES` / `LOG_BAND_PAD` | linie | 1.6 / 0.4 | minimalna wysokosc pasa logu i oddech nad logiem |
| `ARROW_ZONE_WIDTH` / `ARROW_ZONE_LINES` | 0..1 / linie | 0.14 / 2.4 | szerokosc kolumny sterowania |
| `PANEL_RESERVE_CELLS` | cell | 1 | ile oczek pudelka sceny oddac prawemu panelowi; plansza przesuwa sie w lewo zamiast sciskac sterowanie (oddawane, gdy `cell` spadlby pod `MIN_CELL`) |
| `COMPASS_RADIUS_CELLS` | cell | 1.6 | promien tarczy kompasu; `min` z polowa wolnej wody i z wolnym pasem nad i pod tarcza, podzielonym przez zasieg strzalek |
| `FLEE_HALF_WIDTH_LINES` | linie | 3 | polowa szerokosci przycisku ucieczki; nigdy wiecej niz polowa panelu (`rightWidth / 2`) |
| `FLEE_HALF_HEIGHT_LINES` | linie | 1.1 | polowa wysokosci przycisku ucieczki |
| `FLEE_CORNER_GAP_LINES` | linie | 0.5 | oddech miedzy pasem tekstu a przyciskiem ucieczki |
| `BACKDROP` | RGB | 20, 13, 18 | tlo calej sceny (nie widac go, ale nie ma szczelin) |
| `PLAYER_COLOR` | RGB | 0.25, 0.55, 1 | kolor gracza: napisy, luki, zaloga, znaczniki |
| `ENEMY_COLOR` | RGB | 0.9, 0.2, 0.2 | to samo dla wroga |

Poza sekcja 1, ale tez strojenie w czasie: `ENEMY_THINK_SECONDS` (0.8) - ile "mysli" wrog,
`INPUT_DELAY_SECONDS` (0.6) - blokada dotyku po koncu bitwy.

### 2. Kompozycja sceny (w oczkach)

| Parametr | Wartosc | Co robi |
|---|---|---|
| `MAST_BAND` | 2 | ile nieba nad plansza na maszty |
| `SEA_DEPTH` | 1.30 | ile morza przed kadlubami |
| `NET_SCALE` | 1.2 | o tyle wieksza jest siatka od czystego dopasowania |
| `SHIP_ROOM` | 5.495 | **pol kadluba** miejsca z boku planszy na statek: `SHIP_GAP_CELLS + HULL_BOW_CELLS + HULL_STERN_CELLS + SHIP_AIR_CELLS` = `-2.60 + 1.218 + 3.427 + 3.4507` |
| `HORIZON_HEIGHT_CELLS` | 2.50 | wysokosc horyzontu nad stolem, odporna na `NET_SCALE` |

### 3. Niebo

| Parametr | Wartosc | Co robi |
|---|---|---|
| `SKY_POS` | 0, 0.07, 0.20, 0.42, 0.70, 1 | pozycje stopni gradientu od horyzontu (0) do gory (1) |
| `SKY_RGB` | 6 kolorow | 206,227,245 -> 38,76,152 |
| `SKY_ROW_HEIGHT` | 1.02 | wiersz ma piksel i wlos nakladki, zeby nie bylo szwow |
| `HAZE_HEIGHT` | 0.70 cell | jak wysoko siega mgielka nad horyzontem |
| `HAZE_STRENGTH` | 0.30 | jak mocno mieszana jest z kolorem nieba |
| `HAZE_FALLOFF` | 1.5 | wykladnik wygaszania mgielki |
| `HAZE_COLOR` | 238, 245, 253 | kolor mgielki |
| `HORIZON_RIM_HEIGHT` | 0.028 cell | grubosc jasnej linii na horyzoncie |
| `HORIZON_RIM_COLOR` | 176, 205, 231 | jej kolor |

### 4. Slonce

| Parametr | Wartosc | Co robi |
|---|---|---|
| `SUN_X` | 0.30 | pozycja pozioma jako udzial szerokosci |
| `SUN_HEIGHT` | 0.55 cell | wysokosc nad horyzontem |
| `SUN_GLOW_RINGS` / `SUN_GLOW_RADIUS` | 26 / 0.14 cell | ile pierscieni poswiaty i jak daleko siegaja |
| `SUN_GLOW_ALPHA` / `SUN_GLOW_ALPHA_GROWTH` | 2.4 / 26 | alfa najbardziej zewnetrznego i najbardziej wewnetrznego pierscienia |
| `SUN_COLOR` | 255, 244, 205 | kolor poswiaty |
| `SUN_DISC_STEPS` / `SUN_DISC_RADIUS` / `SUN_DISC_ALPHA` | 5 / 0.40 cell / 62 | tarcza: ile pierscieni, promien, alfa |
| `SUN_DISC_COLOR` | 255, 252, 236 | kolor tarczy |
| `SUN_CORE_RADIUS` / `SUN_CORE_COLOR` | 0.16 cell / 255, 253, 240 | jedyna nieprzezroczysta czesc slonca |

### 5. Chmury

| Parametr | Wartosc | Co robi |
|---|---|---|
| `CLOUD_LAYOUT` | 5 wierszy | `{x, y, promien w cell, alfa 0..255}`; `x`, `y` to udzialy nieba |
| `CLOUD_PUFFS` | 4 wiersze | `{dx, dy, rx, ry}` klapy wzgledem promienia chmury |
| `PUFF_RINGS` / `PUFF_RING_BASE` / `PUFF_RING_STEP` / `PUFF_RING_ALPHA` | 2 / 0.78 / 0.11 / 0.34 | klapa to pierscienie wokol rdzenia (libGDX nie ma rozmycia) |
| `PUFF_SEGMENTS` | 28 | ile segmentow ma elipsa klapy |
| `PUFF_LIFT` | 0.14 | o tyle wyzej siedzi podswietlona czapa |
| `PUFF_LIT_WIDTH` / `PUFF_LIT_HEIGHT` | 0.84 / 0.78 | o tyle mniejsza jest czapa |
| `CLOUD_SHADE_ALPHA` | 0.70 | ile alfy dostaje zacieniony spod, a czapa dostaje cala |
| `CLOUD_LIT` / `CLOUD_BASE` | 252,253,255 / 178,196,224 | kolory czapy i spodu |

### 6. Morze

| Parametr | Wartosc | Co robi |
|---|---|---|
| `SEA_POS` | 7 stopni | pozycje gradientu od horyzontu (0) do dolu (1) |
| `SEA_RGB` | 7 kolorow | 138,173,206 -> 22,35,68 |
| `SEA_ROW_HEIGHT` | 1.02 | jak w niebie |
| `WAVE_ROWS` / `WAVE_FALLOFF` | 96 / 1.8 | ile wierszy fal i jak bardzo gna sie ku dolowi |
| `WAVE_THICKNESS` / `WAVE_THICKNESS_GROWTH` | 0.010 / 0.062 cell | grubosc kreski fali na horyzoncie i jej przyrost |
| `WAVE_SPAN` / `WAVE_SPAN_GROWTH` | 0.18 / 1.90 cell | dlugosc kreski i jej przyrost |
| `WAVE_GAP` / `WAVE_GAP_GROWTH` | 1.1 / 1.5 | odstep miedzy kreskami w wierszu (razy dlugosc) |
| `WAVE_CREST_ALPHA` / `WAVE_CREST_ALPHA_GROWTH` | 34 / 104 | alfa wierszy grzbietow |
| `WAVE_TROUGH_ALPHA` / `WAVE_TROUGH_ALPHA_GROWTH` | 58 / 126 | alfa wierszy dolkow |
| `CREST_COLOR` / `TROUGH_COLOR` | 152,190,224 / 14,24,52 | kolory grzbietu i dolka |
| `GLITTER_COUNT` / `GLITTER_FALLOFF` | 190 / 1.7 | ile blyskow slonca na wodzie i jak sie gna |
| `GLITTER_ALPHA` / `GLITTER_FADE` | 168 / 126 | alfa na horyzoncie i o ile maleje ku dolowi |
| `GLITTER_WIDTH` / `GLITTER_WIDTH_GROWTH` | 0.05 / 0.62 cell | dlugosc blysku i jej przyrost |
| `GLITTER_THICKNESS` / `GLITTER_THICKNESS_GROWTH` | 0.011 / 2.4 cell | grubosc blysku i jej przyrost |
| `GLITTER_SPREAD` / `GLITTER_SPREAD_GROWTH` | 0.08 / 1.35 cell | rozrzut na boki wokol osi slonca |
| `FOAM_COUNT` | 70 | ile piany w pierwszym planie |
| `FOAM_DEPTH` / `FOAM_DEPTH_SPREAD` | 0.72 / 0.28 | w jakiej glebi siedzi piana |
| `FOAM_WIDTH` / `FOAM_WIDTH_SPREAD` | 0.18 / 0.55 cell | dlugosc piany |
| `FOAM_ALPHA` / `FOAM_ALPHA_SPREAD` | 40 / 70 | alfa piany |
| `FOAM_THICKNESS` | 0.018 cell | grubosc piany |
| `FOAM_COLOR` | 214, 232, 245 | kolor piany |
| `SEA_SEED` | 20261007 | staly ziarno rozrzutu: morze i niebo wygladaja tak samo w kazdej klatce |

### 7. Statki (kadlub i takielunek)

| Parametr | Wartosc | Co robi |
|---|---|---|
| `HULL_COLOR` | 107, 66, 36 | kolor burty - jedyny kolor poszycia, ani pasow, ani linii wodnej |
| `STERN_SHADE` / `HULL_STERN_COLOR` | 0.86 / 92, 56, 30 | o tyle ciemniejsza jest sciana rufy, zeby rufa i burta czytaly sie jako dwie osobne sciany |
| `HULL_EDGE_CELLS` | 0.03 cell | grubosc ciemnego obrysu kadluba, lukow i zagla (`ship view 15deg.png`); na 1920x1080 to 2.41 px, na 400x800 tylko 0.51 px, wiec linia jest podnoszona do dolnej granicy `MIN_LINE_PX` (1 px) |
| `HULL_EDGE_SHADE` / `HULL_EDGE_COLOR` | 0.45 / 48, 29, 16 | obrys kadluba: ta sama barwa co burta, sciemniona o tyle |
| `VIEW_YAW_DEG` | 15 | obrot kadluba wzgledem osi statku - kat, pod ktorym patrzymy (skos ze szkicu) |
| `VIEW_DISTANCE_UNITS` | 19.5 | odleglosc oka od srodka kadluba; perspektywa skraca dlugosc i podnosi rufe |
| `HULL_LENGTH_UNITS` | 20 | dlugosc kadluba rufa-dziob; jedna jednostka = jedno oczko siatki (`ship dimensions.png`, rysunek B) |
| `HULL_BEAM_UNITS` | 3 | szerokosc kadluba (`ship dimensions.png`, rysunek C) |
| `HULL_HEIGHT_UNITS` | 7 | wysokosc kadluba od linii wodnej do pokladu (`ship dimensions.png`, rysunek B) |
| `FOCAL_CELLS` | 8.682 | ogniskowa rzutu w oczkach; wyliczona tak, zeby **jeden krok kolumny lukow byl dokladnie tak wysoki jak jeden wiersz planszy**, ktory ten luk otwiera |
| `HULL_BOW_CELLS` | 1.218 | o tyle dziob wystaje do przodu od srodka kadluba |
| `HULL_STERN_CELLS` | 3.427 | o tyle rufa wystaje do tylu od srodka kadluba |
| `HATCH_REAR_CELLS` | 0.882 | odleglosc tylnej krawedzi luku od dziobu, w oczkach; liczona z `hullX`, wiec **ujemna** (rosnie ku rufie) |
| `HATCH_SPAN_CELLS` | 0.284 cell | o tyle dziobowa krawedz luku stoi blizej siatki niz tylna, czyli ile szerokosci luku widac na ekranie |
| `SHIP_STANDOFF_CELLS` | 0.5 cell | o tyle tylna krawedz luku stoi za skrajna linia siatki - **z tego stoi statek** (`SHIP_GAP_CELLS`) |
| `STRAND_LASH_CELLS` | 0.216 cell | `SHIP_STANDOFF_CELLS - HATCH_SPAN_CELLS`: o tyle liny poziome wychodza poza skrajne linie siatki, czyli dokladnie do **dziobowej** krawedzi luku |
| `SHIP_AIR_CELLS` | 3.4507 cell | zapas powietrza miedzy rufa a krawedzia sceny; trzyma `SHIP_ROOM` na 5.495 cell, gdy statki stoja blizej siatki |
| `SAIL_COLOR` | 196, 186, 158 | kolor zagli |
| `SHIP_GAP_CELLS` | -2.60 cell | o tyle statek **wchodzi na plansze**: dzioby wchodza `-SHIP_GAP_CELLS + HULL_BOW_CELLS = 1.38 cell`, a tylne krawedzie lukow staja `SHIP_STANDOFF_CELLS = 0.5 cell` za skrajna linia siatki, wiec konce lin trafiaja na dziobowe krawedzie lukow. Ujemna wartosc jest suma trzech czlonow (patrz sekcja 3, punkt 9) |
| `RIGGING_MIN_SKY` | 0.4 cell | minimalna wysokosc takielunku nad stopa masztu (dolna granica) |
| `RIGGING_OVERFLOW` | 0.5 cell | o tyle takielunek wychodzi ponad gorna krawedz sceny (`sceneTop`) |
| `MAST_POSITION` | 0.42 | gdzie stoi maszt na kadlubie (udzial dlugosci od rufy, czyli prawie na srodku) |
| `MAST_BEAM_UNITS` | `HULL_BEAM_UNITS / 2` = 1.5 | gdzie stoi maszt w poprzek pokladu, w jednostkach od dalekiej burty (`0` = daleka burta, `HULL_BEAM_UNITS` = burta od strony planszy), czyli **srodek szerokosci pokladu** |
| `SAIL_HALF_WIDTH` | 0.4 | polowa szerokosci zagla jako udzial szerokosci planszy (pelna szerokosc = 0.8 planszy); to szerokosc wzdluz rei, na glowie |
| `SAIL_CLEW_INSET` | 0.06 | o tyle stopa jest wezsza od glowy - boki (liky) zbiegaja sie ku dolowi |
| `SAIL_CLEW_RISE` | 0.2 | o tyle oba narozniki stopy (rogi szotowe) leza wyzej od najglebszego punktu stopy |
| `SAIL_SEGMENTS` | 16 | na ile odcinkow dzielony jest sagi luk stopy |
| `SAIL_EDGE_CELLS` / `SAIL_EDGE_SHADE` | `HULL_EDGE_CELLS` / `HULL_EDGE_SHADE` | obrys zagla: ta sama grubosc i to samo sciemnienie co obrys kadluba, tylko od `SAIL_COLOR` (`SAIL_EDGE_COLOR` = 88, 84, 71) |
| `SAIL_BOTTOM` / `SAIL_TOP` | 0.25 / 0.95 | jedyny zagiel, udzialy wysokosci takielunku od stopy masztu: stopa wisi nad naroznikiem planszy, glowa poza scena |
| `MAST_THICKNESS` / `MAST_TOP` | 0.176 cell / 1 | maszt: grubosc (gruby slup) i jak wysoko siega |

Zagiel, reja i choragiewka zniknely: zostaje sam dol takielunku. Wysokosc takielunku to **cale niebo
nad stopa masztu plus `RIGGING_OVERFLOW`**, a `drawSceneMask()` ucina wszystko powyzej `sceneTop`,
wiec widac tylko podstawe masztu i dolna czesc zagla. `RIGGING_MIN_SKY` dziala juz tylko jako
podloga wysokosci (na bardzo ciasnym ekranie niebo jest nizsze niz ta wartosc, ale maska i tak utnie
gore).

Zagiel jest **0.8 szerokosci planszy** (`2 * SAIL_HALF_WIDTH`), wiec miesci sie w widoku: od zewnetrznej
krawedzi sceny dzieli go jeszcze 1.09 cell na 1920x1080 (rosnie na wezszych ekranach, bo skala
maleje szybciej niz margines). Oba zagla natomiast **nachodza na siebie o 0.76 cell** (0.10
szerokosci planszy) - oba maszty sa 5.64 cell od siebie, bo statki zostaly dociagniete
do siatki, a kazdy zagiel ma 6.40 cell szerokosci. To nachodzenie nie zalezy od rozdzielczosci, bo
oba maszty i obie polowy zagla licza sie z tego samego `cell`. Dopoki maszt stal na burcie od strony
planszy, nachodzenie wynosilo 2.19 cell - oba plotna zachodzily na siebie prawie na trzeciej czesci
szerokosci, bo oba maszty staly tuz przy burcie sasiada (patrz wyzej `MAST_BEAM_UNITS`).

Dwa plotna w jednym kolorze zlalyby sie w jeden szeroki zagiel, wiec kazdy zagiel ma wokol siebie
cienka ciemna linie (`SAIL_EDGE_*`, `drawSailEdge`) - tak samo, jak kadlub ma obrys. Linia idzie po
**wlasnym** plotnie (glowa, oba liki, stopa po tej samej krzywej co wypelnienie), wiec granica
widoczna na ekranie to **tylny lik zagla wroga**: zagiel wroga rysowany jest drugi i to on przykrywa
zagiel gracza.

Ksztalt zagla to **kwadratowy zagiel rejowy**: prosta glowa wzdluz rei, dwa liki zbiegajace sie ku
dolowi (`SAIL_CLEW_INSET`) i stopa, ktora **sagi** miedzy rogami szotowymi - jak plachta wypelniona
wiatrem, a nie plaski panel. Stopa to **szescienna krzywa Beziera** wychodzaca z kazdego rogu
w kierunku jego wlasnego liku, wiec stopa wchodzi w lik **bez zalamania**, a rogi sa zaokraglone
(zamiast ostrego "V" z dwoch prostych odcinkow, jak bylo wczesniej).

Geometria statku (`drawShip` + `hullX`/`hullY`/`hullScreenX`/`hullScreenY`):

Punkt kadluba opisuje sie w **jednostkach statku**: `s` od rufy (0) do dziobu (`HULL_LENGTH_UNITS`),
`x` w poprzek kadluba od dalekiej burty (0) do burty od strony planszy (`HULL_BEAM_UNITS`),
`y` w gore od linii wodnej (0) do pokladu (`HULL_HEIGHT_UNITS`). Jedna jednostka to jedno oczko
siatki - dokladnie tak, jak zwymiarowane sa `_graphics/ship dimensions.png`.

```
sn = sin(VIEW_YAW_DEG),  cs = cos(VIEW_YAW_DEG)

depth(s,x)  = VIEW_DISTANCE_UNITS + (s - L/2) * cs - (x - B/2) * sn   // odleglosc od oka
across(s,x) =                        (s - L/2) * sn + (x - B/2) * cs   // w prawo od srodka statku
hullX(s,x)   = FOCAL_CELLS * across / depth        // oczka w prawo od srodka komorki kadluba
hullY(s,x,y) = FOCAL_CELLS * (y - H/2) / depth     // oczka nad horyzontem

FOCAL_CELLS = depth(HATCH_COLUMN_S, B) / HATCH_STEP_UNITS = 8.682
```

Ostatnia linia jest kluczowa: ogniskowa jest **wyprowadzona z kroku kolumny lukow**, a nie wybrana -
dzieki temu `HATCH_STEP_UNITS = 6 / HEIGHT = 1.2` jednostki burty (jeden wiersz planszy, tyle samo
co odstep dwoch linii siatki) zajmuje na ekranie dokladnie **jedno oczko**, czyli jeden wiersz
planszy, ktory ten luk otwiera. Ogniskowa wypada dokladnie ta sama, co dawniej przy szesciu
jednostkach na piec oczek (`depth(1.0, B) / 1.2` = `5 * depth(1.0, B) / 6`), wiec skala calej sceny
sie nie zmienila. Rzut jest wiec
zadany z gory: kadlub 20 x 3 x 7 jednostek, yaw `VIEW_YAW_DEG` od osi statku, oko na wysokosci
srodka kadluba (`y = H/2`, dlatego **horyzont przechodzi przez srodek kadluba** i nie widac pokladu
z gory) i `VIEW_DISTANCE_UNITS` jednostek od srodka statku. Perspektywa skraca dlugosc - 20 jednostek
kadluba zajmuje na ekranie tylko **4.645 cell** szerokosci i **6.429 cell** wysokosci - i podnosi
rufe wzgledem dziobu, dokladnie jak na `_graphics/ship view 15deg.png`.

Pozycja na ekranie (jedyne miejsce, w ktorym rzut spotyka sie z ukladem ekranu):

```
edge  = cellLeft(0)                      // gracz;  cellLeft(WIDTH)  // wrog
out   = -1                               // gracz;  +1               // wrog
near  = edge + out * cell * (SHIP_GAP_CELLS + HULL_BOW_CELLS)   // srodek kadluba
hullScreenX(player, s, x) = near + cell * (player ? +hullX(s,x) : -hullX(s,x))
hullScreenY(s, x, y)      = boardY + cell * HEIGHT / 2 + cell * hullY(s,x,y)
```

`near` to **srodek** kadluba, nie jego krawedz: rzut obraca dziob w strone siatki, wiec to dziob,
a nie srodek, musi byc odsuniety o `SHIP_GAP_CELLS`. Dlatego srodek stoi o `HULL_BOW_CELLS` dalej,
a cala reszta (luki, maszt, obszary dotyku) liczy sie juz od tego samego `near`. Statek wroga to
odbicie gracza: `hullX` zmienia znak, wiec oba dzioby patrza na siatke, a obie rufy na zewnatrz.

Kadlub to **dwie sciany po dwa trojkaty** (`hullFace`), kazda rozpiety na czterech naroznikach
tego samego pudelka:

```
burta (HULL_COLOR), od strony planszy:
    (s=0,          x=B, y=H) - (s=L, x=B, y=H) - (s=L, x=B, y=0) - (s=0, x=B, y=0)
rufa (HULL_STERN_COLOR, rysowana druga, na wierzchu):
    (s=0, x=0, y=H) - (s=0, x=B, y=H) - (s=0, x=B, y=0) - (s=0, x=0, y=0)
```

Dzieki temu statek czyta sie lekko izometrycznie (3/4): widac **cala rufe** (waska sciana na
zewnetrznej krawedzi, ciemniejsza) i **burte** od strony sieci, a krawedz narozna jest pionowa.
Dwa fakty, ktore z tego wynikaja:

- obie sciany maja te sama wysokosc (`HULL_HEIGHT_UNITS`), bo zadna nie jest przechylona - rufa
  jest tylko wezsza na ekranie, bo stoi blizej oka niz dziob;
- **linia pokladu i linia wodnej opadaja od rufy do dziobu** (poklad 739.6 -> 566.0 px, woda
  222.4 -> 396.1 px na 1920x1080), choc obie sa w rzeczywistosci poziome - to wylacznie
  perspektywa, ten sam efekt, ktory rysuje skos na `ship view 15deg.png`.

Obrys kadluba (`hullFace` + `hullEdge`, takze ze szkicu): kazda sciana jest **najpierw wypelniona,
a potem obwiedziona** ciemna kreska `HULL_EDGE_COLOR` o grubosci `HULL_EDGE_CELLS`. Ramka idzie po
wszystkich czterech krawedziach, wiec ciemna linie ma tez poklad i linia wodnej. Obrys to
`rectLine` o **plaskich koncach**, wydluzony o polowe grubosci poza kazdy koniec, dzieki czemu na
naroznikach kreski sie przecinaja i zamykaja naroznik bez szczeliny. Luki dostaly to samo
(`hatchQuad`), tylko ciemniejszym kolorem i po czterech bokach **rzutu** luku, a nie pudelka -
patrz punkt 9. To samo robi `drawSailEdge` dla zagla (patrz nizej), a wszystkie trzy miejsca
korzystaja z jednego pomocnika `bar(x1, y1, x2, y2, thickness)` - obrys to po prostu odcinek
o grubosci `thickness`, wydluzony o `thickness / 2` poza kazdy koniec.

Takielunek - **tylko jego dol jest widoczny**:

```
sky    = max(cell * RIGGING_MIN_SKY, sceneTop - mastY)   // niebo nad stopa masztu
rig    = sky + cell * RIGGING_OVERFLOW                   // takielunek siega ponad scene
mastS  = MAST_POSITION * HULL_LENGTH_UNITS
mastX  = hullScreenX(player, mastS, MAST_BEAM_UNITS)     // w poprzek: srodek szerokosci pokladu
mastY  = hullScreenY(mastS, MAST_BEAM_UNITS, HULL_HEIGHT_UNITS)   // stopa na linii pokladu
half   = cell * WIDTH * SAIL_HALF_WIDTH                  // 0.4 szerokosci planszy

drawSailPanel(mastX, half, mastY + rig * SAIL_BOTTOM, mastY + rig * SAIL_TOP)
rect(mastX - cell * MAST_THICKNESS / 2, mastY, cell * MAST_THICKNESS, rig * MAST_TOP)
```

Maszt zostaje **pionowy**, tak jak na szkicu - przechyla sie tylko jego stopa, bo ta lezy na
opadajacej linii pokladu (`MAST_POSITION = 0.42` liczy sie od rufy, wiec maszt stoi na srodku
dlugosci kadluba). W poprzek maszt stoi na `MAST_BEAM_UNITS = HULL_BEAM_UNITS / 2`, czyli **na
srodku szerokosci pokladu**. Wczesniej stal na `HULL_BEAM_UNITS`, czyli na burcie od strony planszy -
a ze zagiel liczy sie od `mastX`, oba maszty i oba zagla siedzialy tuz przy burcie sasiada (maszty
dzielilo wtedy 4.21 cell, a os masztu byla 0.312 szerokosci kadluba od dziobu; teraz jest to `0.626`,
a maszty dzieli 5.64 cell). Zagiel bazuje na wysokosci stopy masztu.

Ksztalt zagla (`drawSailPanel`) - wszystko liczone od `mastX`, `half`, `bottom`, `top`:

```
clewHalf = half * (1 - SAIL_CLEW_INSET)          // polowa szerokosci stopy
clewY    = bottom + half * SAIL_CLEW_RISE        // wysokosc rogow szotowych
lean     = half - clewHalf                       // o tyle lik zbiera sie ku dolowi
drop     = top - clewY
spread   = (clewY - bottom) / (0.75 * drop)      // rozstaw punktow kontrolnych
c1       = (mastX + clewHalf - spread * lean, clewY - spread * drop)
c2       = (mastX - clewHalf + spread * lean, clewY - spread * drop)
```

Stopa to krzywa Beziera od `(mastX + clewHalf, clewY)` do `(mastX - clewHalf, clewY)` z punktami
kontrolnymi `c1`, `c2`. Dwa fakty, ktore z tego wynikaja i ktore trzeba zachowac przy zmianach:

- w `t = 0.5` wysokosc krzywej to `clewY - 0.75 * spread * drop`, czyli dokladnie `bottom` - dlatego
  `spread` ma wlasnie taki wzor: **najglebszy punkt stopy trafia idealnie w `SAIL_BOTTOM`**;
- styczne w rogach maja kierunek `3 * spread * (-lean, -drop)`, czyli **dokladnie wzdloz liku** -
  dlatego stopa wchodzi w lik bez zalamania, a rogi sa zaokraglone zamiast ostrych.

Zagiel jest wypukly, wiec jest rysowany jako **wachlarz trojkatow** (`SAIL_SEGMENTS + 2` trojkatow,
przy 16 odcinkach 18) ze srodka glowy - w tym punkcie pozniej siada maszt i zaslania szew.
Zerowe alokacje, brak `polygon()`, brak triangulacji z uszami.

`drawSailPanel` to dwa przebiegi: **`drawSail`** wypelnia plotno, a **`drawSailEdge`** kresli po nim
ciemna linie `SAIL_EDGE_COLOR` o grubosci `SAIL_EDGE_CELLS` (te same wartosci co obrys kadluba).
Obrys idzie po glowie, obu likach i stopie - stopa **ta sama krzywa Beziera** i tymi samymi
`SAIL_SEGMENTS` odcinkami, co wypelnienie, wiec linia lezy dokladnie na krawedzi plotna. Grubosc jest
podniesiona do **co najmniej `MIN_LINE_PX`** (`max(cell * SAIL_EDGE_CELLS, MIN_LINE_PX)`), bo
`ShapeRenderer` chodzi na OpenGL **bez antyaliasingu**: kreska wezsza niz piksel nie rozmywa sie,
tylko wypada albo na jednym
pikselu, albo na zadnym - zalezy, po ktorej stronie srodka piksela trafi jej srodek. Na 400x800
`0.03 cell` to 0.51 px, wiec bez tej podlogi lik przerywalby sie na niektorych wierszach i oba
plotna stykalyby sie na tych wierszach.

Kolejnosc w `drawShip`: **najpierw takielunek, potem kadlub** - `drawSailPanel`, maszt, burta, rufa.
Maszt stoi na srodku szerokosci pokladu (glebokosc 17.96 jednostki), a rufa jest najblizej oka
(9.45), wiec kadlub rysowany na koncu **zabiera zaglowi jego stope** i sam maszt do wysokosci linii
pokladu w swoim miejscu (`maszt stopa pod burta` = 0.716 cell): widoczna dolna krawedz zagla to linia
pokladu, a nie wlasna stopa plachty. Odwrotna kolejnosc kladla plachte **na** poklad i na naroznik
planszy (do 73 px na 1920x1080), co psulo i kadlub, i plansze. Zagiel jest rysowany od
`mastY + rig * SAIL_BOTTOM` do `mastY + rig * SAIL_TOP`, a ze `rig` jest wieksze od `sky`, glowa
zagla wypada **powyzej** `sceneTop` i `drawSceneMask()` ucina ja plasko razem z masztem. Efekt:
nad pokladem widac tylko niski pas dolnej czesci zagla i gruby maszt wchodzacy w gore poza ekran.

`SAIL_BOTTOM = 0.25` jest **dolna granica, nie ozdoba**: stopa zagla jest schowana za kadlubem tylko
tak dlugo, jak dlugo jest nad plansza, a zagiel jest 0.8 szerokosci planszy, wiec jego narozniki
wychodza poza kadlub i nad naroznik planszy. Stopa wisi `rig * SAIL_BOTTOM` **nad stopa masztu**
(na 1920x1080: `266.07 px * 0.25 = 66.5 px`), wiec **0.01 `SAIL_BOTTOM` podnosi ja o 2.66 px**.
Przy `0.24` wiersz `zagle nad plansza` dawal `+0.015 cell` (0.25-1.18 px) na szesciu
rozdzielczosciach - i to wystarczalo, dopoki maszt stal na burcie od strony planszy. Po przeniesieniu
masztu na srodek pokladu jego stopa spadla (maszt stoi glebiej w widoku: 1.485 -> 1.522 cell pod
pokladem w rogu, na 1920x1080 3.0 px), a razem z nia stopa zagla - przy `0.24` weszlaby na plansze na
szesciu rozdzielczosciach, dlatego `SAIL_BOTTOM` poszlo na `0.25` i wiersz daje teraz `+0.019 cell`
(0.33-1.56 px). Na dwoch bardzo szerokich ekranach (2400x1080, 2992x1344) stopa i tak wchodzi na
plansze o 2-3 px, ale wchodzila juz przy `0.24` (-2.53 / -3.42 px) - to proporcja planszy na
ultra-wide, a nie takielunek, wiec nie naprawia sie tego `SAIL_BOTTOM` (byloby to odebranie
widocznego zagla na wszystkich pozostalych ekranach). Kazde podniesienie `SAIL_BOTTOM` zabiera
widoczny zagiel, wiec trzyma sie go tak nisko, jak pozwala naroznik planszy.

W praktyce niebo nad stopa masztu ma 1.16-2.00 cell (`NET_SCALE` od 1.0 do 1.2), a widoczny pas
zagla to jego dolna czesc az do linii pokladu - na 1920x1080 okolo 155 px przy maszcie. Maszt ma
0.176 cell grubosci (na 1920x1080 to 14 px, na telefonie 1080x1920 podobnie). Rogi szotowe leza
0.30-0.64 cell nad najglebszym punktem stopy (39 px na
1080x1920, 65 px na 1920x1080), a promien zaokraglenia rogow wychodzi 4.5-21 px (ok. 11 px na
1080x1920). Na linii ciecia `sceneTop` zagiel ma jeszcze 6.24 cell szerokosci z 6.4 cell glowy
(502 px z 515 px), bo lik zbiera tylko `SAIL_CLEW_INSET` - **widoczna szerokosc zagla to naprawde
0.8 szerokosci planszy**. Najwieksze odchylenie 16-odcinkowej lamanej od prawdziwej krzywej to
0.9 px na 1080x1920 i 1.4 px na 1920x1080, czyli niewidoczne. Maska nie podcina dolnej krawedzi
zagla - ucina tylko jego gore i wierzch masztu, a stopa zagla jest schowana za kadlubem.

### 8. Siatka (lina i rozdarcia)

| Parametr | Wartosc | Co robi |
|---|---|---|
| `STRAND_THICKNESS` | 0.04 cell | grubosc liny (0.67 px na 400x800), podnoszona do `MIN_LINE_PX`, zeby lina nie rozsypala sie na kreski |
| `STRAND_EDGE` | 0.012 cell | ciemna obwodka wokol liny, zeby byla czytelna nad jasnym morzem; **bez** podlogi `MIN_LINE_PX`, bo to ramka wokol pelnego paska, a nie wlasna linia: z podloga lina mialaby `1 + 2*1 = 3 px` z 16.85 px oczka |
| `STRAND_LASH_CELLS` | 0.216 cell | o tyle pozioma lina wychodzi poza skrajna linie siatki - `SHIP_STANDOFF_CELLS` mniej `HATCH_SPAN_CELLS`, czyli dokladnie do **dziobowej** krawedzi luku; caly otwor luku zostaje wolny |
| `TORN_STUB_CELLS` | 0.12 cell | dlugosc kikuta, ktory zostaje przy wezle po przerwaniu liny |
| `TORN_FRAY_CELLS` | 0.16 cell | dlugosc postrzepionego konca zwisajacego z kikuta w strone przerwy |
| `TORN_FRAY_DROP` | 0.7 | o jaka czesc swojej dlugosci postrzepiony koniec opada w dol |
| `ROPE_COLOR` / `ROPE_SHADE` | 217,214,199 / 133,128,117 | lina i jej obwodka |

Geometria siatki (`drawNet`):

```
left   = cellLeft(1),  right = cellLeft(WIDTH - 1)      // linie 1..7
bottom = cellBottom(HEIGHT - 1),  top = cellBottom(0) + cell
reach  = cell * STRAND_LASH_CELLS
liny poziome:  HEIGHT + 1 = 6 linii, od left - reach, dlugosc right - left + 2 * reach
liny pionowe:  WIDTH - 1  = 7 linii, na cellLeft(1..7)
```

Czyli **siatka ma 6 oczek w poziomie i 5 w pionie**, a kolumny z lukami (0 i 7) leza poza nia,
na kadlubach. Jedno oczko siatki = jedno pole planszy, jednostka stoi zawsze w srodku oczka, nigdy
na wezle. `reach` to **koncowka liny za ostatnim wezlem**: linie siatki zaczynaja sie dopiero na
`cellLeft(1)`, wiec lina musi przejsc jeszcze to jedno oczko, zeby dojsc do statku - i wlasnie ta
koncowka siada na drewnie. Jest **wyprowadzona z luku**, a nie dobrana osobno:
`STRAND_LASH_CELLS = SHIP_STANDOFF_CELLS - HATCH_SPAN_CELLS` - lina idzie od skrajnej linii siatki
przez pol oczka, ktore dzieli ja od tylnej krawedzi luku, i jeszcze przez szerokosc luku, az do jego
**dziobowej krawedzi**. Statek stoi z osobnej stalej: `SHIP_GAP_CELLS = -(HULL_BOW_CELLS +
HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS)`, czyli `SHIP_GAP_CELLS + HULL_BOW_CELLS +
HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS = 0`. To daje dwie rzeczy naraz: lina jest **zamocowana przy
samym luku**, przez ktory wychodza jednostki, a luk **zostaje wolny w calosci** - koniec liny
zatrzymuje sie na jego dalszej krawedzi, wiec otwor nie jest niczym przykryty (jednostka wychodzi
tuz obok konca liny, nie przez nia). Kadlub rysowany jest **pod**
siatka (sekcja 4), wiec koncowki lin leza na drewnie i widac je az do burty. Luki sa na **burcie** -
rysowane rzutem z punktu 7 - wiec linki konczace sie na drewnie sa na tej samej wysokosci, co
otwory, ktore otwieraja. Narzedzie `_viz/` mierzy z obrazu, gdzie konczy sie kazda lina, i porownuje
to z **policzona** dziobowa krawedzia luku (sekcja `siec: konce lin`), a takze sprawdza, ze za koncem
liny jest drewno (60 px na 1920x1080).

Rozdarcie siatki to **przerwana gorna pozioma lina oczka**. Kazda pozioma lina idzie przez
`drawNetLine`, ktory tnie ja nad kazda komorka z `state.isHole(x, y)` danej linii (linia o indeksie
`line` jest gorna krawedzia wiersza `HEIGHT - line`): w miejscu ciecia zostaje **kikut**
(`TORN_STUB_CELLS` = 0.12 cell) przy kazdym z dwoch wezlow komorki, a z konca kazdego kikuta zwisa
**postrzepiony koniec** (`drawTornKnot` -> `strandBar`) dlugosci `TORN_FRAY_CELLS` = 0.16 cell,
opadajacy o `TORN_FRAY_DROP` = 0.7 swojej dlugosci. Sama komorka nie jest niczym zamalowana, wiec
rozdarcie **pokazuje to, co jest pod siatka** - drewno kadluba albo niebo za nim - zamiast wygryzac
w statku ciemna plame. Kikuty i strzepy rysuje ta sama funkcja co liny, wiec siatka z rozdraciami
jest nadal rysowana **nad** kadlubami (sekcja 4).

Rozdarcia sa czescia rozgrywki (`BattleState.moveCrewMember`: pirat, ktory wejdzie na rozdarte
oczko, wypada za burte), wiec ich polozenia sa nietkniete. `BattleState.create` losuje kolumne z
zakresu `1..WIDTH - 2`, odrzuca srodek i kolumny z wejsciem na luke, i odbija je
punktowo-symetrycznie, wiec rozdarcia staja tylko w kolumnach `2..5`. Dla `VizScene.SEED` to
`2,1 / 3,3 / 4,1 / 5,3`. Sprawdzone skanem pikseli (`_viz`, sekcja `siec: rozdarcia`): srodek gornej
liny kazdej takiej komorki jest **pusty**, oba kikuty **trzymaja sie wezlow** (8 z 8 koncow), a w
prostokatach obu kadlubow nie ma **ani jednego piksela** starej ciemnej plamy `10,13,23` (0 px na
wszystkich 8 rozdzielczosciach).

### 9. Luki, zaloga i znaczniki

| Parametr | Wartosc | Co robi |
|---|---|---|
| `CREW_SHADOW_RADIUS` / `CREW_SHADOW_COLOR` | 0.38 cell / czarny 0.6 | cien pod piratem na siatce |
| `CREW_RADIUS` | 0.32 cell | promien pirata na siatce |
| `CREW_SEGMENTS` | 24 | ile segmentow ma kolo pirata |
| `HATCH_COLUMN_UNITS` | 0.5 | odleglosc tylnej krawedzi kolumny lukow od rufy (`ship dimensions.png`, rysunek B) |
| `HATCH_WIDTH_UNITS` | 1 | dlugosc luku wzdluz burty - jednostka, czyli bok kwadratu ze szkicu |
| `HATCH_STEP_UNITS` | `6 / HEIGHT` = 1.2 | **wyliczana**: jeden wiersz planszy w jednostkach burty; z niej liczy sie `FOCAL_CELLS` (punkt 7), wiec krok kolumny to dokladnie jedno oczko |
| `HATCH_GAP_UNITS` | `HATCH_STEP_UNITS * STRAND_THICKNESS` = 0.048 | **wyliczana**: pusta przestrzen nad i pod lukiem - dokladnie grubosc poziomej liny |
| `HATCH_OPEN_UNITS` | `HATCH_STEP_UNITS - 2 * HATCH_GAP_UNITS` = 1.104 | **wyliczana**: wysokosc otworu luku na burcie |
| `HATCH_MARGIN_UNITS` | 0.548 | **wyliczana**: zapas od pokladu do najwyzszego luku i od najnizszego luku do stepki; kolumna jest wyosiowana na horyzoncie, wiec oba zapasy sa rowne |
| `HATCH_COUNT` | `BattleState.HATCH_ROWS.length` = 5 | ile lukow ma kolumna |
| `HATCH_EDGE_SHADE` / `HATCH_EDGE_COLOR` | 0.35 / 37, 23, 12 | obrys luku - ciemniejszy od obrysu kadluba, zeby luki odstawaly od burty; grubosc bierze z `HULL_EDGE_CELLS` |
| `HATCH_DISABLED_COLOR` | 0.35, 0.35, 0.35 | zniszczony luk |
| `HATCH_OPEN_COLOR` | 0.98, 0.79, 0.28 | zloty kolor otwartego luku |
| `HATCH_OPEN_BRIGHTNESS` / `HATCH_OPEN_PULSE` | 0.85 / 0.15 | otwarty luk pulsuje miedzy tymi jasnosciami |
| `HATCH_CLOSED_BRIGHTNESS` | 0.6 | zamkniety luk jest tak przygaszony |
| `HATCH_ENEMY_BRIGHTNESS` | 0.8 | luki wroga sa zawsze przygaszone |
| `HATCH_TAP_REACH` | 1 cell | o tyle poszerzony jest obszar dotyku luku z kazdej strony (luki na burcie sa wezsze od oczka, wiec bez tego bylyby trudne do trafienia) |
| `WAITING_MARKER_INSET` / `WAITING_MARKER_TOP` | 0.36 / 0.82 cell | gdzie zaczyna sie slupek oczekujacych |
| `WAITING_MARKER_STEP` / `WAITING_MARKER_RADIUS` | 0.22 / 0.08 cell | odstep i promien znacznikow |
| `WAITING_MARKER_SEGMENTS` | 12 | ile segmentow ma znacznik |
| `HATCH_PULSE_BASE` / `HATCH_PULSE_SWING` / `HATCH_PULSE_SPEED` | 0.65 / 0.35 / 5 | puls gotowego luku: `base + swing * sin(t * speed)` |
| `ENTRY_MARKER_RADIUS` / `ENTRY_MARKER_INNER` | 0.44 / 0.34 cell | pierscien pokazujacy pole wejscia |
| `ENTRY_MARKER_SEGMENTS` | 24 | ile segmentow ma pierscien |

Geometria lukow (`drawHatchColumn` / `hatchQuad`):

```
low    = HATCH_MARGIN_UNITS + (HATCH_COUNT - 1 - i) * HATCH_STEP_UNITS
high   = low + HATCH_OPEN_UNITS

rearX = hullScreenX(player, HATCH_COLUMN_UNITS, B)                    // krawedz rufowa luku
bowX  = hullScreenX(player, HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS, B) // krawedz dziobowa luku
rearBottom = hullScreenY(HATCH_COLUMN_UNITS, B, low)   rearTop = hullScreenY(HATCH_COLUMN_UNITS, B, high)
bowBottom  = hullScreenY(HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS, B, low)
bowTop     = hullScreenY(HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS, B, high)

left   = min(rearX, bowX)              right  = max(rearX, bowX)
bottom = min(rearBottom, bowBottom)    top    = max(rearTop, bowTop)
```

`B = HULL_BEAM_UNITS` to blizsza burta (ta od strony planszy), a `HATCH_COUNT` to liczba wierszy lukow
z `BattleState.HATCH_ROWS`. Luk jest **rzutem jednostkowego kwadratu burty**, czyli - poniewaz burta
ucieka od oka - **rownoleglobokiem**: `hatchQuad` liczy cztery narozniki `rearX`/`bowX` x
`low`/`high`, rozbija go na dwa trojkaty, a potem obwodzi wszystkie cztery boki ciemna kreska
(`HATCH_EDGE_COLOR`). Zadna z tych czterech krawedzi nie jest pozioma ani pionowa w oczkach planszy:
luk nie jest prostokatem w ukladzie ekranu, tylko kwadratem na burcie.

**Luki ida za rytmem lin.** Kolumna lukow nie jest juz rozstawiona wlasnym krokiem `1 + 0.25`
jednostki, tylko **linami siatki**: idac od dolu mamy lina, pusta przysczen o wysokosci liny, luk,
znowu taka przysczen, lina, i tak dalej, a nad najwyzszym lukiem znowu przysczen i ostatnia lina.
Dlatego krok kolumny to dokladnie **jeden wiersz planszy** (`HATCH_STEP_UNITS = 6 / HEIGHT`), a nie
szesc jednostek na piec wierszy, i dlatego `FOCAL_CELLS` liczy sie z tego kroku - jedyne miejsce,
w ktorym rytm lin spotyka sie z rytmem lukow, to ta jedna zaleznosc. Puste przysczenie nad i pod
lukiem to z tego samego powodu dokladnie `STRAND_THICKNESS`, czyli tyle, ile ma pozioma lina
(`HATCH_GAP_UNITS`), a otwor luku to reszta kroku (`HATCH_OPEN_UNITS`). Linie siatki **nie byly przy
tym zmieniane** - zmienily sie tylko rozmiar i polozenie lukow.

Perspektywa sprawia, ze ten rytm jest **dokladny w jednostkach burty**, a nie w pikselach: luk jest
prostokatem na burcie, a wiersz planszy pasem o stalej wysokosci na ekranie, wiec wysokosc luku
widziana na ekranie zalezy od tego, jak daleko od oka jest jego krawedz (na 1920x1080 otwor ma
`77.60 px = 0.965` oczka przy rufie i `70.73 px = 0.879` oczka przy dziobie). Kolumna jest wiec
wyosiowana **na horyzoncie** (`HATCH_MARGIN_UNITS`), czyli w polowie glebosci kolumny
(`HATCH_COLUMN_S`): srodkowy luk wypada dokladnie w srodku swojego wiersza przy obu krawedziach
(przysczenie `0.0175` oczka przy rufie i `0.06` przy dziobie), a blad rosnie liniowo z odlegloscia
luku od horyzontu - po `0.097` oczka na skrajnych lukach przy rufie i `0.089` przy dziobie, w obie
strony od srodka. To najlepsze mozliwe wyosiowanie, bo przy kazdym innym jeden koniec kolumny
zyskiwalby kosztem drugiego. Cena tego jest taka, ze na 1920x1080 dolna krawedz linii wchodzi
o `8 px` w gorny naroznik rufowy najwyzszego luku (i, symetrycznie, w dolny naroznik rufowy
najnizszego), a o `4 px` w dolny naroznik dziobowy najwyzszego (i w gorny naroznik dziobowy
najnizszego). Zeby te naciecia zniknely, otwor musialby byc o okolo `0.14` oczka nizszy, czyli pusta
przysczenie musialaby byc trzy i pol razy grubsza od liny - to sprzeczne z rytmem, ktory mial byc
uzyskany. Na 400x800 obie krawedzie sa podniesione do `MIN_LINE_PX`, wiec tam rytm jest tylko
z grubsza.

Trzy wlasnosci tego rzutu, ktore trzeba zachowac przy zmianach:

* **boki luku zostaja pionowe** - `hullX` nie zalezy od `y`, wiec rzut przesuwa punkt w bok tylko
  jego polozeniem wzdluz statku (`s`), nigdy wysokoscia; dlatego luk nie jest przekrzywiony;
* **gorna i dolna krawedz opadaja od rufy do dziobu** - dokladnie tak, jak linia pokladu nad nimi,
  bo wysokosc `y` dzieli sie przez `depth`, a `depth` rosnie z `s`. Na 1920x1080 gorna krawedz
  opada `18.69 px = 0.232 cell`, dolna `12.46 px = 0.155 cell`;
* **opad luku to `3.0 / 3.5` opadu pokladu** - stosunek `(H - low - 0.5) / H` dla srodka kolumny
  (`low = 5.5` dla pierwszego luku), bo skos jest liniowy w `y`. Narzedzie porownuje te dwie liczby
  w wierszu `luk skos / skos pokladu` (0.86 wobec oczekiwanego 0.857).

Wnetrze luku zostaje **plaskie**: zaglebienie ze szkicu nie jest rysowane, wiec luk czyta sie po
kolorze (stan luku) i po obrysie, a nie po cieniu. Luk stoi **na burcie przy rufie** (kolumna
startuje `0.5` jednostki od rufy, tak jak na `ship dimensions.png`, rysunek B) i kurczy sie razem
z perspektywa: na 1920x1080 ma `22.87 px = 0.284 cell` szerokosci i `70.29 px = 0.874 cell` wysokosci
(przy dziobie `64.07 px = 0.796 cell`, bo dziob jest dalej od oka), a na 400x800 ledwie kilka
pikseli. Slupek oczekujacych znacznikow stoi tuz obok, od strony rufy
(`WAITING_MARKER_INSET`), wiec na waskim ekranie ladnie przylega do ciemnej rufy.

Obszar dotyku lukow w `handleInput` jest budowany z tego samego rzutu, ale z **pudelka**
(`hatchLeft` / `hatchRight` x `hatchBottom` / `hatchTop`), bo przekrzywiony luk nie jest juz
prostokatem na ekranie, a palec nie trafia w skos. Pudelko jest o tyle wyzsze od luku, o ile
opadaja jego krawedzie: na 1920x1080 ma `82.75 px = 1.029 cell` wysokosci zamiast `0.874 cell`
luku (na 400x800 `17.33 px`), i jest poszerzone o `cell * HATCH_TAP_REACH` z kazdej strony - inaczej
waski luk na burcie bylby prawie nie do trafienia. Poniewaz zasieg jest wiekszy niz odstep miedzy
lukami, pudelka dwoch sasiednich lukow zachodza na siebie; zeby nie otwieralo to luku wyzej, wygrywa
luk **blizszy dotkniecia** (porownanie odleglosci `touchY` od srodka pudelka). Dotkniecie
w dowolnym miejscu luku zawsze otwiera ten luk.

### 10. Sterowanie

| Parametr | Wartosc | Co robi |
|---|---|---|
| `COMPASS_RADIUS_CELLS` | 1.6 cell | promien tarczy kompasu (srednica 3.2 oczka); `min` z polowa wolnej wody i z wolnym pasem nad i pod tarcza, podzielonym przez `COMPASS_ARROW_TIP` |
| `COMPASS_RIM_RING` | 0.055 | grubosc obwodki jako udzial promienia |
| `COMPASS_GO_RADIUS` | 0.34 | promien plytki "GO!" - dokladnie tyle wynosi tez zasieg dotkniecia wykonujacego ruch |
| `COMPASS_ARROW_INNER` / `COMPASS_ARROW_HEAD` / `COMPASS_ARROW_TIP` | 1 / 1.10 / 1.28 | gdzie zaczyna sie i konczy strzalka kierunku: trzonek idzie od samej obwodki do `COMPASS_ARROW_HEAD`, a grot do `COMPASS_ARROW_TIP` |
| `COMPASS_ARROW_STEM` / `COMPASS_ARROW_WING` | 0.16 / 0.21 | polowa szerokosci trzonka i polowa podstawy grota |
| `COMPASS_LETTER_RING` | 0.78 | okrag, po ktorym rozlozone sa litery N/E/S/W, jako udzial promienia |
| `COMPASS_LABEL_SIZE` | 0.28 | wysokosc liter i napisu "GO!" jako udzial promienia tarczy |
| `NEEDLE_LENGTH` / `NEEDLE_HALF_WIDTH` | 0.86 / 0.16 | igla wzgledem promienia; igla jest tak samo szeroka jak trzonek strzalki, a konczy sie plasko |
| `DIAL_SEGMENTS` / `MARK_SEGMENTS` | 48 / 12 | ile trojkatow maja kola tarczy i okragla plytka "GO!" |
| `BUTTON_FACE_COLOR` | 0.2, 0.2, 0.28 | kolor tarczy (i tla pod obwodka) |
| `ARROW_COLOR` | 0.95, 0.8, 0.35 | kolor obwodki, strzalek kierunkow, igly i plytki "GO!" |
| `CONTROL_INACTIVE` | 0.45 | o tyle przygaszone jest wszystko, co teraz nie dziala |
| `FLEE_LABEL_PAD` | 0.4 linie | oddech wokol napisu "UCIECZKA" |
| `FLEE_COLOR` | 0.45, 0.12, 0.12 | ciemna czerwien przycisku ucieczki |
| `FLEE_HALF_WIDTH_LINES` / `FLEE_HALF_HEIGHT_LINES` | 3 / 1.1 linie | rozmiar plytki ucieczki (cala plytka = 6 x 2.2 linii); szerokosc ogranicza polowa panelu, zeby plytka nie weszla na plansze |
| `FLEE_CORNER_GAP_LINES` | 0.5 linie | oddech miedzy pasem tekstu a plytka |

**Kompas** (`drawControls`) rysuje w kolejnosci: tarcza (`circle` w `BUTTON_FACE_COLOR`), obwodka
(`circle` w `ARROW_COLOR` i na nim mniejszy `circle` w `BUTTON_FACE_COLOR`), trzy strzalki kierunkow
**poza** obwodka, igla (prostokat od srodka do `NEEDLE_LENGTH`) i plytka "GO!" (kolo o promieniu
`compassRadius * COMPASS_GO_RADIUS` w `ARROW_COLOR`, ktore przykrywa wewnetrzny koniec igly).
Trzy strzalki to dokladnie trzy dozwolone kierunki igly: prawo, prawa gora, prawy dol (`FORWARD`,
`FORWARD_UP`, `FORWARD_DOWN`). Kazda z nich to `rectLine` (trzonek, `COMPASS_ARROW_STEM` na kazda
strone) plus `triangle` (grot, `COMPASS_ARROW_WING` na kazda strone); trzonek zaczyna sie na samej
obwodce (`COMPASS_ARROW_INNER`), a grot konczy na `COMPASS_ARROW_TIP`, wiec nie ma miedzy nimi ani
szczeliny, ani kolizji z literami. Strzalki sa **tylko ozdoba** - dotyk obsluguje tarcza (patrz
nizej), a one same nie reaguja na nic.

**Igla** (`drawNeedle`) to `rectLine`, czyli prostokat o stalej szerokosci `2 * NEEDLE_HALF_WIDTH`
(tyle samo, co trzonek strzalki) i **plaskich** koncach - dlatego konczy sie tepo, a nie w szpic.
Wolana jest dwa razy: raz z tarcza (od srodka), raz po literach (od `COMPASS_GO_RADIUS`), bo
przecina litere `E`.

**Litery N/E/S/W i "GO!"** (`drawCompassLabels`, wolane na koncu `drawTexts`, bo to potok `batch`)
sa czysto ozdobna roza kompasu: biale `N`, `E`, `S`, `W` leza na czterech osiach tarczy w odleglosci
`COMPASS_LETTER_RING`, a na srodku jest czarny napis `GO!`. Rozmiar obu bierze sie z promienia
tarczy (`compassRadius * COMPASS_LABEL_SIZE`), wiec skaluja sie razem z kompasem; metoda podmienia
skale fontu i **przywraca ja** po sobie (ten sam wzorzec co `drawFleeLabel`). Litery nic nie
rezerwuja w ukladzie - leza wewnatrz tarczy, ktora i tak jest wolna woda. Igla przecina litere,
na ktora wskazuje (przy `FORWARD` jest to `E`), dlatego igla wraca na wierzch trzecim przebiegiem
`draw()` - litera pod igla jest zaslaniana, a nie odwrotnie.

**Obsluga dotkniec** (`handleInput`), po tescie plytki ucieczki, a przed testem lukow:

* dotkniecie w odleglosci `<= compassRadius * COMPASS_GO_RADIUS` od srodka **wykonuje ruch** w kierunku,
  w ktory wskazuje igla (`aimDirection`) - `playerArrow(aimDirection)`. Plytka "GO!" jest wiec
  jednoczesnie rysowana i klikalna - nie ma osobnego, niewidocznego obszaru dotyku;
* **przeciagniecie palca** (`dragging`) tylko **ustawia igle**: dopoki palec jest na ekranie, kazdy
  ruch przelicza `nearestDirection(touchX - compassX, touchY - compassY)`, czyli kat `atan2`
  przyporzadkowany najblizszemu z trzech kierunkow (granice na `+-22.5` stopnia). Puszczenie palca
  konczy przeciaganie i **nie** wykonuje ruchu - ruch robi dopiero "GO!";
* dotkniecie (tap) gdziekolwiek innym w tarczy (`<= compassRadius`) tez tylko **ustawia igle**;
* dotkniecie poza tarcza idzie dalej, do lukow.

`aimDirection` startuje jako `FORWARD` i **przezywa miedzy turami** - igla zostaje tam, gdzie ja
ustawiono.

Plytka ucieczki siedzi w prawym gornym rogu **obszaru gry**, wyrownana do `width - margin` i
zaczepiona pod pasem tekstu (`topBand`), a nie pod kompasem. Na waskich ekranach panel boczny jest
wezszy niz napis "UCIECZKA", wiec `drawFleeLabel` zmniejsza napis - na 400x800 do ~0.7 skali, na
1080x1920 do ~0.92.

## 6. Inwentarz elementow

| Element | Metoda | Z czego zbudowany |
|---|---|---|
| Gradient nieba | `drawSeaAndSky` | prostokat na kazdy wiersz pikseli, kolor z `ramp(SKY_POS, SKY_RGB, ...)` |
| Mgielka i rym horyzontu | `drawHaze` | wiersze mieszane z `HAZE_COLOR` + jasna linia na horyzoncie |
| Slonce | `drawSun` | 26 pierscieni poswiaty + 5 pierscieni tarczy + rdzen |
| Chmury | `drawClouds` / `cloud` / `puff` | po 4 klapy na chmure, kazda jako 3 elipsy (rdzen + 2 pierscienie), spod + czapa |
| Gradient morza | `drawSeaAndSky` | jak niebo, `ramp(SEA_POS, SEA_RGB, ...)` |
| Fale | `drawWaves` | 96 wierszy kresek, wiersze parzyste grzbiety, nieparzyste dolki |
| Blyski slonca | `drawSunGlitter` | 190 kresek rozrzuconych wokol osi slonca |
| Piana | `drawFoam` | 70 kresek w pierwszym planie |
| Siatka | `drawNet` / `drawNetLine` / `strand` / `strandBar` | 12 lin poziomych + 10 pionowych, kazda jako prostokat w obwodce |
| Rozdarcia | `drawNetLine` / `drawTornKnot` | przerwana gorna lina oczka: kikut przy kazdym wezle + postrzepiony koniec z kazdego kikuta |
| Kadlub (dwie sciany) | `drawShip` / `hullFace` / `hullEdge` | burta (`HULL_COLOR`) i rufa (`HULL_STERN_COLOR`), kazda jako jeden czworokat na rzucie perspektywicznym (15 stopni od osi statku, patrz punkt 7), obwiedziony ciemnym obrysem; poklad i linia wody opadaja od rufy do dziobu, a wysokosc obu scian jest ta sama |
| Maszt i zagiel | `drawShip` / `drawSailPanel` / `drawSail` / `drawSailEdge` | jeden zagiel o szerokosci `0.8` planszy (wachlarz 18 trojkatow: prosta glowa, liki zbierajace sie ku dolowi, sagi luk stopy) + ciemny obrys po glowie, likach i stopie + gruby maszt stojacy na rzucie pokladu; **rysowane przed kadlubem**, wiec stopa zagla zupelnie znika za burta i widoczna dolna krawedz zagla to linia pokladu; glowa zagla ucinana od gory przez `drawSceneMask` |
| Luki | `drawHatchColumn` / `drawHatch` / `hatchQuad` | rzut **kwadratu na burcie** (rownoleglobok, boki pionowe, gorna i dolna krawedz po skosie burty) z ciemnym obrysem po czterech bokach, w kolumnie przy rufie, kolor zalezny od stanu (wnetrze plaskie, bez zaglebienia) |
| Slupek oczekujacych | `drawHatch` | jedno male kolo na pirata, od gory luku w dol, na zewnatrz luku od strony rufy |
| Znacznik wejscia | `drawEntryMarker` | zloty pierscien z tlem w srodku |
| Zaloga na siatce | `drawBoard` | cien + kolo w kolorze strony |
| Kompas | `drawControls` / `drawCompassLabels` | tarcza + obwodka + 3 strzalki kierunkow poza obwodka (trzy mozliwe kierunki igly) + igla + plytka "GO!" + litery N/E/S/W i napis "GO!" |
| Wolna woda (kompas) | `computeGeometry` / `drawControls` | pas od dalekiej krawedzi kadluba wroga do `width - margin`; tarcza wysrodkowana na `compassX`/`compassY`, promien `COMPASS_RADIUS_CELLS` oczek (mniejszy, gdy pas jest wezszy niz tarcza) |
| Przycisk ucieczki | `drawControls` / `drawFleeLabel` | plytka `FLEE_COLOR` w prawym gornym rogu obszaru gry, tuz pod pasem tekstu + napis "UCIECZKA" (zmniejszany, gdy nie miesci sie w plytce) |
| Napisy | `drawTexts` | 4 bloki tekstu + log + etykieta ucieczki + litery kompasu i napis "GO!" |

## 7. Mapowanie elementow na model (`BattleState`)

| Element ekranu | Zrodlo w modelu |
|---|---|
| Rozmiar planszy | `BattleState.WIDTH` = 8, `BattleState.HEIGHT` = 5 |
| Kolumna lukow gracza / wroga | `hatchColumn(PLAYER)` = 0, `hatchColumn(ENEMY)` = `WIDTH - 1` |
| Wiersze z lukami | `HATCH_ROWS` = `{0, 1, 2, 3, 4}`; indeks luku z wiersza: `hatchIndexForRow(y)` |
| Pole wejscia | `entryColumn(PLAYER)` - zaznaczone `drawEntryMarker` |
| Rozdarcia siatki | `state.isHole(x, y)` |
| Zaloga na siatce | `state.getCrewAt(x, y)` (kolor strony) |
| Stan luku | `state.isHatchDisabled`, `state.getHatchCrew`, `state.canRelease` |
| Liczniki w napisach | `getReserve`, `getCrewOnNet`, `getDisabledHatches` |
| Tura | `getTurnsPlayed`, `MAX_TURNS`, `getSideToMove` |
| Wynik | `getOutcome`, `BattleOutcome` (DEFEAT wraca od razu do menu glownego) |
| Wyjscie | `leave()` -> `BattleResult(outcome, lost)`, gdzie `lost = (initialPlayerUnits - totalCrew(PLAYER)) * CREW_PER_UNIT` |

## 8. Napisy (bez polskich znakow, tak jak w kodzie)

| Miejsce | Napis |
|---|---|
| Linia gracza | `TY (<statek>): rezerwa N, na sieci N, luki N/5` |
| Linia wroga | `WROG (<statek>): rezerwa N, na sieci N, luki N/5` |
| Linia tury | `Tura N/MAX - <status>` (zlota, gdy otwarte luki lub koniec) |
| Podpowiedz | `otwarte luki: N (tap = otworz/zamknij)` |
| Log | `Ty: ...` / `Wrog: ...` + `wyprowadzenie N pir.` / `skok <kierunek>` / `, pokonani: N` / `, wpadli: N` / `, zablokowane luki: N` / `, wycofani: N` |
| Komunikaty | `Luk N jest zniszczony.`, `Luk N jest pusty.`, `Luk N zamkniety (czeka N pir.).`, `Luk N otwarty - kompas wyprowadzi zaloge.`, `Luk N otwarty, ale pole wejsciowe zajete - odsun pirata.` |
| Statusy | `Twoja tura: wyprowadzenie`, `Twoja tura: skok`, `Tura wroga...`, `ZWYCIESTWO!`, `PORAZKA`, `REMIS`, `UCIECZKA` |
| Kierunki | `naprzod`, `ukos w gore`, `ukos w dol` |
| Kompas | `N`, `E`, `S`, `W` (roza kompasu) |
| Przycisk | `UCIECZKA`, `GO!` |

## 9. Przepisy na typowe zmiany

| Chce zmienic | Co ruszyc |
|---|---|
| Wieksza / mniejsza siatka miedzy statkami | `NET_SCALE` (horyzont zostaje na miejscu) |
| Odsunac statki od siatki | `SHIP_STANDOFF_CELLS` **razem z `SHIP_AIR_CELLS`** - dopoki ich suma zostaje ta sama, skala sceny sie nie zmienia. Uwaga: `SHIP_GAP_CELLS` jest **wyprowadzone** z `SHIP_STANDOFF_CELLS` (`-(HULL_BOW_CELLS + HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS)`), wiec reczna zmiana rozjedzie tylne krawedzie lukow ze skrajna linia siatki - to wlasnie ta tozsamosc sprawdza `_viz/` |
| Inna dlugosc koncowki liny za skrajna linia siatki | `STRAND_LASH_CELLS` - konce lin przesuna sie wzgledem lukow (sekcja 8); zmiana `SHIP_STANDOFF_CELLS` przesuwa je razem z lukami |
| Wyrazniejszy / slabszy szew miedzy zaglami | `SAIL_EDGE_CELLS`, `SAIL_EDGE_SHADE` (albo `SAIL_EDGE_COLOR` na sztywno) |
| Ciensze / grubsze **wszystkie** cienkie linie naraz (fale, polysk, piana, szew, obrys kadluba i lukow, liny siatki) | `MIN_LINE_PX` - jedna wspolna dolna granica grubosci w pikselach |
| Grubsza lina / wyrazniejsza siatka | `STRAND_THICKNESS`, `STRAND_EDGE` (ponizej `MIN_LINE_PX` grubosc i tak zostanie podniesiona do 1 px) |
| Wieksze / mniejsze rozdarcia (dluzsza przerwa, dluzsze strzepy) | `TORN_STUB_CELLS`, `TORN_FRAY_CELLS`, `TORN_FRAY_DROP` |
| Inny kolor kadluba / zagli | `HULL_COLOR`, `SAIL_COLOR` |
| Szerszy lub wezszy / dluzszy lub krotszy / wyzszy lub nizszy kadlub | `HULL_BEAM_UNITS`, `HULL_LENGTH_UNITS`, `HULL_HEIGHT_UNITS` - reszta (`FOCAL_CELLS`, `HULL_BOW_CELLS`, `HULL_STERN_CELLS`, `SHIP_ROOM`) **przelicza sie sama** |
| Inny kat widoku (mniej / bardziej z boku) | `VIEW_YAW_DEG` (0 = patrzymy wzdluz statku, 90 = z burty) |
| Inne skrocenie perspektywy (kadlub dluzszy / krotszy na ekranie) | `VIEW_DISTANCE_UNITS` (mniej = blizej, wieksze skrocenie) |
| Wiekszy / mniejszy statek na ekranie | `HATCH_STEP_UNITS` (przez `FOCAL_CELLS`; uwaga: rozjedzie wtedy krok lukow z liniami siatki) albo `SHIP_GAP_CELLS` + `SHIP_AIR_CELLS` (przez `SHIP_ROOM`) - przeliczy wtedy `cell` i caly uklad |
| Wyrazniejsza / slabsza krawedz narozna | `STERN_SHADE` |
| Inne polozenie kolumny lukow (blizej / dalej rufy) | `HATCH_COLUMN_UNITS` |
| Wieksze / mniejsze luki | `HATCH_WIDTH_UNITS` wzdluz burty; w poprzek (w gore) luk jest tak wysoki, jak zostawia to rytm lin - `HATCH_STEP_UNITS` minus dwa `HATCH_GAP_UNITS` - wiec zmiana wysokosci luku bylaby zmiana przysczenia, czyli grubosci lin |
| Wysokosc widocznego dolnego pasa zagla | `SAIL_BOTTOM` (dolna granica: stopa musi zostac nad plansza), `SAIL_TOP`, `RIGGING_OVERFLOW` |
| Szeroki / waski zagiel | `SAIL_HALF_WIDTH` (0.4 = 0.8 szerokosci planszy) |
| Bardziej / mniej wybrzuszona stopa zagla | `SAIL_CLEW_RISE` (0 = stopa prosta, rogi na jej poziomie), `SAIL_CLEW_INSET` (0 = liki pionowe) |
| Gladszy / bardziej kanciasty luk stopy | `SAIL_SEGMENTS` |
| Grubszy / cienszy maszt | `MAST_THICKNESS` |
| Inna wysokosc masztu nad pokladem | `MAST_TOP` |
| Gdzie konczy sie widok (i takielunek) | `MAST_BAND` (niebo) - ciecie idzie po `sceneTop` |
| Inne polozenie masztu (wzdluz kadluba) | `MAST_POSITION` |
| Inne polozenie masztu (w poprzek pokladu: blizej burty od strony planszy lub dalej) | `MAST_BEAM_UNITS` (`HULL_BEAM_UNITS / 2` = srodek szerokosci pokladu, `HULL_BEAM_UNITS` = burta od strony planszy). Uwaga: maszt niesie zagiel, wiec przesuwa tez stope zagla i wiersz `zagle nad plansza` - patrz `SAIL_BOTTOM` |
| Wiecej / mniej nieba i morza | `MAST_BAND`, `SEA_DEPTH` (proporcja miedzy nimi dzieli nadwyzke sceny) |
| Wyszej / nizej horyzont | `HORIZON_HEIGHT_CELLS` |
| Inne slonce | `SUN_*` |
| Inne chmury | `CLOUD_LAYOUT` (dodaj / usun wiersz) |
| Spokojniejsze morze | `WAVE_ROWS`, `WAVE_SPAN`, `WAVE_SPAN_GROWTH`, `WAVE_GAP` |
| Mniej lub wiecej blyskow slonca | `GLITTER_COUNT`, `GLITTER_ALPHA` |
| Wiecej miejsca na tekst | `LOG_BAND_LINES`, `TEXT_GAP`, `MARGIN_*` |
| Wiecej miejsca na sterowanie po prawej | `PANEL_RESERVE_CELLS` (1 = jedno oczko; plansza idzie w lewo, a panel rosnie o ta sama szerokosc) |
| Wiekszy kompas | `COMPASS_RADIUS_CELLS`, `ARROW_ZONE_*` |
| Gdzie stoi kompas (srodek wolnej wody) | nic nie trzeba ruszac - liczy sie sam z kadluba wroga i `width - margin`; przy zbyt waskim pasie zweza sie |
| Wieksza / mniejsza plytka "GO!" | `COMPASS_GO_RADIUS` (to samo ustawienie zmienia obszar dotkniecia wykonujacy ruch) |
| Blizej / dalej od srodka litery | `COMPASS_LETTER_RING` |
| Dluzsze / krotsze strzalki kierunkow | `COMPASS_ARROW_TIP` (grot), `COMPASS_ARROW_HEAD` (podstawa grota) |
| Grubsze / ciensze strzalki i igla | `COMPASS_ARROW_STEM` (trzonek i igla razem), `COMPASS_ARROW_WING` (grot) |
| Wieksze litery N/E/S/W i "GO!" | `COMPASS_LABEL_SIZE` |
| Wlaczanie / wylaczanie przeciagania igly | galaz `dragging` w `handleInput` |
| Inny przycisk ucieczki | `FLEE_*` |
| Inne kolory stron | `PLAYER_COLOR`, `ENEMY_COLOR` |

## 10. Liczby, ktore celowo zostaly inline

Sa to wylacznie mnozniki rozrzutu (losowosc) w generatorach morza - "jak bardzo kreska moze sie
rozjechac". Nie sa parametrami wygladu, bo nie da sie ich sensownie opisac poza miejscem uzycia:

* `drawWaves`: `0.55f + 0.80f * rand` (dlugosc kreski), `0.35f + rand` (odstep w wierszu)
* `drawSunGlitter`: `0.35f + 0.65f * rand` (alfa), `0.45f + rand` (dlugosc)
* `puff`: `ring == 0 ? alpha : alpha * PUFF_RING_ALPHA` - rdzen jest jedyny nieprzezroczysty

Reszta liczb w pliku to indeksy, `2f`, `1f` i dzielenia - nie wartosci projektowe.

## 11. Jak sprawdzic zmiane wygladu

```
.\gradlew.bat :core:compileJava --console=plain --quiet     # EXIT=0 wystarcza
```

Projekt nie ma testow wizualnych ani modulu desktop, ale ma **narzedzie do wizualizacji** (folder
`_viz/`, patrz sekcja 12). Ono uruchamia prawdziwy `BattleScreen` na kilku rozdzielczosciach,
zapisuje zrzuty i zestawienie liczb, wiec zamiast powtarzac matematyke z sekcji 3 i 5 w skrypcie
mozna po prostu spojrzec na obrazek. Przy zmianach parametrow nadal warto najpierw policzyc `cell`,
`boardX`, `boardY` i `horizonY` dla najwezszego i najszerszego wspieranego ekranu: pasy tekstu i
`arrowZone` zjadaja szerokosc, wiec `netScale` moze spasc do 1 i wowczas siatka jest mniejsza, niz
wynika z `NET_SCALE`. Narzedzie wypisuje te liczby samo (sekcja 12).

Przy zmianach w statkach warto sprawdzic cztery rzeczy, ktore latwo zepsuc nie zmieniajac przy tym
zadnej stalej wprost:

* **kadlub wchodzi na siatke i jest pod nia** - `SHIP_ROOM` liczy sie z `SHIP_GAP_CELLS`,
  `HULL_BOW_CELLS` i `HULL_STERN_CELLS`, wiec kazda zmiana `VIEW_YAW_DEG`, `VIEW_DISTANCE_UNITS`
  albo wymiarow kadluba przesuwa rowniez polozenie statku i szerokosc sceny; dzioby maja wchodzic
  `-SHIP_GAP_CELLS + HULL_BOW_CELLS = 1.38 cell` na plansze, bo tylko wtedy drewno widac pod linami;
* **koniec liny trafia na dziobowa krawedz luku** - blok `siec: konce lin` w `_viz/out/measurements.txt`
  podaje dla kazdego z 6 rzedow polozenie konca liny, policzona dziobowa krawedz luku i ich roznice;
  roznica musi zmiescic sie w tolerancji (`0.07 cell` = obrys luku + 1 px rasteru), a kolumna
  `drewno za koncem` musi pokazac `DREWNO`; ta sama sekcja sprawdza, ze luk **zostaje wolny**.
  Pilnuje tego para tozsamosci `SHIP_GAP_CELLS + HULL_BOW_CELLS + HATCH_REAR_CELLS +
  SHIP_STANDOFF_CELLS = 0` oraz `STRAND_LASH_CELLS = SHIP_STANDOFF_CELLS - HATCH_SPAN_CELLS`, czyli
  `SHIP_GAP_CELLS` **wyprowadzone** z `HULL_BOW_CELLS`, `HATCH_REAR_CELLS` i `SHIP_STANDOFF_CELLS`;
* **stopa zagla zostaje nad plansza** - wiersz `zagle nad plansza` musi byc dodatni w kazdej
  rozdzielczosci (na 1920x1080 jest to `+1.56 px = 0.019 cell`; na 2400x1080 i 2992x1344 wychodzi
  `-2.23` / `-3.05 px`, tak jak wychodzilo przed przeniesieniem masztu na srodek pokladu, wiec to
  proporcja planszy na ultra-wide, a nie takielunek); zagiel rysowany jest przed kadlubem, wiec jego
  dol jest ukryty i widoczna krawedz zagla to linia pokladu, ale sam zagiel nie moze wejsc na plansze.
  Uwaga na sprzezenie: stopa zagla wisi `rig * SAIL_BOTTOM` nad stopa **masztu**, wiec kazde
  przesuniecie masztu - takze w poprzek pokladu, przez `MAST_BEAM_UNITS` - przesuwa tez stopa zagla
  (`0.01 SAIL_BOTTOM` to `rig * 0.01` px, na 1920x1080 2.66 px);
* **maszt stoi na srodku szerokosci pokladu, a nie przy burcie sasiada** - wiersz `maszt na belce`
  ma wyjsc `1.50 z 3.00 jednostki = 0.500 szerokosci pokladu (OK)`, a `maszt od cudzej burty` ma byc
  **dodatni** (odleglosc osi masztu od blizszej burty *sasiedniego* statku; teraz `4.217 cell` na
  kazdej rozdzielczosci) - maszt ma stac za wlasnym kadlubem, a nie nad cudzym drewnem. `maszt
  stopa pod burta` (`0.716 cell`) mowi, ile stopy masztu zaslania blizsza burta: wartosc dodatnia
  jest potrzebna, zeby maszt wyrastal z pokladu, a nie z powietrza (przy `0` albo na minusie widac
  szczeline miedzy stopa masztu a linia pokladu);
* **luki siedza na burcie** - kontrola `luk w burcie` i zakres `x` luku musza sie zgadzac, bo obszar
  dotyku lukow jest budowany z tego samego rzutu (sekcja 9);
* **luk jest kwadratem na burcie, nie prostokatem na ekranie** - wiersz `luk skos / skos pokladu`
  musi dawac 0.84 (tyle, ile `2.95 / 3.5` opadu pokladu), a blok `gora luku z pikseli` musi czytac
  **oba** narozniki gornej krawedzi: naroznik rufowy wyzej od dziobowego o policzona wartosc
  (`opad`). Ten odczyt bierze kolumne pionowej krawedzi luku (najwiecej trafien w polowie okna),
  bo skrajne kolumny lapia tylko zaokraglone konce poziomych kresek sasiednich lukow i podaja liczbe
  z sufitu; gdy luk jest wezszy niz `MIN_HATCH_READ_PX = 10 px` (400x800, 800x480), narzedzie
  porownuje tylko najwyzszy piksel obrysu z policzonym naroznikiem rufowym;
* **krok lukow to jeden wiersz planszy** - wiersz `luk y rufa wiersz 0 / 4` mierzy krok przy
  **krawedzi rufowej**, czyli blizej oka, wiec jest on powiekszony o
  `depth(HATCH_COLUMN_S, B) / depth(HATCH_COLUMN_UNITS, B) = 1.0486` i ma wyjsc `4.194 cell`
  (`337.41 px` na 1920x1080), a nie `4.000` - to nie blad. Sam krok w polowie glebosci kolumny, tam
  gdzie stoi `FOCAL_CELLS`, to dokladnie jedno oczko, i to jest wlasciwa kontrola rytmu lukow
  z liniami (sekcja 9);
* **obrys nie zjada kadluba** - `HULL_EDGE_CELLS` jest w `cell`, wiec na najwezszym ekranie
  (400x800) wychodzi `0.51 px`, czyli mniej niz piksel. Dlatego obrys jest podnoszony do
  `MIN_LINE_PX` (1 px) i na zadnym cieciu nie moze zniknac: blok `obrys kadluba` wypisuje grubosc po
  podlodze i linijke `obrys kompletny na cieciu: 4 z 4 miejsc OK` (rufa, naroze, dziob, oba obrysy
  luku). `brak px` na ktoryms z tych czterech miejsc znaczy, ze podloga przestala dzialac.
  Narzedzie mierzy obrys z pikseli, wiec zamiast zgadywac, warto spojrzec na te liczby: szerokosci
  maja byc rowne policzonej grubosci (z dokladnoscia do 1 px rasteryzacji), a nie wieksze.

## 12. Narzedzie do wizualizacji (`_viz/`)

`_viz/` to osobny projekt Gradle, ktory **nie jest** czescia glownego builda (nie ma go w
`settings.gradle`) i **nie jest wersjonowany** - jest wpisany do `.gitignore`, tak samo jak jego
wyniki. Kompiluje `core/src/main/java` bezposrednio (`sourceSets` wskazuje na `../core`), wiec
zadnego pliku z `core` nie trzeba zmieniac ani kopiowac.

Uruchomienie z katalogu glownego repo (jedno polecenie generuje wszystko):

```
.\gradlew.bat -p _viz run --console=plain
```

Wynik trafia do `_viz/out/`:

* `out/resolutions/*.png` - osiem rozdzielczosci (telefon pion, tablet, desktop, bardzo szeroki,
  bardzo wysoki), sam ekran bitwy bez zadnych dodatkow;
* `out/measurements/*.png` - te same rozdzielczosci z **nakladka pomiarowa**: ramka marginesu,
  obrys sceny i planszy, siatka oczek, pasy tekstu, rezerwa na panel po prawej, strefa strzalek,
  linia horyzontu, obrysy obu scian kadluba (burta i rufa), czworokaty wszystkich pieciu lukow
  obu statkow (kazdy z wlasnym obrysem), obrys zagla i masztu, obrys kompasu z jego plytka "GO!",
  igla, strzalki kierunkow, krzyzem liter i etykieta z liczbami w pikselach;
* `out/states/*.png` - cztery stany bitwy (`start`, `mid`, `victory`, `defeat`) w 1920x1080;
* `out/gallery.png` - zbiorcza plansza kontaktowa ze wszystkich zrzutow;
* `out/probe/` - zrzuty z trybu diagnostycznego `-PvizMain=viz.Probe`;
* `out/measurements.txt` - zestawienie liczbowe (patrz nizej).

Inne tryby (pojedynczo) wybiera sie wlasciwoscia `-PvizMain=`, np. `-PvizMain=viz.Probe`
(diagnostyka GL: sprawdza, czy bufor ramki dziala i czy obraz nie jest odwrocony - zapisuje
`out/probe/probe-orientation.png` i `out/probe/probe-battle.png`). Domyslnie uruchamia sie
`viz.VizMain`.

`out/measurements.txt` dla kazdej rozdzielczosci podaje:

* **kontrole modelu** - narzedzie liczy geometrie druga implementacja tych samych wzorow i
  porownuje ja pole po polu z polami prawdziwego ekranu (23 wartosci, tolerancja 0.01 px). Kazda
  niezgodnosc pojawia sie jako `ROZJAZD`; brak rozjazdow to `OK wszystkie 23 wartosci`. To zabezpiecza
  przed sytuacja, w ktorej narzedzie mierzy cos innego, niz widac na ekranie;
* **wejscie** - `margin`, `textScale`, `lineHeight`, `textWidth`, `topBand`, `bottomBand`,
  `arrowZone`, `usableW`, `usableH`;
* **plansza i scena** - `sceneCells`, `fit`, `netScale`, `cell`, `cell / lineHeight`;
* **rezerwa na panel** - o ile pikseli plansza idzie w lewo dzieki `PANEL_RESERVE_CELLS` oraz
  porownanie `cell` z rezerwa i bez niej (uwaga: przy waskich ekranach dziala warunek zwrotny
  `MIN_CELL`, wiec rezerwa kosztuje 0 px - zestawienie to pokazuje);
* **oba statki** - `near`/`far`/`room`, szerokosc i wysokosc kadluba w px i w `cell`, szerokosc
  burty i rufy, `x` krawedzi naroznej i dalszego rogu rufy, `x` dziobu, `poklad` i `linia wody`
  w trzech punktach (rog burty, rufa, dziob), `wysokosc sciany` w tych samych punktach (kontrola, ze
  obie sciany sa tej samej wysokosci), szerokosc luku w px i w `cell`, zakres `x` luku, jego wiersze
  (`luk y rufa wiersz 0/4` plus krok), wysokosc luku przy rufie i przy dziobie (`luk wysokosc rufa` /
  `luk wysokosc dziob`), opad gornej i dolnej krawedzi luku (`luk skos gora` / `luk skos dol`),
  iloraz tego opadu do opadu pokladu (`luk skos / skos pokladu`, ma wyjsc `0.84`), wysokosc pudelka
  luku (`luk otoczka wysokosc`, czyli obszar dotkniecia), kontrola `luk w burcie`, szerokosc zagla,
  wysokosc zawieszenia
  zagla nad stopa masztu, wysokosc obciecia maska, **`zagle nad plansza`** (znakowana odleglosc
  stopy zagla od gornej krawedzi planszy; wartosc ujemna to blad), szerokosc masztu, jego os `x`
  (takze jako udzial szerokosci kadluba od dziobu), `maszt na belce` (gdzie maszt stoi w poprzek
  pokladu, w jednostkach burty i jako udzial jej szerokosci - ma wyjsc `0.500`),
  `maszt od cudzej burty` (o ile pikseli i oczek os masztu mija blizsza burte **sasiedniego**
  statku; dodatnia, gdy maszt stoi za wlasnym kadlubem), `maszt stopa pod burta` (ile stopy masztu
  zaslania blizsza burta, czyli na ile maszt wyrasta z pokladu), wysokosc stopy i wysokosc;
* **pomiar z pikseli** - niezalezny od modelu skan koloru na gotowym obrazie, ktory mierzy kadlub
  (`HULL_COLOR`), obie sciany (`HULL_COLOR` i `HULL_STERN_COLOR`), gorna krawedz burty przy rufie
  i przy dziobie (z tego wychodzi zmierzony opad pokladu) oraz maszt - szerokosc, **os `x` z
  porownaniem do modelu** (`OK` przy roznicy do 1 px; 0.52 px na 400x800) i gorna krawedz masztu
  wzgledem ciecia sceny. Zgodnosc z liczbami
  wyliczonymi oznacza, ze i wzory, i rysowanie sa w porzadku. Roznica kilku pikseli na krawedziach
  (antyaliasing i zaokraglenie do piksela) jest normalna, ale pomiar przyciety krawedzia ekranu
  jest oznaczany jako `UWAGA`;
* **obrys z pikseli** - drugi skan, tym razem na obrazie **bez nakladki** (linie nakladki leza
  dokladnie na krawedziach kadluba i lukow, wiec zaslaniaja obrys, ktory ma byc zmierzony). Blok
  `obrys kadluba` podaje ciecie `y` i policzona grubosc po podlodze `MIN_LINE_PX`, dlugosci ciemnych
  odcinkow na tym cieciu (`rufa` / `naroze` / `dziob`, osobno obie pionowe krawedzie obrysu luku;
  `brak px` znaczy, ze obrys jest cienszy niz piksel - z podloga 1 px nie zdarza sie to na zadnej
  rozdzielczosci, tak jak `brak px` nie pojawia sie na 400x800, gdzie sam `HULL_EDGE_CELLS` ma
  `0.51 px`), linijke `obrys kompletny na cieciu: 4 z 4 miejsc (podloga 1 px)  OK` oraz obrys calej
  sylwetki (`obrys statku x [...]`) z odchyleniem od pudelka kadluba powiekszonego o polowe
  grubosci. Odchylenie do ~1.5 px jest
  normalne, wieksze znaczy, ze obrys wyszedl poza kadlub. Ten sam skan czyta **narozniki gornej
  krawedzi luku** (`gora luku z pikseli`): osobno naroznik rufowy i dziobowy z ich policzonymi
  wartosciami oraz `opad` w pikselach - to jedyna kontrola, ktora widzi skos luku na ekranie. Dla
  kazdej polowy okna wybierana jest kolumna z najwieksza liczba pikseli obrysu (prawdziwa pionowa
  krawedz luku), bo skrajne kolumny okna lapia tylko zaokraglone konce poziomych kresek sasiednich
  lukow; gdy luk jest wezszy niz `MIN_HATCH_READ_PX = 10 px`, blok podaje sam najwyzszy piksel
  obrysu zamiast dwoch naroznikow;
* **konce lin** - trzeci skan, takze na obrazie bez nakladki: dla kazdego z 6 rzedow siatki blok
  `siec: konce lin` podaje, o ile oczek koniec poziomej liny wychodzi za skrajna linie siatki,
  **policzona** odleglosc dziobowej krawedzi luku od tej samej linii i ich roznice, a takze ile
  pikseli drewna lezy za koncem liny (`DREWNO` / `WODA`). Ostatnie dwie linie bloku pilnuja reguly:
  koniec liny ma trafic na dziobowa krawedz luku (tolerancja to obrys luku `HATCH_EDGE` plus 1 px
  rasteru, na 1920x1080 `0.07 cell`), a luk ma **zostac wolny** - otwor luku konczy sie dopiero
  za koncem liny. Pomiar obejmuje obwodke liny, bo na waskich ekranach (400x800) samo
  `STRAND_THICKNESS` ma `0.67 px` i dopiero podloga `MIN_LINE_PX` podnosi lina do pelnego piksela;
* **szew miedzy zaglami** - czwarty skan, na obrazie bez nakladki: blok `zagle: obrys, ktory
  rozdziela oba plotna` podaje, o ile pikseli i oczek oba zagla **na siebie nachodza** (zagiel
  wroga rysowany jest drugi, wiec jego tylny lik lezy na zaglu gracza), a potem idzie wiersz po
  wierszu od rogu szotowego zagla wroga do maski i sprawdza, czy w oknie 5 px wokol tego liku jest
  **choc jeden piksel obrysu** - inaczej dwa plotna w jednym kolorze zlalyby sie w jeden zagiel.
  Wynik `N z N wierszy ... OK` znaczy, ze szew jest ciagly w calej widocznej wysokosci zagla
  (22-153 wierszy, zaleznie od rozdzielczosci); blok podaje tez oba kolory (`rgb`) i grubosc obrysu
  w pikselach. To jedyna kontrola, ktora widzi podloge `max(cell * SAIL_EDGE_CELLS, MIN_LINE_PX)` -
  bez niej na 400x800 (`0.51 px`) obrys przerywalby sie na czesci wierszy;
* **grubosc liny** - piaty skan, na obrazie bez nakladki: blok `siec: grubosc liny` mierzy pionowy
  przekroj poziomej liny w srodku wolnego oczka srodkowego rzedu (z dala od wezlow i postrzepionych
  koncow) i porownuje go z policzona gruboscia liny razem z obwodka po obu stronach
  (`strandPx + 2 * cell * STRAND_EDGE`). Roznica do 1 px rasteryzacji jest normalna; bez podlogi
  `MIN_LINE_PX` na 400x800 lina mialaby `0.67 px` i pomiar pokazalby 0 albo 1 px zamiast ciaglej
  kreski;
* **sterowanie** - `compassX`, `compassY`, `compassRadius`, promien tarczy "GO!" (`COMPASS_GO_RADIUS`),
  promien okregu liter (`COMPASS_LETTER_RING`), rozmiar napisu (`COMPASS_LABEL_SIZE`), dlugosc i
  szerokosc igly (`igla dl./szer.`), zasieg grota strzalki (`grot strzalki R`), polowa szerokosci
  wolnej wody, w ktorej stoi kompas, oraz polowa szerokosci i polowa wysokosci plytki ucieczki
  (`fleeX`, `fleeY`, `fleeHalfWidth`, `fleeHalfHeight`).

Zasady, ktorych trzyma sie narzedzie:

* `BattleScreen.java` jest **tylko czytany** - narzedzie siega po jego pola i stale przez refleksje
  (`viz/Layout.java`), a nie przez kopiowanie wartosci;
* scena rysowana jest poza ekranem (ukryte okno GL + `FrameBuffer`), wiec nic nie wyskakuje na
  pulpicie i nie trzeba emulatora ani telefonu;
* losowosc jest zaszyta (`SEED`), a fale i slonce dostaja wlasne ziarno, zeby ten sam zrzut byl
  powtarzalny;
* stany bitwy powstaja przez wykonanie legalnych ruchow na `BattleState`, a nie przez podmiane
  pol - wiec to, co widac na `out/states/*.png`, to naprawde mozliwe stany gry.

