# Play Console — Internal Testing Yayını

Hedef: **internal testing** track'i, davetli arkadaşlara dağıtım.
Production hedefi yok — gerekçesi [STICKERLAB.md](STICKERLAB.md) bölüm 2'de.

---

## 1. İmza anahtarı (bir kereliğine, sende kalır)

Anahtarı **sen** üretiyorsun; parolalar hiçbir dosyaya yazılmadan senden isteniyor:

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v \
  -keystore "$USERPROFILE/keys/stickerlab.jks" \
  -alias stickerlab -keyalg RSA -keysize 2048 -validity 10000
```

Klasör yoksa önce oluştur: `mkdir -p "$USERPROFILE/keys"`

> **Bu dosyayı kaybetme.** Aynı uygulamanın sonraki sürümleri aynı anahtarla
> imzalanmak zorunda. Kaybedersen paketi güncelleyemezsin, yenisini yayınlaman
> gerekir. Yedeğini Play Store'un dışında bir yerde tut.

Sonra `keystore.properties.example` dosyasını `keystore.properties` adıyla
kopyalayıp doldur. Bu dosya `.gitignore`'da, depoya girmez.

## 2. AAB üret

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:bundleRelease
```

Çıktı: `app/build/outputs/bundle/release/app-release.aab`

`keystore.properties` yoksa AAB imzasız üretilir ve Play Console kabul etmez;
imzalı olduğunu doğrulamak için:

```bash
"$LOCALAPPDATA/Android/Sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs \
  app/build/outputs/bundle/release/app-release.aab
```

## 3. Gizlilik politikası URL'si

[docs/index.html](docs/index.html) hazır. Yayınlamak için:

1. `docs/index.html` içindeki `E_POSTA_ADRESIN` yazan yeri kendi adresinle değiştir
2. GitHub → Settings → Pages → Source: `main` dalı, `/docs` klasörü
3. URL şu olur: `https://atakanakkan.github.io/stickerMaker/`

Bu URL'yi Play Console'da "App content → Privacy policy" alanına gireceksin.

## 4. Play Console: App content formları

Herhangi bir track'e yükleyebilmek için bunların tamamlanması zorunlu.

### Data safety

Uygulama hiçbir veri toplamıyor, bu yüzden form kısa:

| Soru | Cevap |
|---|---|
| Uygulaman kullanıcı verisi topluyor mu / paylaşıyor mu? | **Hayır** |
| Veriler aktarım sırasında şifreleniyor mu? | Soru sorulmaz (veri yok) |
| Kullanıcı veri silinmesini isteyebiliyor mu? | Soru sorulmaz (veri yok) |

Fotoğraflar cihazda işleniyor ve dışarı çıkmıyor; Play'in tanımına göre bu
"veri toplama" değil.

### Content rating

Anketi doldur. İçerik kategorisi: **Utility / Productivity**. Şiddet, cinsellik,
kumar, kullanıcı etkileşimi, konum paylaşımı — hepsine hayır. Sonuç muhtemelen
"Everyone / 3+" çıkar.

### Target audience

- Hedef yaş: **18 ve üzeri** (çocuklara yönelik değil)
- Çocuklara çekici gelecek içerik: hayır

### Diğerleri

- **Ads:** Uygulamada reklam yok
- **Government apps:** hayır
- **Financial features:** hayır
- **Health:** hayır

## 5. Store listing (internal testing için minimum)

| Alan | Not |
|---|---|
| Uygulama adı | `StickerLab` — içinde "WhatsApp" **geçmemeli**, WhatsApp kuralı |
| Kısa açıklama | 80 karakter |
| Tam açıklama | Reklamsız kişisel sticker yapıcı |
| Uygulama ikonu | 512×512 PNG |
| Feature graphic | 1024×500 PNG |
| Telefon ekran görüntüsü | En az 2 adet |

Ekran görüntüsü almak için (telefon bağlıyken):

```bash
adb shell screencap -a -p /sdcard/shot.png && adb pull /sdcard/shot_0.png
```

## 6. Internal testing track

1. Play Console → Testing → **Internal testing** → Create new release
2. AAB'yi yükle
3. Release notes yaz
4. Testers → e-posta listesi oluştur, arkadaşlarının Google hesaplarını ekle
5. Yayınla, çıkan opt-in linkini paylaş

**Google incelemesi yok**, dakikalar içinde yayında olur. 12 tester / 14 gün kuralı
internal testing'e ait değil — o kural closed testing içindir ve tek amacı
production erişimi açmaktır.

---

## Teknik durum

| Gereklilik | Durum |
|---|---|
| `targetSdk = 36` (31 Ağustos 2026'dan itibaren zorunlu) | ✅ |
| `applicationId` içinde "whatsapp" geçmiyor | ✅ `com.atakan.stickerlab` |
| Uygulama adında "WhatsApp" geçmiyor | ✅ `StickerLab` |
| AAB üretiliyor | ✅ 8.3 MB |
| İmza yapılandırması | ✅ anahtar üretildi, AAB imzalı doğrulandı (10 Eylül 2026) |

### Bilinçli tercih: `isMinifyEnabled = false`

R8 küçültme kapalı. Açmak APK'yı birkaç MB düşürürdü ama Room, Hilt ve ML Kit'in
yansıma kullandığı yerlerde çalışma zamanı hatası riski var — ve bu hatalar ancak
kullanıcıda ortaya çıkar. Dağıtım birkaç düzine kişiye olduğu için boyut kazancı
bu riski karşılamıyor. İleride açılırsa **release derlemesiyle cihazda test
edilmeli**, sadece derlenmesi yeterli değil.
