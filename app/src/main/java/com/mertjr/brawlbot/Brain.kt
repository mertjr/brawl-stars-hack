package com.mertjr.brawlbot

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Botun beyni: ekrani okur, karar verir, BotService ile dokunur. */
class Brain(private val s: BotService) {

    @Volatile var running = false
    var calibrate = false

    private class Mass(val n: Int, val vx: Float, val vy: Float)

    private var bmp: Bitmap? = null
    private var W = 1
    private var H = 1

    private var yon = Pair(1f, 0f)
    private var yonBitis = 0L
    private var strafe = 1f
    private var strafeBitis = 0L
    private var sonSuper = 0L
    private var takildi = 0
    private var serbestBitis = 0L
    private var oncekiImza: IntArray? = null

    private var macSayisi = 0
    private var kupaKontrol = false
    private var sonDrop = 0L
    private var oynaZamani = 0L
    private var kartIdx = -1
    private var sonDegisimMac = 0
    private var macte = false
    private var macDisi = 0
    private var bilinmeyen = 0
    private var disarida = 0

    private val tiklama = listOf(Config.DEVAM, Config.KAPAT, Config.ORTA)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ---------- yardimcilar ----------
    private fun log(m: String) = s.log(m)
    private fun sl(ms: Long) = Thread.sleep(ms)
    private fun nx(p: P): Float = if (p.x <= 1f) p.x * W else p.x
    private fun ny(p: P): Float = if (p.y <= 1f) p.y * H else p.y
    private fun jit(v: Float, n: Float): Float = v + (Random.nextFloat() * 2f - 1f) * n

    private fun tap(p: P) {
        val x = jit(nx(p), 6f)
        val y = jit(ny(p), 6f)
        s.gesture(listOf(Stroke(x, y, x, y)), (60 + Random.nextInt(60)).toLong(), true)
    }

    private fun pixAt(x: Float, y: Float): Int {
        val b = bmp ?: return 0
        return b.getPixel(x.toInt().coerceIn(0, b.width - 1), y.toInt().coerceIn(0, b.height - 1))
    }

    private fun pix(p: P): Int = pixAt(nx(p), ny(p))

    private fun benzer(c1: Int, c2: Int): Boolean {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return sqrt((dr * dr + dg * dg + db * db).toFloat()) <= Config.ESIK
    }

    private fun refresh(): Boolean {
        val b = s.shot() ?: return false
        val eski = bmp
        bmp = b
        W = b.width
        H = b.height
        eski?.recycle()
        return true
    }

    private fun oran(x1: Float, y1: Float, x2: Float, y2: Float, renk: Int): Float {
        var t = 0
        var e = 0
        for (i in 0 until 8) for (j in 0 until 5) {
            val x = W * (x1 + (x2 - x1) * i / 7f)
            val y = H * (y1 + (y2 - y1) * j / 4f)
            t++
            if (benzer(pixAt(x, y), renk)) e++
        }
        return e.toFloat() / t
    }

    private fun lobiMi(): Boolean = oran(0.80f, 0.86f, 0.96f, 0.95f, Config.SARI) > 0.5f
    private fun macMi(): Boolean = benzer(pix(Config.ATK), Config.ATK_RENK)

    private fun norm(x: Float, y: Float): Pair<Float, Float> {
        var n = hypot(x, y)
        if (n == 0f) n = 1f
        return Pair(x / n, y / n)
    }

    // ---------- mac ici ----------
    private fun yasak(x: Float, y: Float): Boolean {
        if (y < H * 0.12f) return true
        if (x < W * 0.25f && y > H * 0.55f) return true
        if (x > W * 0.68f && y > H * 0.5f) return true
        if (abs(x - W / 2f) < W * 0.07f && abs(y - H / 2f) < H * 0.14f) return true
        return false
    }

    private fun tara(renk: Int): Mass? {
        var vx = 0f
        var vy = 0f
        var n = 0
        val cx = W / 2f
        val cy = H / 2f
        for (i in 0 until 40) for (j in 0 until 20) {
            val gx = W * i / 40f
            val gy = H * j / 20f
            if (yasak(gx, gy)) continue
            if (benzer(pixAt(gx, gy), renk)) {
                vx += gx - cx
                vy += gy - cy
                n++
            }
        }
        return if (n == 0) null else Mass(n, vx / n, vy / n)
    }

    private fun imza(): IntArray {
        val a = IntArray(15 * 8)
        var k = 0
        for (i in 1 until 16) for (j in 1 until 9) {
            a[k++] = Color.green(pixAt(W * i / 16f, H * j / 9f))
        }
        return a
    }

    private fun fark(a: IntArray, b: IntArray): Float {
        var t = 0
        for (i in a.indices) t += abs(a[i] - b[i])
        return t.toFloat() / a.size
    }

    /** hareket + nisan + super ayni anda (coklu dokunus) */
    private fun eylem(d: Pair<Float, Float>?, ms: Long, nisan: Pair<Float, Float>?, sup: Boolean, bekle: Boolean) {
        val r = H * 0.12f
        val st = ArrayList<Stroke>()
        val jx = nx(Config.JOY)
        val jy = ny(Config.JOY)
        val ax = nx(Config.ATK)
        val ay = ny(Config.ATK)
        if (d != null) st.add(Stroke(jit(jx, 4f), jit(jy, 4f), jx + d.first * r, jy + d.second * r))
        if (nisan != null) st.add(Stroke(ax, ay, ax + nisan.first * r, ay + nisan.second * r))
        if (sup && nisan != null) {
            val sx = nx(Config.SUPER)
            val sy = ny(Config.SUPER)
            st.add(Stroke(sx, sy, sx + nisan.first * r, sy + nisan.second * r))
        }
        if (st.isEmpty()) {
            sl(ms)
            return
        }
        s.gesture(st, ms, bekle)
    }

    private fun macAdimi() {
        val gaz = tara(Config.GAZ)
        val rk = tara(Config.RAKIP)
        val superHazir = benzer(pix(Config.SUPER), Config.SARI)
        val im = imza()
        val onc = oncekiImza
        if (onc != null && fark(im, onc) < 2.5f) takildi++ else takildi = 0
        oncekiImza = im
        val simdi = System.currentTimeMillis()

        // 1. Gazdan kac
        if (gaz != null && gaz.n >= 6) {
            eylem(norm(-gaz.vx, -gaz.vy), 450, null, false, false)
            return
        }

        // 2. Takildiysan rastgele yone cik
        if (takildi >= 6) {
            yon = norm(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f)
            serbestBitis = simdi + 1200
            takildi = 0
        }
        if (simdi < serbestBitis) {
            eylem(yon, 450, null, false, false)
            return
        }

        // 3. Rakip var
        if (rk != null && rk.n >= 2) {
            val dx = rk.vx
            val dy = rk.vy + H * 0.05f
            val dist = hypot(dx, dy)
            val d = norm(dx, dy)
            val mv: Pair<Float, Float> = when {
                rk.n > 14 -> norm(-d.first, -d.second)       // cok yakin / kalabalik: geri cekil
                dist > W * 0.30f -> d                        // uzak: yaklas
                else -> {
                    if (simdi > strafeBitis) {
                        strafe = -strafe
                        strafeBitis = simdi + 800 + Random.nextInt(900)
                    }
                    val t = if (dist > W * 0.20f) 0.4f else -0.4f
                    norm(-d.second * strafe + d.first * t, d.first * strafe + d.second * t)
                }
            }
            val ates = dist < W * 0.38f
            val sup = ates && superHazir && simdi - sonSuper > 3000
            if (sup) sonSuper = simdi
            eylem(mv, 450, if (ates) d else null, sup, false)
            return
        }

        // 4. Kimse yok: gez, ara sira kutulara vur
        if (simdi > yonBitis) {
            val a = Random.nextDouble() * 2.0 * PI
            yon = Pair(cos(a).toFloat(), sin(a).toFloat())
            yonBitis = simdi + 1500 + Random.nextInt(2000)
        }
        val nis = if (Random.nextFloat() < 0.3f) yon else null
        eylem(yon, 450, nis, false, false)
    }

    // ---------- lobi ----------
    private fun ocr(b: Bitmap): String? {
        return try {
            val img = InputImage.fromBitmap(b, 0)
            Tasks.await(recognizer.process(img), 5, TimeUnit.SECONDS).text
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun kupaOku(): Int? {
        if (!refresh()) return null
        val b = bmp ?: return null
        val r = Config.KUPA_BOLGE
        val x = (b.width * r[0]).toInt()
        val y = (b.height * r[1]).toInt()
        val w = (b.width * r[2]).toInt().coerceAtMost(b.width - x)
        val h = (b.height * r[3]).toInt().coerceAtMost(b.height - y)
        if (w <= 0 || h <= 0) return null
        val kes = Bitmap.createBitmap(b, x, y, w, h)
        val buyuk = Bitmap.createScaledBitmap(kes, w * 3, h * 3, true)
        val metin = ocr(buyuk) ?: return null
        val m = Regex("\\d{2,4}").find(metin.replace('O', '0').replace('o', '0')) ?: return null
        return m.value.toInt()
    }

    private fun lobiyeDon(): Boolean {
        for (i in 0 until 4) {
            if (refresh() && lobiMi()) return true
            s.back()
            sl(1200)
        }
        return false
    }

    private fun karakterDegistir() {
        log("Karakter degisiyor")
        for (d in Config.KARTLAR.indices) {
            tap(Config.BRAWLERS); sl(2000)
            kartIdx = (kartIdx + 1) % Config.KARTLAR.size
            tap(Config.KARTLAR[kartIdx]); sl(1500)
            tap(Config.SEC); sl(2000)
            lobiyeDon()
            val k = kupaOku()
            log("Yeni karakter kupa: $k")
            if (k == null || k < Config.KUPA_LIMIT) {
                sonDegisimMac = macSayisi
                return
            }
        }
        log("Hepsi limitte, durdum")
        running = false
    }

    private fun degisimGerek(): Boolean {
        val k = kupaOku()
        if (k != null) {
            log("Kupa: $k")
            return k >= Config.KUPA_LIMIT
        }
        return macSayisi - sonDegisimMac >= Config.MAC_LIMIT
    }

    private fun dropAc() {
        log("Starr Drop aciliyor")
        tap(Config.STARR); sl(2000)
        for (i in 0 until 10) {
            tap(Config.ORTA); sl(900)
        }
        tap(Config.DEVAM); sl(1500)
        lobiyeDon()
    }

    private fun lobiIsle() {
        val simdi = System.currentTimeMillis()
        if (simdi - oynaZamani < 8000) {
            sl(800)
            return
        }
        if (kupaKontrol) {
            kupaKontrol = false
            if (degisimGerek()) {
                karakterDegistir()
                return
            }
        }
        if (simdi - sonDrop > Config.DROP_ARALIK_MS) {
            sonDrop = simdi
            if (refresh() && benzer(pix(Config.STARR), Config.STARR_RENK)) {
                dropAc()
                return
            }
        }
        tap(Config.OYNA)
        oynaZamani = System.currentTimeMillis()
        log("Maca giriliyor")
        sl(1500)
    }

    private fun macBitti() {
        macte = false
        macSayisi++
        kupaKontrol = true
        log("Mac bitti. Toplam: $macSayisi")
    }

    // ---------- kalibrasyon ----------
    private fun kalibrasyon() {
        val l = ArrayList<String>()
        l.add("lobi=${lobiMi()} mac=${macMi()}  ${W}x$H")
        l.add("gaz=${tara(Config.GAZ)?.n ?: 0} rakip=${tara(Config.RAKIP)?.n ?: 0}")
        for ((ad, p) in Config.NOKTALAR) {
            l.add(ad + "=" + String.format("#%06X", pix(p) and 0xFFFFFF))
        }
        s.setLines(l)
        sl(2000)
    }

    // ---------- ana dongu ----------
    private fun step() {
        if (!refresh()) {
            sl(400)
            return
        }
        if (calibrate) {
            kalibrasyon()
            return
        }
        if (s.currentPkg != Config.PKG) {
            disarida++
            if (disarida >= 3) {
                log("Oyun aciliyor")
                s.openGame()
                sl(15000)
                disarida = 0
                macte = false
            }
            sl(1000)
            return
        }
        disarida = 0

        val lobi = lobiMi()
        if (!lobi && macMi()) {
            macte = true
            macDisi = 0
            bilinmeyen = 0
            macAdimi()
            return
        }
        if (lobi) {
            bilinmeyen = 0
            if (macte) macBitti()
            lobiIsle()
        } else {
            if (macte) {
                macDisi++
                if (macDisi >= 4) macBitti()
            }
            bilinmeyen++
            if (bilinmeyen > 6) tap(tiklama[bilinmeyen % tiklama.size])
            sl(1000)
        }
    }

    fun loop() {
        log(if (calibrate) "Kalibrasyon modu" else "Bot basladi")
        try {
            while (running) {
                try {
                    step()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    log("Hata: ${e.message}")
                    try {
                        Thread.sleep(500)
                    } catch (x: InterruptedException) {
                        break
                    }
                }
            }
        } finally {
            s.onBrainStopped()
        }
    }
}
