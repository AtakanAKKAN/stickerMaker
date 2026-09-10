# StickerLab

Milestone 1-7 tamam: Room + ContentProvider + WhatsApp entegrasyonu, WebP export
pipeline, galeri fotoğrafından sticker üretimi (isteğe bağlı arka plan silme),
sticker editörü (sil / geri getir, fırça ve lasso, renkli kalem, metin ve emoji,
zoom-pan, undo/redo) ve çoklu pack yönetimi (pack detayı, seçim modu,
taşıma/kopyalama, tray ikonu).
Sırada: Play Console internal testing yayını — adımlar PLAY_YAYIN.md dosyasında.

Proje kararları ve yol haritası için [STICKERLAB.md](STICKERLAB.md).
Ekran akışı ve UI kararları için [EKRANLAR.md](EKRANLAR.md).
Play Store yayını için [PLAY_YAYIN.md](PLAY_YAYIN.md).

## Derleme

Sistemdeki `java` sürümü 8; Gradle'ın JDK 17+ istemesi nedeniyle Android Studio'nun
JBR'si kullanılıyor:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

Android Studio'dan açıldığında bu ayar zaten gömülü gelir.

### Yol uyarısı

Proje `OneDrive/Masaüstü/...` altında ve yol ASCII olmayan karakter içeriyor.
AGP bunu normalde reddediyor; `gradle.properties` içindeki
`android.overridePathCheck=true` kontrolü kapatıyor. Şu an sorunsuz derleniyor,
ama açıklanamayan dosya/yol hatalarında ilk şüpheli burasıdır — native kod
(NDK) eklenirse yol taşınmalı.

## Provider'ı izleme

```bash
adb logcat -c && adb logcat -s StickerProvider
```

Uygulamada butona bas. Beklenen sıra: `metadata` -> `stickers/<id>` -> her sticker
ve tray için `stickers_asset/...`. Hiçbir satır gelmiyorsa WhatsApp provider'ı
göremiyor demektir (bkz. Bilinen Tuzaklar #9).

`adb shell content query` bu provider'da **çalışmaz**: `readPermission`
tanımlı olduğu için shell erişemez. Bu izin WhatsApp'ın provider'ı görebilmesi
için zorunlu, kaldırılamaz.

## Doğrulanan / doğrulanmayan

- [x] Dört URI de çalışıyor; `stickers_asset` geçerli 512×512 WebP ve 96×96 PNG döndürüyor
- [x] WhatsApp kurulu değilken buton disable oluyor
- [x] Gerçek cihazda WhatsApp'a ekleme — Redmi (HyperOS), WhatsApp 2.26.34.81 ile doğrulandı
- [x] 55/55 cihaz testi geçiyor (export + görsel yükleme + editör motoru + jestler + pack kuralları)
- [x] Maske motoru: silme, orijinalden geri getirme, lasso, undo/redo, darbe iptali
- [x] Taşıma/kopyalama kuralları: 3 sticker sınırı, dolu hedef, iki packin sürüm artışı
- [x] Overlay katmanı: renkli kalem, metin/emoji yerleştirme, silginin overlaye dokunmaması
- [ ] Foto seç -> işlemler -> kaydet akışı — cihazda elle test edilecek

## Testler

Export pipeline'ın tek işi WhatsApp'ın bayt limitlerine sığmak. `Bitmap.compress`
çıktısı cihaza ve Android sürümüne göre değiştiği için doğrulama cihazda yapılıyor:

```bash
./gradlew :app:connectedDebugAndroidTest
```

MIUI/HyperOS bu komutu `INSTALL_FAILED_USER_RESTRICTED` ile reddediyor (Gradle'ın
split-install oturumunu engelliyor). O durumda elle:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.atakan.stickerlab.test/androidx.test.runner.AndroidJUnitRunner
```

**Telefonun ekranı açık olmalı.** Jest testleri (`StickerEditorGestureTest`)
Compose ekranını gerçekten çiziyor; ekran kapalıyken "No compose hierarchies
found" hatası verir. Ekranı açık tutmak için:

```bash
adb shell svc power stayon true
```

Redmi (HyperOS, API 36) üzerinde ölçülen çıktılar:

| Girdi | Sonuç |
|---|---|
| Kesilmiş şekil, 1024×1024 | 12 KB, quality 100 |
| Küçük kaynak, 64×64 | 25 KB, quality 100 |
| Rastgele gürültü (en kötü durum) | 100 KB, quality 45 |
| Tray, 96×96 PNG | 1.3 KB |
