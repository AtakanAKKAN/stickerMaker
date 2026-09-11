package com.atakan.stickerlab.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Marka paleti.
 *
 * Uygulama koyu odaklı: görsel düzenleme yapan uygulamaların çoğu (Lightroom,
 * Snapseed) koyu zemin kullanıyor çünkü nötr koyu bir zemin sticker'ın gerçek
 * renklerini doğru gösteriyor — açık zemin gözü yanıltıyor.
 *
 * Açık tema yine de tam destekleniyor; sistem ayarı takip ediliyor.
 *
 * Dynamic color (Material You, duvar kâğıdından renk) bilerek kullanılmıyor:
 * uygulamanın Play Store ikonu ve marka kimliği menekşe-sarı; kullanıcının
 * duvar kâğıdına göre yeşile dönmesi o kimliği bozardı.
 */

// --- Marka çekirdeği ---

/** Launcher ikonu ve Play görsellerindeki menekşe. */
val BrandViolet = Color(0xFF5B45E0)

/** Sticker yüzünün sarısı; küçük vurgularda kullanılıyor. */
val BrandYellow = Color(0xFFFFC83D)

// --- Koyu tema ---

val DarkBackground = Color(0xFF0E0E13)
val DarkSurface = Color(0xFF14141B)
val DarkSurfaceContainer = Color(0xFF1B1B24)
val DarkSurfaceContainerHigh = Color(0xFF23232E)
val DarkSurfaceContainerHighest = Color(0xFF2C2C38)
val DarkOnSurface = Color(0xFFE8E6F0)
val DarkOnSurfaceVariant = Color(0xFFA9A6B8)
val DarkOutline = Color(0xFF43414F)

/** Koyu zeminde menekşenin okunabilir olması için açık tonu gerekiyor. */
val DarkPrimary = Color(0xFFB6A4FF)
val DarkOnPrimary = Color(0xFF27145F)
val DarkPrimaryContainer = Color(0xFF3D2A94)
val DarkOnPrimaryContainer = Color(0xFFE7DEFF)

val DarkTertiary = Color(0xFFFFD166)
val DarkOnTertiary = Color(0xFF3D2E00)
val DarkTertiaryContainer = Color(0xFF574200)
val DarkOnTertiaryContainer = Color(0xFFFFE9A8)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// --- Açık tema ---

val LightBackground = Color(0xFFFCFBFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF3F1FA)
val LightSurfaceContainerHigh = Color(0xFFEDEAF6)
val LightSurfaceContainerHighest = Color(0xFFE7E3F2)
val LightOnSurface = Color(0xFF15141B)
val LightOnSurfaceVariant = Color(0xFF48465A)
val LightOutline = Color(0xFF79768C)

val LightPrimary = BrandViolet
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE6DEFF)
val LightOnPrimaryContainer = Color(0xFF1B0B5C)

/** Açık zeminde sarı okunmuyor; metin/ikon için koyu kehribara kayıyor. */
val LightTertiary = Color(0xFF775900)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDF97)
val LightOnTertiaryContainer = Color(0xFF251A00)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// --- Temaya bağlı olmayan sabitler ---

/**
 * Saydamlığın görüldüğü damalı zemin. Koyu temada da açık gri kalıyor:
 * damalı zeminin işi "burada piksel yok" demek, ve kullanıcılar bu deseni
 * açık griyle tanıyor.
 */
val CheckerLight = Color(0xFFE9E9EF)
val CheckerDark = Color(0xFFD2D2DC)
