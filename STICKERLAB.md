# StickerLab — Proje Dokümanı

Kişisel WhatsApp sticker maker. Reklamsız, Android-only, Google Play **internal testing** track'i üzerinden sadece davetli arkadaşlara dağıtılacak. Production'a çıkma hedefi yok.

---

## 1. Kapsam ve Kararlar

### Neden bu proje
Marketteki sticker uygulamalarının reklam yoğunluğu. WhatsApp'ın kendi içindeki sticker maker yetersiz.

### Teknoloji
Saf **Kotlin + Jetpack Compose**, tek modül. React Native / Expo **kullanılmıyor** — ContentProvider, ML Kit ve WebP encode zaten native yazılacak; RN sadece bridge maliyeti ekler.

### v1 kapsamı
- Galeri fotoğrafından sticker üretimi
- Otomatik arka plan silme (ML Kit Subject Segmentation)
- Manuel rötuş: sil / geri getir fırçası + undo
- Serbest lasso kesim
- Çoklu pack, pack'ler arası taşıma/kopyalama
- WhatsApp'a pack ekleme

### v2'ye ertelenenler
- Boş canvas'a serbest çizim (v1 altyapısıyla hiçbir şey paylaşmıyor, ayrı uygulama büyüklüğünde)
- Animasyonlu sticker (libwebp NDK derlemesi veya ffmpeg bundle = +25MB APK, kişisel kullanım için maliyeti faydasından fazla)

### Bilinçli kabul edilen sınır
Arka plan kesme kalitesi Google'ın ML Kit modelinin kalitesidir. Marketteki uygulamalar da aynı modeli kullanıyor; otomatik kesimde onlardan daha iyi olunamaz. Fark yaratılacak yer **manuel rötuş aracının kalitesi** — çoğu rakipte ya yok ya kötü.

---

## 2. Dağıtım

### Track seçimi: Internal Testing
| | Internal | Closed | Open |
|---|---|---|---|
| Tester limiti | 100 | Sınırsız | Herkes |
| Google incelemesi | Yok | Var (1–3 gün) | Var |
| 12 tester / 14 gün şartı | **Yok** | Var | — |
| Güncelleme hızı | Dakikalar | Günler | Günler |

12 tester / 14 gün kuralı **sadece closed testing** track'ine ait ve tek amacı **production erişimi açmak**. Production hedefi olmadığı için o kapıya hiç girilmiyor. Closed testing'e geçmek her release'i Google incelemesine sokar, karşılığında hiçbir şey kazandırmaz.

### Yükleme öncesi zorunlu formaliteler
Herhangi bir track'e yükleyebilmek için Play Console'da "App content" bölümü tamamlanmalı:
- Data safety formu (uygulama hiçbir veriyi cihaz dışına çıkarmıyor → 5 dakikalık iş)
- Content rating anketi
- Target audience
- Privacy policy URL (GitHub Pages'e tek sayfa HTML yeterli)

### Teknik zorunluluk
31 Ağustos 2026'dan itibaren yeni uygulamalar **API 36 (Android 16)** target etmek zorunda.

---

## 3. WhatsApp Sticker API — Referans

Resmi kaynak: `github.com/WhatsApp/stickers` (BSD lisanslı, Android + iOS örnekleri). API key yok, başvuru yok, onay yok, maliyet yok.

### Format kuralları
Bunlara uyulmazsa WhatsApp pack'i **sessizce reddeder, hata mesajı vermez.**

| Öğe | Kural |
|---|---|
| Sticker boyut | 512 × 512 px, WebP |
| Sticker dosya boyutu | statik ≤ 100 KB (animasyonlu ≤ 500 KB) |
| Tray icon | 96 × 96 px, PNG veya WebP, ≤ 50 KB |
| Pack başına sticker | min 3, max 30 |
| Uygulama başına pack | max 10 |
| Emoji tag | her sticker için en az 1 |
| Uygulama adı | içinde "WhatsApp" **geçemez** |

### ContentProvider sözleşmesi

Authority: `${applicationId}.stickercontentprovider`
Manifest'te `exported="true"` **ve** `readPermission="com.whatsapp.sticker.READ"`.

> **readPermission zorunlu.** Bu satır olmadan WhatsApp provider'ı Android 11+
> package visibility kuralları nedeniyle hiç göremiyor (WhatsApp `QUERY_ALL_PACKAGES`
> iznine sahip değil). Provider tek bir sorgu bile almadan ekleme
> `handleStickerPackPreviewResult/failed` ile düşüyor. Resmi örnekte ve marketteki
> çalışan uygulamalarda bu satır var. 2026-09-10'da bu yüzden bir tur kaybedildi.

**URI'ler:**
```
/metadata                              → tüm packler
/metadata/<identifier>                 → tek pack
/stickers/<identifier>                 → pack'in sticker'ları
/stickers_asset/<identifier>/<file>    → openAssetFile ile WebP döner
```

**`/metadata` cursor kolonları — isimler birebir bunlar olmak zorunda:**
```
sticker_pack_identifier
sticker_pack_name
sticker_pack_publisher
sticker_pack_icon
android_play_store_link
ios_app_download_link
sticker_pack_publisher_email
sticker_pack_publisher_website
sticker_pack_privacy_policy_website
sticker_pack_license_agreement_website
image_data_version
whitelisted
animated_sticker_pack
```

**`/stickers` cursor kolonları:**
```
sticker_file_name
sticker_emoji                  ← noktalı virgülle ayrılır: "😂;🔥"
sticker_accessibility_text
```

**`whitelisted` → her zaman `0`.** `1` verilirse WhatsApp pack'i Play Store'da yayınlanmış sanıp cache'ler, geliştirme sırasında değişiklikler görünmez.

### Ekleme intent'i
```kotlin
Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
    putExtra("sticker_pack_id", identifier)
    putExtra("sticker_pack_authority", "$packageName.stickercontentprovider")
    putExtra("sticker_pack_name", name)
    setPackage("com.whatsapp")
}
```
`startActivityForResult` ile açılır. Dönen `resultCode` ve `"validation_error"` extra'sı loglanmalı.

Manifest'e **zorunlu**:
```xml
<queries>
    <package android:name="com.whatsapp" />
    <package android:name="com.whatsapp.w4b" />
</queries>
```
API 30+ package visibility nedeniyle bu olmadan `com.whatsapp` görünmez ve intent sessizce patlar.

---

## 4. Bilinen Tuzaklar

1. **Cache invalidation.** Zaten eklenmiş bir pack değiştiğinde `image_data_version` artırılıp `contentResolver.notifyChange()` çağrılmazsa WhatsApp'ta hiçbir şey değişmez. En çok zaman kaybettiren konu — baştan doğru kurulmalı.

2. **Minimum 3 sticker kuralı taşımayı kırar.** 4 sticker'lı pack'ten 2 tanesi taşınırsa kaynak pack geçersiz hale gelir ve WhatsApp'ta sessizce bozulur. UI bu hareketi engellemeli.

3. **Taşıma iki pack'i birden etkiler.** Taşıma/kopyalama sonrası **hem kaynak hem hedef** pack'in `image_data_version`'ı artırılıp `notifyChange()` çağrılmalı.

4. **`identifier` değişmez olmalı.** UUID kullanılır, kullanıcının verdiği isimden türetilmez — isim değişince pack sıfırlanır.

5. **Sticker dosyaları pack'e aittir.** Aynı sticker'ı iki pack'te göstermek = iki ayrı WebP dosyası. DB'de kaynak görsel paylaşımlı tutulup export'ta kopyalanmalı.

6. **WebP quality sabit verilemez.** 100 KB limitine sığdırmak için quality üzerinde binary search yapılmalı; sabit quality bazı görsellerde limiti aşar.

7. **ML Kit segmentation Play Services üzerinden indirilen bir modül.** İlk kullanımda indirme gecikmesi var, handle edilmeli.

8. **Zoom/pan + fırça koordinatları.** Ekran uzayından mask uzayına `Matrix.invert()` + `mapPoints` ile çevrilmeli. Baştan doğru kurulmazsa sonradan sökülmesi zor bir bug kaynağı.

9. **Provider'ın `readPermission`'ı.** `com.whatsapp.sticker.READ` tanımlanmazsa
   WhatsApp provider'ı hiç göremez, tek sorgu bile gelmez ve ekleme sessizce düşer.
   Belirti: `adb logcat -s StickerProvider` bomboş, hata mesajı
   `handleStickerPackPreviewResult/failed`.

---

## 5. Milestone'lar

| # | Milestone | Not |
|---|---|---|
| **1** | Proje iskeleti + Room şeması + ContentProvider + WhatsApp'a ekleme | **En riskli entegrasyon, ilk gün doğrulanacak.** Dummy WebP ile. |
| 2 | WebP export pipeline (512×512, quality binary search, tray icon) | Boyut limitleri burada çözülür |
| 3 | Photo Picker + ML Kit segmentation → otomatik sticker | Uygulama burada fiilen kullanılabilir hale gelir |
| 4 | Mask editörü: zoom/pan + sil/geri getir fırçası + undo | En büyük parça, birkaç gün |
| 5 | Lasso kesim | 4'ün üstüne küçük ek |
| 6 | Çoklu pack, taşıma/kopyalama, versiyon invalidation | |
| 7 | Play Console internal testing release | |
| — | Çizim canvas'ı | v2 |

**Milestone 1 atlanamaz.** ContentProvider'ı sona bırakan herkes yanıyor: WhatsApp hata vermiyor, sadece pack'i eklemiyor, debug etmek işkence.

### Mask motoru notu (M4–M5)
Rötuş, lasso ve undo bağımsız üç özellik değil — hepsi tek bir maskeye dayalı düzenleme motoru üzerinde çalışır:
- ML Kit → 8-bit grayscale alpha mask
- Silgi fırçası = maskeye siyah boyamak
- Geri getirme fırçası = maskeye beyaz boyamak
- Lasso = kapalı `Path`'i maskeye fill etmek
- Undo/redo = mask snapshot stack'i

Motor bir kez doğru kurulursa lasso neredeyse bedava gelir.

---

## 6. Milestone 1 — Uygulama Spec'i

### Proje ayarları
```
applicationId  com.atakan.stickerlab      ← "whatsapp" geçmiyor
minSdk         26
targetSdk      36
compileSdk     36
Kotlin + Compose + Room + Hilt
```

### Dosya yapısı
```
app/src/main/java/com/atakan/stickerlab/
├─ data/
│  ├─ db/          AppDatabase, StickerPackDao
│  ├─ entity/      StickerPackEntity, StickerEntity
│  └─ StickerRepository.kt
├─ provider/
│  ├─ StickerContentProvider.kt
│  └─ StickerContract.kt        ← kolon ismi sabitleri
├─ whatsapp/
│  ├─ WhatsAppInstallChecker.kt
│  └─ AddPackToWhatsApp.kt
└─ ui/
   └─ packlist/
```

### Disk düzeni
```
filesDir/packs/<packIdentifier>/
├─ tray.png
├─ <stickerUUID>.webp
├─ <stickerUUID>.webp
└─ <stickerUUID>.webp
```

### Claude Code prompt'u

> Kotlin + Jetpack Compose ile sıfırdan bir Android projesi kur. applicationId `com.atakan.stickerlab`, minSdk 26, targetSdk 36, compileSdk 36. Room ve Hilt ekle.
>
> Amaç: WhatsApp third-party sticker pack API'sinin ContentProvider tarafını uçtan uca çalışır hale getirmek. Sticker üretimi bu aşamada YOK — assets klasöründen kopyalanan sabit 3 adet dummy WebP kullanacağız.
>
> Yapılacaklar:
>
> 1. Room şeması: `StickerPackEntity` (id, identifier UUID string, name, publisher, trayFileName, imageDataVersion Int, animated Boolean, createdAt) ve `StickerEntity` (id, packId FK, fileName, emojis String, accessibilityText). CASCADE delete.
>
> 2. İlk açılışta `assets/seed/` içindeki 3 adet 512x512 WebP ve 1 adet 96x96 tray PNG'yi `filesDir/packs/<packIdentifier>/` altına kopyalayıp bir seed pack kaydı oluştur.
>
> 3. `StickerContentProvider`: `/metadata`, `/metadata/<id>`, `/stickers/<id>`, `/stickers_asset/<id>/<file>` URI'lerini destekle. Kolon isimleri WhatsApp spec'indeki gibi birebir olacak (dokümandaki listeye bak). `whitelisted` her zaman 0. `stickers_asset` için `openAssetFile` ile `ParcelFileDescriptor` döndür. Manifest'te authority `${applicationId}.stickercontentprovider`, exported=true, izin yok.
>
> 4. Manifest'e `<queries>` içinde `com.whatsapp` ve `com.whatsapp.w4b` package tanımlarını ekle.
>
> 5. Compose ekranı: pack listesi, her satırda pack adı, sticker sayısı ve "WhatsApp'a Ekle" butonu. Buton `ENABLE_STICKER_PACK` intent'ini `startActivityForResult` ile açsın; dönen `resultCode` ve `validation_error` extra'sını hem Log'a hem Snackbar'a yazsın.
>
> 6. WhatsApp kurulu değilse butonu disable et ve sebebini göster.

### Başarı kriteri
Butona bas → WhatsApp'ın pack ekleme onay ekranı açılsın → onayla → WhatsApp sticker sekmesinde 3 sticker görünsün.

**Bu olmadan Milestone 2'ye geçilmez.**

### Debug checklist
```bash
# Provider'a gelen her sorguyu izle — readPermission nedeniyle "adb content query"
# artık çalışmıyor (shell com.whatsapp.sticker.READ iznine sahip değil).
adb logcat -c && adb logcat -s StickerProvider
# Butona bas; beklenen sıra: metadata -> stickers -> her sticker icin stickers_asset
```
- Logcat'te `WhatsApp` filtresi — validation hatası genelde açıkça yazılır
- Sticker 512×512 ve <100 KB mı
- Tray 96×96 ve <50 KB mı
- Pack'te en az 3 sticker var mı
- Manifest'te `<queries>` var mı
