# StickerLab — çalışma notları

Kişisel WhatsApp sticker maker. Kotlin + Jetpack Compose, tek modül, reklamsız.
Google Play **dahili test** kanalından davetli arkadaşlara dağıtılıyor.

| Doküman | İçerik |
|---|---|
| [STICKERLAB.md](STICKERLAB.md) | Kapsam kararları, WhatsApp API sözleşmesi, bilinen tuzaklar |
| [EKRANLAR.md](EKRANLAR.md) | Ekran akışı, UI kararları ve gerekçeleri |
| [TASARIM.md](TASARIM.md) | Tasarım yenileme planı — devam eden iş |
| [PLAY_YAYIN.md](PLAY_YAYIN.md) | Play Console adımları, imza, data safety cevapları |

---

## Durum

**M1–M7 tamam, uygulama yayında** (dahili test, arkadaşlar kurdu).

- ContentProvider ile WhatsApp entegrasyonu
- WebP export (512×512, 100 KB limitine quality binary search)
- Fotoğraftan sticker, isteğe bağlı ML Kit arka plan silme
- Editör: sil/geri getir fırçası, lasso, renkli kalem, metin/emoji, zoom-pan, undo/redo
- Çoklu pack: detay, seçim modu, taşıma/kopyalama, tray ikonu

**Devam eden:** tasarım yenilemesi. Faz 1–2 bitti (tema + ikonlar), Faz 3'ten
devam — bkz. [TASARIM.md](TASARIM.md).

---

## Komutlar

`JAVA_HOME` **şart**: sistemdeki `java` sürüm 8, Gradle 17+ istiyor.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease          # Play için AAB
```

### Testler (55+ tane, hepsi cihazda çalışır)

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.atakan.stickerlab.test/androidx.test.runner.AndroidJUnitRunner
```

`./gradlew connectedDebugAndroidTest` **kullanma** — MIUI split-install'ı reddediyor
(`INSTALL_FAILED_USER_RESTRICTED`). Yukarıdaki elle yol çalışıyor.

### Tasarım turu

`DesignPreviewTest` bileşenleri cihazda çizip PNG kaydeder — uygulamayı kurup
ekranlarda gezinmeden tasarıma bakmak için:

```bash
adb shell am instrument -w -e class com.atakan.stickerlab.ui.DesignPreviewTest \
  com.atakan.stickerlab.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.atakan.stickerlab/files/design
```

---

## Tuzaklar (hepsi bir kez canımızı yaktı)

### Proje yolu ASCII değil

`OneDrive/Masaüstü/...` — AGP bunu reddediyor. `gradle.properties` içindeki
`android.overridePathCheck=true` kontrolü kapatıyor. Şu an sorunsuz derleniyor;
açıklanamayan dosya/yol hatalarında ilk şüpheli burası. Native kod (NDK)
eklenirse yol taşınmalı.

### Provider'ın `readPermission`'ı zorunlu

`android:readPermission="com.whatsapp.sticker.READ"` olmadan WhatsApp provider'ı
Android 11+ package visibility yüzünden **hiç göremiyor** (WhatsApp
`QUERY_ALL_PACKAGES` iznine sahip değil). Belirti: `adb logcat -s StickerProvider`
bomboş, hata `handleStickerPackPreviewResult/failed`.

Yan etkisi: `adb shell content query` bu provider'da çalışmaz (shell o izne sahip
değil). Teşhis için logcat kullan.

### Telefon: Redmi / HyperOS

- **Yeni paket kurulumu** telefonda onay ekranı çıkarıyor; `adb install` o onay
  verilmezse `INSTALL_FAILED_USER_RESTRICTED` ile düşüyor. Kullanıcıya "telefona
  bak, Yükle'ye bas" demek gerekiyor. Arka planda çalıştırıp mesaj göndermek işe yarıyor.
- **Girdi enjeksiyonu engelli** (`adb shell input tap` → SecurityException). UI'ı
  parmakla test etmek gerekiyorsa kullanıcıdan rica et; Compose UI testleri
  (`performTouchInput`) uygulama içi olduğu için **çalışıyor**.
- Play'den kurulan sürüm Google'ın imzasıyla; `adb` ile kurduğumuz kendi upload
  anahtarımızla. İkisi çakışır, biri kaldırılmadan diğeri kurulamaz.

### Compose UI testleri için ekran açık olmalı

Ekran kapalıyken "No compose hierarchies found" hatası verir:

```bash
adb shell svc power stayon true
```

### Emülatör API 37

`Resizable_Experimental` AVD'si API 37. Eski Espresso orada
`InputManager.getInstance` arayıp patlıyor — `espresso-core:3.7.0` gerekiyor
(eklendi). Emülatör tasarım turları için ideal: telefondaki Play sürümüne
dokunmadan ekran görüntüsü alınabiliyor.

### Kabuk

Kullanıcı **PowerShell 5.1** kullanıyor: `&&` yok (`;` ve `if ($?)`), `mkdir -p`
yok (`New-Item -ItemType Directory -Force`), yol çağırmak için `&` gerekiyor.
Ona komut verirken PowerShell sözdizimi yaz.

Türkçe kesme işareti (`'`) tek tırnaklı `node -e` ve heredoc'ları bozuyor —
uzun betikleri dosyaya yaz, öyle çalıştır.

---

## Mimari notlar

**Editör motoru iki katman** ([StickerEditorState](app/src/main/java/com/atakan/stickerlab/ui/editor/StickerEditorState.kt)):
maske (orijinalin hangi pikseli görünür) + overlay (üstüne eklenenler).
Sonuç = (orijinal ∩ maske) + overlay. Silgi overlay'e dokunmaz.

Undo, snapshot değil **işlem listesi**; undo'da katmanlar baştan çizilir.
Snapshot adım başına ~5 MB tutardı.

**Pack kuralları** [PackRules](app/src/main/java/com/atakan/stickerlab/data/PackRules.kt)
içinde tek yerde: UI butonları kapatmak için, repository son savunma olarak
aynı fonksiyonları çağırıyor. Bu kurallar ihlal edilince WhatsApp hata vermez,
pack sessizce bozulur — o yüzden testle sabitlenmiş durumda.

**R8 küçültme kapalı** (`isMinifyEnabled = false`), bilinçli: Room/Hilt/ML Kit
yansıma kullanıyor, kırılırsa hata kullanıcıda çıkar. Birkaç düzine kişilik
dağıtımda boyut kazancı bu riski karşılamıyor. Açılırsa **release derlemesiyle
cihazda test edilmeli**, derlenmesi yeterli değil.

---

## Yayın

Paket adı **`com.atakan.stickerlab`** — kalıcı, değiştirilemez.

İmza anahtarı `C:\Users\ataka\keys\stickerlab.jks`, parolası yalnızca kullanıcıda.
`keystore.properties` gitignore'da. Anahtar kaybolursa uygulama bir daha
güncellenemez.
