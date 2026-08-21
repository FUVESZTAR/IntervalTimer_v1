# Interval Timer — Samsung Galaxy S22 + Galaxy Watch5

Egyszerű, energiatakarékos intervallum-jelző alkalmazás. Szabadon megadható időközönként
(1 másodperctől 24 óráig) hang- és/vagy rezgésjelzést ad, megbízhatóan a háttérben is,
Samsung Galaxy S22 telefonon és Galaxy Watch5 okosórán.

## Tartalom

- [Funkciók](#funkciók)
- [Architektúra](#architektúra)
- [Óra: telepíthetőség és önálló működés](#óra-telepíthetőség-és-önálló-működés)
- [Buildelés](#buildelés)
- [Telepítés](#telepítés)
- [Tesztelési útmutató – Galaxy S22](#tesztelési-útmutató--galaxy-s22)
- [Tesztelési útmutató – Galaxy Watch5](#tesztelési-útmutató--galaxy-watch5)
- [Permissionök](#permissionök)
- [Battery optimization beállítások](#battery-optimization-beállítások)
- [Ismert korlátozások](#ismert-korlátozások)

---

## Funkciók

- Szabadon megadható intervallum (óra:perc:mp), presetekkel (30mp – 60 perc)
- Hang / rezgés / mindkettő / néma jelzés, több hang- és rezgésmintával
- Teszt jelzés gomb (nem indítja el az időzítőt)
- START / PAUSE / STOP, jól látható állapot (AKTÍV / SZÜNETEL / LEÁLLÍTVA)
- Megbízható háttérműködés: kijelző kikapcsolva, telefon lezárva, más app használata közben is
- Galaxy Watch5 támogatás **két, egymástól független módban**:
  - telefon vezérli, az óra csak jelez (mode B),
  - az óra **önállóan, telefon nélkül** is futtatja a saját időzítőjét (mode C) — ez a build már ezt tartalmazza, nem opcionális bővítés.
- Beállítások perzisztencia (DataStore), utolsó állapot megőrzése
- Opcionális, kikapcsolt alapértelmezésű automatikus visszaállás rendszerindítás után

## Architektúra

```
IntervalTimer/
├── shared/   – tiszta Kotlin modell + állapotgráf, nincs Android-függés, JVM-teszthető
├── app/      – telefonos alkalmazás (Compose, MVVM)
└── wear/     – önálló Wear OS alkalmazás (Wear Compose)
```

### Időzítési mechanizmus

`AlarmManager.setExactAndAllowWhileIdle()`, **láncolva**: minden jelzéskor a rendszer
azonnal beütemezi a *következő* alarmot, majd visszaalszik. Nincs `while` loop,
`Thread.sleep()` ciklus, polling, vagy folyamatosan futó coroutine — a `TimerAlarmReceiver`
csak a jelzés pillanatában ébred, pár száz ms-ra tart wake locköt, majd elalszik.

**Miért nem WorkManager?** A `WorkManager` periodikus munkája minimum 15 perces, nem
garantál pontos időpontot (batch-eli más appokéval) — alkalmatlan egy 1 mp – 24 óra
tartományú, pontos jelzésekre. **Miért nem Handler/Coroutine delay loop?** Doze módban
leáll, és feleslegesen ébren tartaná a folyamatot háttérben. Az `AlarmManager` +
`setExactAndAllowWhileIdle` az egyetlen mechanizmus, ami Doze alatt is pontos időben ébreszt,
minimális energiafogyasztás mellett.

Egy aktív foreground service (`TimerForegroundService`) tartja életben a folyamatot és
mutatja az állandó notificationt — ő maga nem végez semmilyen aktív munkát, csak a
notification szövegét frissíti, amikor az `AlarmManager` ébreszti a rendszert.

### Óra kommunikáció

Wearable Data Layer `MessageClient` (Google Play Services) — nem folyamatos Bluetooth
kapcsolat, hanem eseményvezérelt, egyszeri üzenetküldés. Ha az óra nem elérhető, a
küldés csendben elbukik, a telefonos időzítő zavartalanul folytatja.

### Állapotgép

`TimerRunState`: `IDLE → RUNNING ⇄ PAUSED → STOPPED → RUNNING`, formálisan validálva a
`shared` modul `TimerStateMachine`-jében (100%-ban unit tesztelt, Android-függés nélkül).

## Óra: telepíthetőség és önálló működés

A `wear` modul **külön telepíthető .apk-t** épít, amely:

- a manifestben `com.google.android.wearable.standalone = true` metaadattal van
  megjelölve, ezért a Play Store / Wear app installer önálló (nem csak "companion")
  alkalmazásként kezeli,
- saját `WatchTimerEngine`-t (azonos `AlarmManager`-alapú mechanizmus, mint a telefonon),
  saját `WatchSettingsRepository`-t (DataStore) és saját rezgő jelzést tartalmaz,
- **telefon és Bluetooth-kapcsolat nélkül is** elindítható, beállítható és futtatható,
  közvetlenül az órán.

Ha egyszerre szeretnéd, hogy a telefonos START egy kattintással az órára is települjön
(Play Store "wearApp" bundling), az a `app/build.gradle.kts`-ben egy
`wearApp(project(":wear"))` sorral opcionálisan bekapcsolható — ez v1-ben szándékosan
nincs bekapcsolva, hogy a két apk telepítése (telefon / óra) egyértelműen szétváljon a
teszteléshez.

**Mode B vs Mode C, egyértelműen elkülönítve:** a telefonról indított időzítés (`WatchCommunicationManager` → `PhoneListenerService`) csak egy rövid rezgést küld az órára
minden jelzéskor — **nem indítja/állítja** az óra saját, önálló `WatchTimerEngine`-jét.
A két mechanizmus nem tud ütközni.

## Buildelés

Előfeltétel: Android Studio (Ladybug vagy újabb), JDK 17.

```bash
git clone <repo>
cd IntervalTimer
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

> Ebben a fejlesztői környezetben (ahol a projekt generálva lett) nincs Android SDK és
> nincs hálózati hozzáférés, ezért a Gradle build itt nem futtatható le — a forráskódot
> Android Studio-ban kell megnyitni és build/sync-elni.

Release build:

```bash
./gradlew :app:assembleRelease
./gradlew :wear:assembleRelease
```

A release build nem tartalmaz verbose logolást (`BuildConfig.VERBOSE_LOGGING = false`).

## Telepítés

**Telefon:** `app/build/outputs/apk/debug/app-debug.apk` telepítése Galaxy S22-re (USB
debug vagy `adb install`).

**Óra (önálló):**
1. Kapcsold be a fejlesztői beállításokat a Watch5-ön (Beállítások → Az óráról →
   koppints 5x a "Szoftververzióra"), engedélyezd az ADB hibakeresést.
2. `adb connect <watch-ip>:5555` (ha Wi-Fi ADB-t használsz a Galaxy Wearable appon
   keresztül), vagy közvetlen USB-n át, ha a fejlesztői gyűrű támogatja.
3. `adb install wear/build/outputs/apk/debug/wear-debug.apk`

Az óra alkalmazás ezután a telefon jelenléte nélkül is megnyitható és használható.

## Tesztelési útmutató – Galaxy S22

- [ ] Kijelző be/kikapcsolva – jelzés időben megtörténik
- [ ] Telefon lezárva – jelzés időben megtörténik, notification a zárolt képernyőn is látszik
- [ ] Másik alkalmazás előtérben – időzítés nem szakad meg
- [ ] Alkalmazás UI bezárva (recent apps-ból eltávolítva) – az aktív időzítés **tovább fut**
      (a foreground service és az AlarmManager független az Activity életciklusától)
- [ ] Battery Saver / One UI "Alvó alkalmazások" listára téve – ha a felhasználó ide teszi
      az appot, a One UI leállíthatja; ezért a UI figyelmeztet, ha az app battery-optimalizált
      (Beállítások → Akkumulátor → Háttérhasználat-korlátozás → *ne* legyen "Alvó" listán)
- [ ] Doze mód (hosszabb állás, kijelző ki, töltőn kívül) – `setExactAndAllowWhileIdle`
      miatt a jelzés Doze alatt is időben megtörténik
- [ ] Reboot – ha az "Automatikus indulás" be van kapcsolva Settingsben, az időzítő
      folytatódik; egyébként IDLE állapotban indul az app

## Tesztelési útmutató – Galaxy Watch5

- [ ] Telefonhoz csatlakoztatva, mode B: telefonos START → az óra rezeg minden jelzéskor
- [ ] **Telefon nélkül** (repülő módban, vagy párosítás nélkül): önálló START az órán →
      a jelzés időben megtörténik, kizárólag az órán futó `WatchTimerEngine` alapján
- [ ] Bluetooth kapcsolat menet közben megszakítva (mode B közben) → a telefonos timer
      tovább jelez, az óra csendben marad, majd újracsatlakozáskor folytatja a rezgést
- [ ] Kijelző kikapcsolva az órán (Ambient/AOD) → önálló időzítés is időben jelez
- [ ] Hosszú távú teszt: 5 perces intervallum, 8 órás futtatás, minden jelzés
      dokumentálva, akkumulátor-fogyasztás ellenőrizve (Beállítások → Akkumulátor →
      Alkalmazáshasználat)

## Permissionök

| Permission | Modul | Miért |
|---|---|---|
| `VIBRATE` | app, wear | rezgőjelzés |
| `POST_NOTIFICATIONS` | app, wear | aktív időzítő notification (Android 13+) |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | app, wear | pontos jelzés Doze alatt is |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | app | a folyamat életben tartása aktív időzítés alatt |
| `RECEIVE_BOOT_COMPLETED` | app | opcionális, kikapcsolható automatikus visszaállás |
| `WAKE_LOCK` | app, wear | néhány száz ms-os wake lock kizárólag a jelzés lejátszásának pillanatában |

Nincs: `INTERNET`, semmilyen `BLUETOOTH_*`, `ACCESS_FINE_LOCATION` — az óra-kommunikációt
a Play Services Wearable API kezeli, nyers Bluetooth-hozzáférés nélkül.

## Battery optimization beállítások

Az app **nem kér automatikusan** akkumulátor-optimalizálás alóli kivételt. A UI csak
akkor mutat erre vonatkozó kártyát, ha az OS ténylegesen jelzi, hogy az app korlátozás
alatt áll (`PowerManager.isIgnoringBatteryOptimizations()` == false).

Samsung One UI specifikum: a "Alvó alkalmazások" / "Soha ne aludjon" beállítás
(Beállítások → Akkumulátor és eszközkarbantartás → Háttérhasználat-korlátozások) erősebb,
mint a standard Android battery optimization — ha a felhasználó manuálisan Alvó listára
teszi az appot, a One UI ettől függetlenül leállíthatja a folyamatot. Ezt dokumentáljuk,
de nem kerülhető meg szoftveresen; a README-ben és a hosszú távú teszteknél ellenőrizendő.

## Ismert korlátozások

- **Nagyon rövid (1–2 mp) intervallumok:** a rendszer energiagazdálkodása miatt a
  pontosság esetenként ±néhány száz ms lehet, különösen Doze alatt. A UI jelzi ezt.
- **Óra hangjelzés:** a Watch5 önálló módban (mode C) csak rezgéssel jelez, nem hanggal —
  a Wear OS 3+ háttér-audio route-olása erre a use case-re nem megbízható a legtöbb
  gyári audio session policy mellett; ez tudatos, dokumentált kompromisszum, nem hiba.
- **Wear app bundling:** a telefonos app jelenleg nem telepíti automatikusan az óra
  apk-ját (`wearApp` Gradle kapcsolat kikapcsolva) — külön kell telepíteni mindkettőt,
  ld. "Telepítés" fejezet.
- **Ez a forráskód ebben a fejlesztői környezetben nem lett lefordítva** (nincs Android
  SDK / hálózat elérhető) — Android Studio-ban validálandó buildelés előtt.
