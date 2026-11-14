package com.mertjr.brawlbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var durum: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        durum = TextView(this)
        durum.textSize = 16f
        root.addView(durum)

        fun btn(t: String, f: () -> Unit) {
            val b = Button(this)
            b.text = t
            b.setOnClickListener { f() }
            root.addView(b)
        }

        btn("0) Kisitli ayarlara izin ver (uygulama bilgisi)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        btn("1) Erisilebilirlik ayarlarini ac") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("2) Brawl Stars'i ac") {
            val i = packageManager.getLaunchIntentForPackage(Config.PKG)
            if (i != null) startActivity(i)
        }

        val bilgi = TextView(this)
        bilgi.textSize = 14f
        bilgi.text = "\nKurulum:\n" +
            "0) Android 13+ ise: acilan sayfada sag ustteki 3 noktadan 'Kisitli ayarlara izin ver'.\n" +
            "1) Erisilebilirlik > Yuklu uygulamalar > BrawlBot > ac.\n" +
            "2) Oyunu ac, Showdown'u sec, lobide dur.\n" +
            "3) Ekranin sol ustunde kucuk panel cikar:\n" +
            "   KALIB. = renkleri/koordinatlari gosterir (once bunu dene)\n" +
            "   BASLAT = botu calistirir, DURDUR = durdurur.\n" +
            "4) Ayarlar GitHub'daki Config.kt dosyasinda."
        root.addView(bilgi)

        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        durum.text = if (en.contains(packageName)) {
            "Servis ACIK. Oyuna gec, panel sol ustte."
        } else {
            "Servis KAPALI. Asagidaki adimlari uygula."
        }
    }
}
