package com.mertjr.brawlbot

import android.graphics.Color

/** x, y: 1'den kucukse ekran orani (0.5 = ortasi), buyukse piksel */
data class P(val x: Float, val y: Float)

class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * AYARLAR. Kalibrasyon yaptiktan sonra buradaki degerleri duzenle.
 * (GitHub'da dosyayi ac, kalem simgesi -> duzenle -> Commit. Yeni APK otomatik derlenir.)
 */
object Config {
    const val PKG = "com.supercell.brawlstars"
    const val KUPA_LIMIT = 1000                 // karakter bu kupaya gelince degisir
    const val MAC_LIMIT = 10                    // OCR kupayi okuyamazsa kac macta bir degissin
    const val DROP_ARALIK_MS = 5 * 60 * 1000L   // Starr Drop kontrol araligi
    const val ESIK = 70f                        // renk toleransi

    // Butonlar (oyun yataydayken)
    val JOY = P(0.12f, 0.75f)       // sol alt joystick
    val ATK = P(0.88f, 0.78f)       // sag alt saldiri
    val SUPER = P(0.80f, 0.62f)     // super butonu
    val OYNA = P(0.88f, 0.90f)      // lobideki sari OYNA
    val DEVAM = P(0.50f, 0.92f)     // sonuc ekrani devam
    val ORTA = P(0.50f, 0.50f)
    val KAPAT = P(0.95f, 0.07f)     // pop-up kapat
    val STARR = P(0.06f, 0.55f)     // lobideki Starr Drop simgesi
    val BRAWLERS = P(0.08f, 0.88f)  // karakterler menusu
    val SEC = P(0.17f, 0.88f)       // karakter sec butonu

    // Karakterin kupa yazisi: x, y, genislik, yukseklik (oran)
    val KUPA_BOLGE = floatArrayOf(0.02f, 0.80f, 0.14f, 0.08f)

    // Karakter listesi ilk sayfa (kartlara sirayla basilir)
    const val GRID_SUTUN = 5
    const val GRID_SATIR = 3
    const val GRID_X0 = 0.38f
    const val GRID_Y0 = 0.28f
    const val GRID_DX = 0.12f
    const val GRID_DY = 0.22f

    // Renkler
    val GAZ: Int = Color.parseColor("#3FD14A")
    val RAKIP: Int = Color.parseColor("#E8293A")
    val SARI: Int = Color.parseColor("#FFC800")
    val STARR_RENK: Int = Color.parseColor("#FFD84A")
    val ATK_RENK: Int = Color.parseColor("#E03A2E")

    val KARTLAR: List<P> = run {
        val l = ArrayList<P>()
        for (r in 0 until GRID_SATIR) for (c in 0 until GRID_SUTUN) {
            l.add(P(GRID_X0 + c * GRID_DX, GRID_Y0 + r * GRID_DY))
        }
        l
    }

    val NOKTALAR: Map<String, P> = linkedMapOf(
        "JOY" to JOY, "ATK" to ATK, "SUPER" to SUPER, "OYNA" to OYNA,
        "DEVAM" to DEVAM, "STARR" to STARR, "BRAWLERS" to BRAWLERS, "SEC" to SEC
    )
}
