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
| 3 | Editör araç çubuğu: tek satır büyük ikon butonlar | ✅ |
| 4 | Pack listesi + detay: büyük kartlar, tek birincil aksiyon, marka boş durumu | ✅ |
| 5 | Cila: dokunma geri bildirimi, geçişler | sırada |

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

### Faz 3 — editör araç çubuğu (tamam)

Önceki sorun: araç çubuğu **üç–dört satırdı** (boyut slider'ı + renk paleti +
araç chip'leri + fırça/lasso chip'leri). Ekranın alt üçte biri araçlara
gidiyordu, hâlbuki asıl iş tuvalde. Chip'ler ~32dp idi; önerilen dokunma hedefi
48dp.

Şimdi: tek **56dp** araç satırı (sil / geri getir / kalem / metin), üstünde
seçili aracın seçeneklerini taşıyan tek satır, gerektiğinde araya sıkışan tek
satırlık ipucu. Üst bar tamamen ikon; "Bitti" birincil aksiyon olduğu için dolu
buton olarak kaldı.

Alınan kararlar:

**Araç butonlarında etiket kaldı.** Hedef "ikon butonlar"dı ama Sil ile Geri
getir ikonları tek başına yeterince ayırt edici değil — 56dp kutunun altına
`labelSmall` etiket kondu. Satır yine tek satır.

**Boyut slider'ı sabit 124dp.** Kalem ve metinde slider ile dokuz renkli palet
aynı satırı paylaşıyor. Slider'ı `weight` ile esnetmek paleti iki renge
düşürüyordu; fırça boyutu hassas bir ayar değil, palet ise yer istiyor. Palet
kaydırılabilir.

**Seçenek satırı sabit yükseklikte (48dp).** Araç değişince alt çubuk zıplıyor
ve parmak yanlış butona iniyordu.

**Metin akışı bir dokunuş kısaldı** — ayrıntısı [EKRANLAR.md](EKRANLAR.md) 2.4'te.

**Damalı zemin artık temayı izliyor.** Faz 1'de "desen her temada açık gri
kalsın, kullanıcı onu böyle tanıyor" denmişti; cihazda bakınca tutmadı, koyu
temada tuvalin tamamı parlayan beyaz bir levha oluyordu ve sticker'ın gerçek
renklerini göstermesi gereken nötr zemin gözü yanıltıyordu — yani temanın
kendi gerekçesine aykırı. Photoshop ve Figma da koyu temada deseni koyultuyor;
tanınırlığı sağlayan ton değil, desenin kendisi. Hücre boyutu da ham pikselden
dp'ye geçti (yoğun ekranlarda desen görünmez inceliğe düşüyordu) ve 8dp'ye
indi.

**Seçili durumun tek dili: dolu `primary` zemin.** İlk denemede araç butonu
`primaryContainer`, fırça/lasso düğmesi `primary` kullanıyordu; cihazda üst üste
iki farklı menekşe çıktı ve açık temada `primaryContainer` araç çubuğunun
zemininden ayırt edilemediği için seçili araç kayboluyordu.

**Slider'ın boş kısmı elle nötrlendi.** Material varsayılanı `secondaryContainer`;
tema o yuvayı marka menekşesine bağladığı için koyu temada boş kısım dolu kadar
güçlü görünüyor, çubuk "sonuna kadar açık" izlenimi veriyordu.

**Editör kendi zemin rengini çiziyor.** Tam ekran modal; altındaki `Surface`'e
güvendiği için tasarım turunda üst bar beyaz bir şerit olarak çıkıyordu.
Uygulamada doğru görünüyordu ama ekranın kendi kendine ayakta durması gerekiyor.

Alt çubuk ayrıca `surfaceContainerHigh` + 1dp ayırıcı çizgi aldı: açık temada
çubuk zemini tuvalle neredeyse aynı beyazlıktaydı.

### Faz 4 — pack listesi ve detay (tamam)

**Pack kartında tek birincil aksiyon kaldı.** "Aç" butonu gitti; karta dokunmak
pack'i açıyor, bunu sağdaki chevron söylüyor. Görünen tek buton ekranın asıl
tekrar eden işi: tam genişlikte **WhatsApp'a Ekle**.

**Overflow menüsü eklenmedi** — plan "tek birincil aksiyon + overflow" diyordu
ama içine koyacak bir şey çıkmadı. Yeniden adlandırma ve silme pack detayında
yaşıyor; listeye de konsaydı tam da kaldırdığımız "aynı işe iki yol" sorunu geri
gelirdi.

**Tray ikonu 56dp'den 72dp'ye çıktı**, ince bir çerçeve aldı. WhatsApp'ta pack'i
temsil eden görsel o; listede de pack'in yüzü olmalı, oysa kartın içinde
kayboluyordu.

**Durum notu kendi zeminine oturdu.** "2 sticker daha gerekli" / "WhatsApp kurulu
değil" düz küçük yazıydı ve butonun neden kapalı olduğunu söylemesi gerekirken
görünmüyordu.

**Boş durum marka yüzünü kullanıyor:** menekşe daire içinde launcher ikonunun
sarı sticker yüzü. Ayrı illüstrasyon çizmek yerine uygulamanın kendi markası —
yeni dosya yok.

Detay ekranında: üst bar metin butonları ikonlara döndü, seçim modu üst barı
`primaryContainer` ile renklenerek normal moddan ayrıldı, alt aksiyonlar
editördeki araç butonlarıyla aynı dili konuşuyor (ikon + etiket, "Sil" kırmızı),
"+ Sticker" yuvarlak butondan grid hücresine dönüştü.

İki şey tasarım turunda cihazda görülüp düzeltildi:

- **Sticker küçük resimlerinin zemini sabit açık griydi** (`#EDEDED`). Koyu temada
  sticker'lar koyu kartların üstünde parlayan beyaz karelere dönüyordu; artık
  editörün damalı zemin tonlarını kullanıyor.
- **Tasarım turu düzeneğinin kendisi yalan söylüyordu:** `MainActivity` içeriği bir
  `Surface` ile sarıyor, düzenek sarmıyordu. `Surface` dışında metin rengi
  `onSurface` değil siyaha düşüyor, zemin de boş kalıyor — koyu tema çekimleri
  beyaz zeminli, siyah yazılı çıkıyordu. Düzenek `MainActivity` ile aynı sarmalamayı
  yapıyor artık.

**Planda olup yapılamayan:** grid'de tray ikonunun hangi sticker olduğunu
işaretlemek. Tray her pack'te `tray.png` adıyla ayrı bir kopya olarak tutuluyor,
yani hangi sticker'dan üretildiği veritabanında yazmıyor. Şema değişikliği
gerektirir; Faz 5'e not düşüldü.

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

Faz 3'te editör ekranı da düzeneğe girdi: `editor-erase-dark/light`,
`editor-pen-dark` (boyut + palet aynı satırda), `editor-lasso-dark` (slider
yerine ipucu). Faz 4'te pack kartı (`packs-dark/light` — üç durum bir arada) ve
boş durum (`empty-dark/light`) eklendi.

Pack **detayı** düzeneğe giremiyor: ekran Hilt ViewModel'ine bağlı, `createComposeRule`
içinde kurulamıyor. O ekranı emülatörde uygulamayı açıp `adb shell input tap` ile
gezerek çektik; telefonda bu mümkün değil (MIUI girdi enjeksiyonunu engelliyor),
emülatörde çalışıyor.
