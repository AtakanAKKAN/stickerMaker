package com.atakan.stickerlab.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Uygulamanın ikon seti.
 *
 * `material-icons-extended` yerine elle çiziliyor: o kütüphane binlerce ikon
 * getiriyor ve bu projede R8 küçültme kapalı (Room/Hilt/ML Kit yansıma riski
 * yüzünden, bkz. PLAY_YAYIN.md), yani APK'ya megabaytlarca ölü kod binerdi.
 * Buradaki set birkaç KB ve çizgi kalınlığı/uçları marka diline uygun.
 *
 * Hepsi 24dp tuval, 2dp çizgi, yuvarlak uçlar.
 */
object StickerIcons {

    private const val SIZE = 24f
    private const val STROKE = 2f

    private fun icon(
        name: String,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    ).apply(block).build()

    /** Çizgi (stroke) tabanlı yol. */
    private fun ImageVector.Builder.stroke(
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathData,
    )

    /** Dolu yol. */
    private fun ImageVector.Builder.fill(
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) = path(fill = SolidColor(Color.Black), pathBuilder = pathData)

    // --- gezinme ---

    val Back: ImageVector = icon("Back") {
        stroke {
            moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
        }
    }

    val Close: ImageVector = icon("Close") {
        stroke {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Check: ImageVector = icon("Check") {
        stroke {
            moveTo(5f, 13f); lineTo(10f, 18f); lineTo(19f, 6f)
        }
    }

    val More: ImageVector = icon("More") {
        fill {
            moveTo(12f, 5f); arcToRelative(1.8f, 1.8f, 0f, true, true, 0.1f, 0f); close()
            moveTo(12f, 10.2f); arcToRelative(1.8f, 1.8f, 0f, true, true, 0.1f, 0f); close()
            moveTo(12f, 15.4f); arcToRelative(1.8f, 1.8f, 0f, true, true, 0.1f, 0f); close()
        }
    }

    val Add: ImageVector = icon("Add") {
        stroke {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    // --- geçmiş ---

    val Undo: ImageVector = icon("Undo") {
        stroke {
            moveTo(8f, 8f); lineTo(4f, 12f); lineTo(8f, 16f)
            moveTo(4f, 12f); lineTo(14f, 12f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, 0f, 7f)
        }
    }

    val Redo: ImageVector = icon("Redo") {
        stroke {
            moveTo(16f, 8f); lineTo(20f, 12f); lineTo(16f, 16f)
            moveTo(20f, 12f); lineTo(10f, 12f)
            arcToRelative(5.5f, 5.5f, 0f, false, false, 0f, 7f)
        }
    }

    // --- editör araçları ---

    /** Silgi: eğik dikdörtgen + zemin çizgisi. */
    val Erase: ImageVector = icon("Erase") {
        stroke {
            moveTo(8.5f, 17.5f); lineTo(4.5f, 13.5f)
            lineTo(13f, 5f); lineTo(19f, 11f); lineTo(14.5f, 15.5f)
            moveTo(4f, 20f); lineTo(20f, 20f)
        }
    }

    /** Geri getir: silinenin dönüşünü anlatan ok + kesikli alan. */
    val Restore: ImageVector = icon("Restore") {
        stroke {
            moveTo(12f, 4f)
            arcToRelative(8f, 8f, 0f, true, true, -7.6f, 5.5f)
            moveTo(4.2f, 4.5f); lineTo(4.4f, 9.7f); lineTo(9.6f, 9.4f)
        }
    }

    /**
     * Fırça modu: serbest el darbesi.
     *
     * Önce kalem/fırça sapı çizilmişti ama [Pen] ile neredeyse aynı görünüyordu;
     * oysa bunlar farklı eksenler — bu, Lasso'nun karşıtı olan "serbest çizim"
     * modu. Darbenin kendisi çizilince ayrım netleşiyor.
     */
    val Brush: ImageVector = icon("Brush") {
        stroke {
            moveTo(4f, 15.5f)
            curveTo(7f, 8.5f, 10.5f, 16.5f, 13.5f, 11f)
            curveTo(15f, 8.2f, 16.5f, 8.5f, 17.5f, 10f)
        }
        fill {
            moveTo(18.5f, 13.2f)
            arcToRelative(2.6f, 2.6f, 0f, true, true, 0.1f, 0f)
            close()
        }
    }

    /** Lasso: kesikli kement halkası. */
    val Lasso: ImageVector = icon("Lasso") {
        stroke {
            moveTo(12f, 4.5f); curveTo(17f, 4.5f, 20f, 7.2f, 20f, 10.5f)
            curveTo(20f, 13.8f, 17f, 16.5f, 12f, 16.5f)
            curveTo(7f, 16.5f, 4f, 13.8f, 4f, 10.5f)
            curveTo(4f, 7.2f, 7f, 4.5f, 12f, 4.5f)
            close()
            moveTo(9f, 16.2f); lineTo(9f, 19.5f)
            moveTo(9f, 19.5f)
            arcToRelative(1.6f, 1.6f, 0f, true, false, 0.1f, 0f)
            close()
        }
    }

    /** Renkli kalem. */
    val Pen: ImageVector = icon("Pen") {
        stroke {
            moveTo(4f, 20f); lineTo(4.8f, 16f); lineTo(16.2f, 4.6f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, 3.2f, 3.2f)
            lineTo(8f, 19.2f); close()
            moveTo(14.5f, 6.3f); lineTo(17.7f, 9.5f)
        }
    }

    /** Metin: klasik "T". */
    val TextTool: ImageVector = icon("TextTool") {
        stroke {
            moveTo(5f, 6f); lineTo(19f, 6f)
            moveTo(12f, 6f); lineTo(12f, 19f)
            moveTo(9f, 19f); lineTo(15f, 19f)
        }
    }

    // --- pack işlemleri ---

    /** Taşı: iki kutu arasında ok. */
    val Move: ImageVector = icon("Move") {
        stroke {
            moveTo(4f, 7f); lineTo(14f, 7f)
            moveTo(10.5f, 3.5f); lineTo(14f, 7f); lineTo(10.5f, 10.5f)
            moveTo(20f, 17f); lineTo(10f, 17f)
            moveTo(13.5f, 13.5f); lineTo(10f, 17f); lineTo(13.5f, 20.5f)
        }
    }

    /** Kopyala: üst üste iki kâğıt. */
    val Copy: ImageVector = icon("Copy") {
        stroke {
            moveTo(9f, 9f)
            moveTo(10.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 15f); lineTo(10.5f, 15f); close()
            moveTo(15f, 19.5f); lineTo(4.5f, 19.5f); lineTo(4.5f, 9f); lineTo(8f, 9f)
        }
    }

    /** Sil: çöp kutusu. */
    val Delete: ImageVector = icon("Delete") {
        stroke {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.4f, 19.5f); lineTo(16.6f, 19.5f); lineTo(17.5f, 6.5f)
            moveTo(10.5f, 10f); lineTo(10.9f, 16f)
            moveTo(13.5f, 10f); lineTo(13.1f, 16f)
        }
    }

    /** Yeniden adlandır: kalem + alt çizgi. */
    val Rename: ImageVector = icon("Rename") {
        stroke {
            moveTo(4f, 16.5f); lineTo(4.6f, 13.4f); lineTo(14.4f, 3.6f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 2.7f, 2.7f)
            lineTo(7.3f, 16.1f); close()
            moveTo(4f, 20.5f); lineTo(20f, 20.5f)
        }
    }

    /** Arka plan silme: yarısı kesilmiş kare. */
    val RemoveBackground: ImageVector = icon("RemoveBackground") {
        stroke {
            moveTo(12f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 12f)
            moveTo(12f, 19.5f); lineTo(4.5f, 19.5f); lineTo(4.5f, 12f)
            moveTo(4.5f, 8f); lineTo(4.5f, 4.5f); lineTo(8f, 4.5f)
            moveTo(16f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 16f)
            moveTo(12f, 8.5f)
            arcToRelative(3.5f, 3.5f, 0f, true, false, 0.1f, 0f)
            close()
        }
    }

    /** WhatsApp'a gönder: dışa çıkan ok. */
    val Share: ImageVector = icon("Share") {
        stroke {
            moveTo(12f, 15f); lineTo(12f, 4f)
            moveTo(8.5f, 7.5f); lineTo(12f, 4f); lineTo(15.5f, 7.5f)
            moveTo(6f, 12f); lineTo(4.5f, 12f); lineTo(4.5f, 20f); lineTo(19.5f, 20f); lineTo(19.5f, 12f); lineTo(18f, 12f)
        }
    }

    /** Fotoğraf seç. */
    val Photo: ImageVector = icon("Photo") {
        stroke {
            moveTo(4.5f, 5.5f); lineTo(19.5f, 5.5f); lineTo(19.5f, 18.5f); lineTo(4.5f, 18.5f); close()
            moveTo(4.5f, 15f); lineTo(9f, 10.5f); lineTo(14f, 15.5f)
            moveTo(13f, 14.5f); lineTo(15.5f, 12f); lineTo(19.5f, 16f)
            moveTo(15f, 9f)
            arcToRelative(1.3f, 1.3f, 0f, true, false, 0.1f, 0f)
            close()
        }
    }
}
