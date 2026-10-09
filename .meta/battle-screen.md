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
* nie ma tu warstw Scene2D - napisy, strzalki i luki sa obslugiwane wlasnym kodem i wlasnym
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
  y = 0 jest najwyzej**, a y = 10 (HEIGHT-1) najnizej, czyli Y rosnie w gore, a indeks wiersza w dol.
* `shipShift(side) = (PLAYER ? -1 : +1) * cell * SHIP_GAP_CELLS` - o tyle statek i jego poklad sa
  odsuniete od siatki. Ten sam shift obowiazuje kadlub, kwadraty pokladu i liny, dzieki czemu
  szczelina zawsze czyta sie jako czesc statku.
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
5. `arrowZone = max(width * ARROW_ZONE_WIDTH, lineHeight * ARROW_ZONE_LINES)` - kolumna strzalek po
   prawej stronie planszy.
6. `usableW = width - 2 * margin - arrowZone`, `usableH = height - 2 * margin - topBand - bottomBand`.
7. Skala sceny. Pudelko sceny w oczkach to `span = WIDTH + 2 * SHIP_ROOM` (8 + 2.2 = 10.2),
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
10. **Horyzont jest przypiety do stolu, nie do siatki**: `horizonY = sceneBottom + cell *
    (HORIZON_HEIGHT_CELLS + SEA_DEPTH) / netScale`. Dzieki temu powiekszenie siatki zabiera
    miejsce niebu i morzu, a nie przesuwa horyzontu.
11. Prawa kolumna. `rightWidth` mierzy sie **od prawej krawedzi planszy do `width - margin`**, wiec
    cala rezerwa z punktu 7 trafia wlasnie do panelu: `compassX = prawa krawedz planszy + rightWidth / 2`,
    `compassY = srodek planszy`,
    `buttonSize = min(rightWidth / COMPASS_BUTTON_WIDTH, usableH * COMPASS_BUTTON_HEIGHT)`,
    a przycisk ucieczki z `FLEE_*` (patrz sekcja 10).

## 4. Kolejnosc rysowania

```
ScreenUtils.clear(BACKDROP)
shapes.begin(Filled)
  drawSeaAndSky()   gradient nieba (1 px na wiersz) -> gradient morza -> drawHaze -> drawSun
                    -> drawClouds -> drawWaves -> drawSunGlitter -> drawFoam
  drawBoard()       drawNet -> drawShips -> kwadraty pokladu + drawHatch -> kola zalogi
  drawSceneMask()   maska sceny: tlo od sceneTop do gory ekranu
  drawControls()    strzalki (kompas) -> przycisk ucieczki
shapes.end()
batch.begin()
  drawTexts()       linia gracza, linia wroga, linia tury, podpowiedz, log (dolem), "UCIECZKA"
batch.end()
```

Kolejnosc w `drawBoard` nie jest przypadkowa:

* siatka idzie pierwsza, bo jest **plansza** - statki rysowane na niej przykrywaja konce lin
  dokladnie tam, gdzie lina jest przywiazana do kadluba;
* kwadraty pokladu ida **na** kadlub (kolumny lukow 0 i 10), wiec kadlub i takielunek zostaja
  widoczne poza obrysem planszy;
* zaloga idzie na koncu, po lukach.

`drawSceneMask()` idzie zaraz po `drawBoard()`, czyli **po** takielunku, a **przed** strzalkami
i napisami. Zamalowuje tlem (`BACKDROP`) wszystko od `sceneTop` do gornej krawedzi ekranu, wiec
takielunek - rysowany wyzej, niz siega niebo - jest ucinany plasko na gornej krawedzi sceny.
Niebo konczy sie dokladnie na `sceneTop`, wiec maska nie przykrywa niczego poza czystym tlem;
strzalki, przycisk ucieczki i napisy leza ponizej tej linii albo ida juz innym potokiem (`batch`).

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
| `WORST_TURN_LINE` | tekst | - | najdluzszy wariant linii tury (paso gory liczy sie dla niego) |
| `LOG_RESERVE` | tekst | - | najdluzszy wariant logu (paso dolu liczy sie dla niego) |
| `LOG_BAND_LINES` / `LOG_BAND_PAD` | linie | 1.6 / 0.4 | minimalna wysokosc pasa logu i oddech nad logiem |
| `ARROW_ZONE_WIDTH` / `ARROW_ZONE_LINES` | 0..1 / linie | 0.14 / 2.4 | szerokosc kolumny strzalek |
| `PANEL_RESERVE_CELLS` | cell | 1 | ile oczek pudelka sceny oddac prawemu panelowi; plansza przesuwa sie w lewo zamiast sciskac sterowanie (oddawane, gdy `cell` spadlby pod `MIN_CELL`) |
| `COMPASS_BUTTON_WIDTH` | - | 3.4 | przycisk = panel / to |
| `COMPASS_BUTTON_HEIGHT` | 0..1 | 0.24 | ... ale nie wyzszy niz ta czesc wolnej wysokosci |
| `FLEE_HALF_WIDTH_BUTTONS` / `FLEE_HALF_WIDTH_OF_PANEL` | - / 0..1 | 1.5 / 0.45 | polowa szerokosci przycisku ucieczki |
| `FLEE_HALF_HEIGHT_BUTTONS` / `FLEE_HALF_HEIGHT_LINES` | - / linie | 0.4 / 0.75 | polowa wysokosci przycisku ucieczki |
| `FLEE_DROP_BUTTONS` | - | 2.2 | jak nisko pod kompasem siedzi przycisk |
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
| `SHIP_ROOM` | 1.10 | pol kadluba miejsca z boku planszy na statek i znaczniki rezerwy |
| `HORIZON_HEIGHT_CELLS` | 2.55 | wysokosc horyzontu nad stolem, odporna na `NET_SCALE` |

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
| `HULL_COLOR` | 107, 66, 36 | jedyny kolor kadluba - ani pasow poszycia, ani linii wodnej |
| `SAIL_COLOR` | 196, 186, 158 | kolor zagli |
| `HULL_TOP_CELLS` | 2.14 cell | szerokosc kadluba na pokladzie |
| `HULL_KEEL_CELLS` | 1.62 cell | szerokosc kadluba na stepce (wezsza o tyle samo z obu stron) |
| `DECK_LIFT_CELLS` | 0.10 cell | o tyle poklad jest nad gorna krawedzia planszy |
| `KEEL_DROP_CELLS` | 0.50 cell | o tyle stepka schodzi pod dolna krawedz planszy |
| `SHIP_GAP_CELLS` | 0.50 cell | o pol oczka statek (kadlub, poklad, liny) stoi od siatki |
| `SHIP_FIT_MARGIN` | 0.05 cell | tyle wolnego zostaje przed krawedzia ekranu, zanim kadlub sie zwezi |
| `KEEL_FLOOR` | 0.02 cell | stepka nie schodzi nizej niz tyle nad dol sceny |
| `RIGGING_MIN_SKY` | 0.4 cell | minimalna wysokosc takielunku nad pokladem (dolna granica) |
| `RIGGING_OVERFLOW` | 0.5 cell | o tyle takielunek wychodzi ponad gorna krawedz sceny (`sceneTop`) |
| `MAST_POSITION` | 0.42 | gdzie stoi maszt na kadlubie (od krawedzi od siatki) |
| `SAIL_HALF_WIDTH` | 1.5 | polowa szerokosci zagla wzgledem kadluba (pelna szerokosc = 3 kadluby); to szerokosc wzdluz rei, na glowie |
| `SAIL_CLEW_INSET` | 0.06 | o tyle stopa jest wezsza od glowy - boki (liky) zbiegaja sie ku dolowi |
| `SAIL_CLEW_RISE` | 0.2 | o tyle oba narozniki stopy (rogi szotowe) leza wyzej od najglebszego punktu stopy |
| `SAIL_SEGMENTS` | 16 | na ile odcinkow dzielony jest sagi luk stopy |
| `SAIL_BOTTOM` / `SAIL_TOP` | 0.15 / 0.95 | jedyny zagiel, udzialy wysokosci takielunku: stopa 3x wyzej nad pokladem, glowa poza scena |
| `MAST_THICKNESS` / `MAST_TOP` | 0.176 cell / 1 | maszt: grubosc (gruby slup) i jak wysoko siega |

Zagiel, reja i choragiewka zniknely: zostaje sam dol takielunku. Wysokosc takielunku to **cale niebo
nad pokladem plus `RIGGING_OVERFLOW`**, a `drawSceneMask()` ucina wszystko powyzej `sceneTop`, wiec
widac tylko podstawe masztu i dolna czesc zagla. `RIGGING_MIN_SKY` dziala juz tylko jako podloga
wysokosci (na bardzo ciasnym ekranie niebo jest nizsze niz ta wartosc, ale maska i tak utnie gore).

Zagiel jest **3x szerszy od kadluba** (`2 * SAIL_HALF_WIDTH = 3`), wiec nie miesci sie w widoku:
na zewnetrznej stronie ucina go krawedz ekranu (7-24% szerokosci zalezy od rozdzielczosci),
a na srodku planszy oba zagla zostawiaja 2.4-3.7 cell przerwy.

Ksztalt zagla to **kwadratowy zagiel rejowy**: prosta glowa wzdluz rei, dwa liki zbiegajace sie ku
dolowi (`SAIL_CLEW_INSET`) i stopa, ktora **sagi** miedzy rogami szotowymi - jak plachta wypelniona
wiatrem, a nie plaski panel. Stopa to **szescienna krzywa Beziera** wychodzaca z kazdego rogu
w kierunku jego wlasnego liku, wiec stopa wchodzi w lik **bez zalamania**, a rogi sa zaokraglone
(zamiast ostrego "V" z dwoch prostych odcinkow, jak bylo wczesniej).

Geometria statku (`drawShip`):

```
edge   = cellLeft(1)                    // gracz;  cellLeft(WIDTH-1)  // wrog
out    = -1                             // gracz;  +1                 // wrog
near   = edge + shipShift(side)          // burta od strony siatki
room   = gracz ? near - margin : width - margin - near
span   = max(cell, min(cell * HULL_TOP_CELLS, room - cell * SHIP_FIT_MARGIN))
deckY  = boardY + cell * (HEIGHT + DECK_LIFT_CELLS)
keelY  = max(sceneBottom + cell * KEEL_FLOOR, boardY - cell * KEEL_DROP_CELLS)
farDeck= near + out * span
keelInset = span * (1 - HULL_KEEL_CELLS / HULL_TOP_CELLS) / 2
```

Kadlub to dwa wywolania `hullBand`: prostokat od `deckY` do `boardY` i symetryczne zwezenie od
`boardY` do `keelY`. Obie strony zbiegaja sie o ten sam `keelInset`, wiec sylwetka jest symetryczna
wzgledem wlasnego srodka. Takielunek - **tylko jego dol jest widoczny**:

```
sky    = max(cell * RIGGING_MIN_SKY, sceneTop - deckY)   // niebo nad pokladem
rig    = sky + cell * RIGGING_OVERFLOW                   // takielunek siega ponad scene
mastX  = near + out * span * MAST_POSITION
half   = span * SAIL_HALF_WIDTH

drawSailPanel(mastX, half, deckY + rig * SAIL_BOTTOM, deckY + rig * SAIL_TOP)
rect(mastX - cell * MAST_THICKNESS / 2, deckY, cell * MAST_THICKNESS, rig * MAST_TOP)
```

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

Kolejnosc w `drawShip`: najpierw kadlub (`hullBand` x2), potem zagiel, na koncu maszt - maszt
dzieli zagiel na dwie polowy. Zagiel jest rysowany od `deckY + rig * SAIL_BOTTOM` (stopa 3x wyzej
nad pokladem) do `deckY + rig * SAIL_TOP`, a ze `rig` jest wieksze od `sky`, glowa zagla wypada
**powyzej** `sceneTop` i `drawSceneMask()` ucina ja plasko razem z masztem. Efekt: nad pokladem
widac tylko niski pas dolnej czesci zagla i gruby maszt wchodzacy w gore poza ekran.

W praktyce (dla `NET_SCALE` od 1.0 do 1.2) niebo nad pokladem ma 1.16-2.00 cell, wiec widoczny pas
zagla to okolo 79-81% tej wysokosci, a najglebszy punkt stopy wisi 0.25-0.38 cell nad pokladem
(bylo 0.08-0.12 cell - dokladnie 3x wyzej). Sama szerokosc zagla to 2 * 1.5 = 3 szerokosci kadluba,
czyli 4.65-6.42 cell, a maszt ma 0.176 cell grubosci (na telefonie 1080x1920 to okolo 15-19 px,
wczesniej ok. 4 px). Rogi szotowe leza 0.30-0.64 cell nad najglebszym punktem stopy (39 px na
1080x1920, 65 px na 1920x1080), a promien zaokraglenia rogow wychodzi 4.5-21 px (ok. 11 px na
1080x1920). Na linii ciecia `sceneTop` zagiel ma jeszcze 4.58 cell szerokosci z 4.65 cell glowy
(384 px z 390 px), bo lik zbiera tylko `SAIL_CLEW_INSET`; wczesniej glowa zwezala sie do szpica przy
maszcie i na cieciu widac bylo ok. 81% szerokosci - **teraz "3 kadluby" sa prawdziwe takze w tym,
co widac**. Najwieksze odchylenie 16-odcinkowej lamanej od prawdziwej krzywej to 0.9 px na
1080x1920 i 1.4 px na 1920x1080, czyli niewidoczne. Maska nie podcina dolnej krawedzi zagla
(rogi sa 0.42-0.91 cell ponizej `sceneTop`) - ucina tylko jego gore i wierzch masztu.

### 8. Siatka (lina i rozdarcia)

| Parametr | Wartosc | Co robi |
|---|---|---|
| `STRAND_THICKNESS` | 0.04 cell | grubosc liny |
| `STRAND_EDGE` | 0.012 cell | ciemna obwodka wokol liny, zeby byla czytelna nad jasnym morzem |
| `STRAND_TUCK` | 0.06 cell | o tyle pozioma lina wchodzi pod kadlub |
| `STRAND_REACH` | `SHIP_GAP_CELLS + STRAND_TUCK` = 0.56 cell | o tyle liny wychodza poza skrajne linie siatki |
| `HOLE_RADIUS` | 0.38 cell | promien rozdarcia |
| `HOLE_SPOKES` | 6 | ile luznych linii przecina rozdarcie |
| `HOLE_BACKDROP` | 0.86 | jaka czesc promienia pokazuje tlo |
| `HOLE_SPOKE_REACH` / `HOLE_SPOKE_LENGTH` / `HOLE_SPOKE_THICKNESS` / `HOLE_SPOKE_TWIST` | 0.8 / 0.16 cell / 0.030 cell / 0.5 | luzne linie: odsadzenie, dlugosc, grubosc, skret |
| `HOLE_SEGMENTS` | 28 | ile segmentow ma okrag rozdarcia |
| `ROPE_COLOR` / `ROPE_SHADE` | 217,214,199 / 133,128,117 | lina i jej obwodka |
| `GAP_COLOR` | 10, 13, 23 | ciemna szpara w miejscu brakujacego kawalka siatki |

Geometria siatki (`drawNet`):

```
left   = cellLeft(1),  right = cellLeft(WIDTH - 1)      // linie 1..10
bottom = cellBottom(HEIGHT - 1),  top = cellBottom(0) + cell
liny poziome:  HEIGHT + 1 = 12 linii, od left - reach, dlugosc right - left + 2 * reach
liny pionowe:  WIDTH - 1  = 10 linii, na cellLeft(1..10)
```

Czyli **siatka ma 9 oczek w poziomie i 11 w pionie**, a kolumny z lukami (0 i 10) leza poza nia,
na kadlubach. Jedno oczko siatki = jedno pole planszy, jednostka stoi zawsze w srodku oczka, nigdy
na wezle. Poziome liny przechodza przez szczeline `SHIP_GAP_CELLS` i wchodza pod kadlub o
`STRAND_TUCK`, dlatego szczelina czyta sie jako lina, a nie jako blad ukladu.

### 9. Luki, zaloga i znaczniki

| Parametr | Wartosc | Co robi |
|---|---|---|
| `CREW_SHADOW_RADIUS` / `CREW_SHADOW_COLOR` | 0.38 cell / czarny 0.6 | cien pod piratem na siatce |
| `CREW_RADIUS` | 0.32 cell | promien pirata na siatce |
| `CREW_SEGMENTS` | 24 | ile segmentow ma kolo pirata |
| `HATCH_INSET` | 0.12 cell | ramka pola wokol plyty luku |
| `HATCH_DISABLED_COLOR` | 0.35, 0.35, 0.35 | zniszczony luk |
| `HATCH_OPEN_COLOR` | 0.98, 0.79, 0.28 | zloty kolor otwartego luku |
| `HATCH_OPEN_BRIGHTNESS` / `HATCH_OPEN_PULSE` | 0.85 / 0.15 | otwarty luk pulsuje miedzy tymi jasnosciami |
| `HATCH_CLOSED_BRIGHTNESS` | 0.6 | zamkniety luk jest tak przygaszony |
| `HATCH_ENEMY_BRIGHTNESS` | 0.8 | luki wroga sa zawsze przygaszone |
| `HATCH_TAP_REACH` | 1 cell | o tyle pol oczka z kazdej strony poszerzony jest obszar dotyku luku |
| `WAITING_MARKER_INSET` / `WAITING_MARKER_TOP` | 0.36 / 0.82 cell | gdzie zaczyna sie slupek oczekujacych |
| `WAITING_MARKER_STEP` / `WAITING_MARKER_RADIUS` | 0.22 / 0.08 cell | odstep i promien znacznikow |
| `WAITING_MARKER_SEGMENTS` | 12 | ile segmentow ma znacznik |
| `HATCH_PULSE_BASE` / `HATCH_PULSE_SWING` / `HATCH_PULSE_SPEED` | 0.65 / 0.35 / 5 | puls gotowego luku: `base + swing * sin(t * speed)` |
| `ENTRY_MARKER_RADIUS` / `ENTRY_MARKER_INNER` | 0.44 / 0.34 cell | pierscien pokazujacy pole wejscia |
| `ENTRY_MARKER_SEGMENTS` | 24 | ile segmentow ma pierscien |

### 10. Sterowanie

| Parametr | Wartosc | Co robi |
|---|---|---|
| `BUTTON_FACE` | 0.92 | plyta strzalki jako udzial `buttonSize` |
| `BUTTON_FACE_COLOR` | 0.2, 0.2, 0.28 | kolor plyty |
| `ARROW_RADIUS` / `ARROW_TAIL` / `ARROW_WING` | 0.3 / 0.6 / 0.7 | trojkat strzalki wzgledem promienia |
| `ARROW_COLOR` | 0.95, 0.8, 0.35 | kolor strzalki |
| `CONTROL_INACTIVE` | 0.45 | o tyle przygaszone jest wszystko, co teraz nie dziala |
| `FLEE_LABEL_PAD` | 0.4 linie | oddech wokol napisu "UCIECZKA" |
| `FLEE_COLOR` | 0.45, 0.12, 0.12 | ciemna czerwien przycisku ucieczki |

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
| Siatka | `drawNet` / `strand` | 12 lin poziomych + 10 pionowych, kazda jako prostokat w obwodce |
| Rozdarcia | `drawHole` | kolo `GAP_COLOR` + kolo tla + 6 luznych linii |
| Kadlub | `drawShip` / `hullBand` | prostokat od pokladu do linii wody + symetryczne zwezenie do stepki |
| Maszt i zagiel (sam dol) | `drawShip` / `drawSailPanel` | jeden zagiel 3x szerszy od kadluba (wachlarz 18 trojkatow: prosta glowa, liki zbierajace sie ku dolowi, sagi luk stopy) + gruby maszt; oba ucinane od gory przez `drawSceneMask` |
| Kwadraty pokladu | `drawBoard` | pelny kwadrat `cell` w kolorze kadluba w kolumnach lukow |
| Luki | `drawHatch` | plyta z ramka `HATCH_INSET`, kolor zalezny od stanu |
| Slupek oczekujacych | `drawHatch` | jedno male kolo na pirata, od gory luku w dol |
| Znacznik wejscia | `drawEntryMarker` | zloty pierscien z tlem w srodku |
| Zaloga na siatce | `drawBoard` | cien + kolo w kolorze strony |
| Strzalki | `drawControls` | plyta + trojkat na kazdy `JumpDirection` |
| Prawy panel (kompas, ucieczka) | `drawControls` | prawa krawedz planszy + cala rezerwa z `PANEL_RESERVE_CELLS`, zmierzona do `width - margin` |
| Przycisk ucieczki | `drawControls` / `drawFleeLabel` | plyta `FLEE_COLOR` + napis "UCIECZKA" (zmniejszany, gdy nie miesci sie w przycisku) |
| Napisy | `drawTexts` | 4 bloki tekstu + log + etykieta ucieczki |

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
| Komunikaty | `Luk N jest zniszczony.`, `Luk N jest pusty.`, `Luk N zamkniety (czeka N pir.).`, `Luk N otwarty - strzalka wyprowadzi zaloge.`, `Luk N otwarty, ale pole wejsciowe zajete - odsun pirata.` |
| Statusy | `Twoja tura: wyprowadzenie`, `Twoja tura: skok`, `Tura wroga...`, `ZWYCIESTWO!`, `PORAZKA`, `REMIS`, `UCIECZKA` |
| Kierunki | `naprzod`, `ukos w gore`, `ukos w dol` |
| Przycisk | `UCIECZKA` |

## 9. Przepisy na typowe zmiany

| Chce zmienic | Co ruszyc |
|---|---|
| Wieksza / mniejsza siatka miedzy statkami | `NET_SCALE` (horyzont zostaje na miejscu) |
| Odsunac statki od siatki | `SHIP_GAP_CELLS` (liny ida za nimi, bo `STRAND_REACH` liczy sie z tego samego) |
| Inna dlugosc lin w szczelinie | `STRAND_TUCK` |
| Grubsza lina / wyrazniejsza siatka | `STRAND_THICKNESS`, `STRAND_EDGE` |
| Wieksze rozdarcia | `HOLE_RADIUS`, ewentualnie `HOLE_SPOKES` |
| Inny kolor kadluba / zagli | `HULL_COLOR`, `SAIL_COLOR` |
| Szerszy lub wezszy kadlub | `HULL_TOP_CELLS` i `HULL_KEEL_CELLS` (oba, zeby zwezenie zostalo symetryczne) |
| Wysokosc widocznego dolnego pasa zagla | `RIGGING_OVERFLOW`, `SAIL_BOTTOM`, `SAIL_TOP` |
| Szeroki / waski zagiel | `SAIL_HALF_WIDTH` (1.5 = 3 kadluby) |
| Bardziej / mniej wybrzuszona stopa zagla | `SAIL_CLEW_RISE` (0 = stopa prosta, rogi na jej poziomie), `SAIL_CLEW_INSET` (0 = liki pionowe) |
| Gladszy / bardziej kanciasty luk stopy | `SAIL_SEGMENTS` |
| Grubszy / cienszy maszt | `MAST_THICKNESS` |
| Inna wysokosc masztu nad pokladem | `MAST_TOP` |
| Gdzie konczy sie widok (i takielunek) | `MAST_BAND` (niebo) - ciecie idzie po `sceneTop` |
| Inne polozenie masztu | `MAST_POSITION` |
| Wiecej / mniej nieba i morza | `MAST_BAND`, `SEA_DEPTH` (proporcja miedzy nimi dzieli nadwyzke sceny) |
| Wyszej / nizej horyzont | `HORIZON_HEIGHT_CELLS` |
| Inne slonce | `SUN_*` |
| Inne chmury | `CLOUD_LAYOUT` (dodaj / usun wiersz) |
| Spokojniejsze morze | `WAVE_ROWS`, `WAVE_SPAN`, `WAVE_SPAN_GROWTH`, `WAVE_GAP` |
| Mniej lub wiecej blyskow slonca | `GLITTER_COUNT`, `GLITTER_ALPHA` |
| Wiecej miejsca na tekst | `LOG_BAND_LINES`, `TEXT_GAP`, `MARGIN_*` |
| Wiecej miejsca na sterowanie po prawej | `PANEL_RESERVE_CELLS` (1 = jedno oczko; plansza idzie w lewo, a panel rosnie o ta sama szerokosc) |
| Wieksze strzalki | `COMPASS_BUTTON_WIDTH`, `COMPASS_BUTTON_HEIGHT`, `ARROW_ZONE_*` |
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

Projekt nie ma testow wizualnych ani modulu desktop, wiec jedyne sprawdzenie to uruchomienie na
Androidzie (`android/`) albo powtorzenie matematyki z sekcji 3 i 5 w skrypcie rysujacym. Przy
zmianach parametrow warto najpierw policzyc `cell`, `boardX`, `boardY` i `horizonY` dla najwezszego
i najszerszego wspieranego ekranu: pasy tekstu i `arrowZone` zjadaja szerokosc, wiec `netScale`
moze spasc do 1 i wowczas siatka jest mniejsza, niz wynika z `NET_SCALE`.
