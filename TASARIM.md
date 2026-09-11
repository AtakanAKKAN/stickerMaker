# Tasarım Yenilemesi

Uygulama M1–M7 boyunca işlevsel kuruldu ama tasarımı hiç ele alınmadı: tema yoktu,
`MaterialTheme {}` çıplak çağrılıyordu (Material 3 varsayılan mor paleti), koyu tema
desteği yoktu, her şey metin butonlardan ibaretti.

## Yön

Kullanıcının tarifi: **olabildiğince kolay tıklanabilir butonlar, sade, şık, modern.**

Alınan iki karar:

**Koyu odaklı, sistem ayarını takip eden tema.** Görsel düzenleme yapan
uygulamaların çoğu (Lightroom, Snapseed) koyu zemin kullanıyor çünkü nötr koyu
zemin sticker'ın gerçek renklerini doğru gösteriyor; açık zemin gözü yanıltıyor.
Açık tema yine de tam destekli.

**Marka menekşe-sarı, arayüz geri çekilir.** Menekşe vurgu rengi, sarı yalnızca
küçük aksanlarda. İçerik (sticker'ların kendisi) zaten renkli; arayüz onlarla
yarışmamalı.

---

## Fazlar

| Faz | İş | Durum |
|---|---|---|
| 1 | Tema temeli: renk şeması (açık + koyu), tipografi, köşe yarıçapları | ✅ |
| 2 | İkon seti | ✅ |
| 3 | Editör araç çubuğu: tek satır büyük ikon butonlar | sırada |
| 4 | Pack listesi + detay: büyük kartlar, tek birincil aksiyon, marka boş durumu | |
| 5 | Cila: dokunma geri bildirimi, geçişler | |

### Faz 1 — tema (tamam)

`ui/theme/` altında `Color.kt` ve `Theme.kt`.

- Dynamic color (Material You) **bilerek kullanılmıyor**: marka kimliği menekşe-sarı,
  kullanıcının duvar kâğıdına göre yeşile dönmesi onu bozardı.
- Köşeler Material varsayılanından daha yuvarlak (kart 18dp, buton 12dp) — sticker'lar
  yumuşak formda, arayüz onlara uyuyor.
- `values-night/colors.xml` ile açılış penceresi zemini; öncesinde koyu temada
  uygulama beyaz bir flaşla açılıyordu.

### Faz 2 — ikonlar (tamam)

`ui/theme/Icons.kt`, 20 ikon, elle çizilmiş vektör.

`material-icons-extended` **kullanılmadı**: binlerce ikon getiriyor ve bu projede
R8 küçültme kapalı, yani APK'ya megabaytlarca ölü kod binerdi. Elle set birkaç KB
ve çizgi kalınlığı marka diline uygun (24dp tuval, 2dp çizgi, yuvarlak uçlar).

Bir düzeltme gerekti: ilk çizimde Fırça ile Kalem neredeyse aynıydı. Fırça,
Lasso'nun karşıtı olan "serbest çizim" modu — darbenin kendisi çizilerek ayrıldı.

### Faz 3 — editör araç çubuğu (sırada)

Bugünkü sorun: araç çubuğu **üç satır** (boyut slider'ı + araç chip'leri +
fırça/lasso chip'leri). Ekranın alt üçte biri araçlara gidiyor, hâlbuki asıl iş
tuvalde. Chip'ler ~32dp; önerilen dokunma hedefi 48dp.

Hedef:
- Tek satır, 56dp ikon butonlar: sil / geri getir / kalem / metin
- Seçili aracın seçenekleri (boyut, renk, fırça-lasso) **bağlama göre** tek satırda
- Üst bar metin butonları (Kapat / Geri al / İleri / Bitti) ikonlara dönsün
- Damalı zemin koyu temaya uyarlansın

### Faz 4 — pack listesi ve detay

- Pack satırında "Aç" ve "WhatsApp'a Ekle" sıkışık; satırın kendisi de tıklanabilir,
  yani iki yol aynı şeyi yapıyor. Tek birincil aksiyon + overflow olmalı
- Tray ikonu daha belirgin
- Boş durum düz yazı; marka görseli gelmeli

### Faz 5 — cila

Dokunma geri bildirimi, ekranlar arası geçişler, silme/kaydetme onay akışları.

---

## Tasarım turu nasıl yapılıyor

`DesignPreviewTest` (androidTest) bileşenleri cihazda çizip PNG kaydediyor.
Uygulamayı kurup ekranlarda gezinmeye gerek yok, açık/koyu iki varyant tek
komutla çıkıyor. Elle çizilen vektörlerde bu şart: bozuk bir yol derlemede hata
vermiyor, ancak bakınca görülüyor.

```bash
adb shell am instrument -w -e class com.atakan.stickerlab.ui.DesignPreviewTest \
  com.atakan.stickerlab.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.atakan.stickerlab/files/design
```

Yeni ekranlar Faz 3–4'te bu düzeneğe eklenecek.
