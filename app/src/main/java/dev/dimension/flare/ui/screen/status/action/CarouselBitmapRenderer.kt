package dev.dimension.flare.ui.screen.status.action

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import dev.dimension.flare.data.ai.scribe.CarouselDeck
import dev.dimension.flare.data.ai.scribe.CarouselSlide
import dev.dimension.flare.data.ai.scribe.CarouselTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * High-speed, zero-dependency Android Canvas renderer for 1080x1350 (4:5 portrait)
 * editorial Carousel Posters. Produces crisp PNG slides for standalone posts,
 * native Android Share Sheets, and Gallery exports.
 */
internal object CarouselBitmapRenderer {

    private const val SLIDE_WIDTH = 1080
    private const val SLIDE_HEIGHT = 1350 // 4:5 Portrait standard

    private data class Palette(
        val bgColor: Int,
        val cardColor: Int,
        val textColor: Int,
        val mutedColor: Int,
        val accentColor: Int,
        val borderColor: Int,
    )

    private fun paletteFor(template: CarouselTemplate): Palette =
        when (template) {
            CarouselTemplate.ObsidianGold -> Palette(
                bgColor = Color.rgb(14, 14, 14),
                cardColor = Color.rgb(22, 22, 24),
                textColor = Color.rgb(244, 241, 234),
                mutedColor = Color.rgb(156, 152, 144),
                accentColor = Color.rgb(232, 185, 72),
                borderColor = Color.argb(65, 232, 185, 72),
            )

            CarouselTemplate.WarmLinen -> Palette(
                bgColor = Color.rgb(244, 241, 234),
                cardColor = Color.rgb(252, 250, 245),
                textColor = Color.rgb(22, 22, 22),
                mutedColor = Color.rgb(90, 88, 79),
                accentColor = Color.rgb(184, 52, 27),
                borderColor = Color.argb(55, 184, 52, 27),
            )

            CarouselTemplate.CyberCyan -> Palette(
                bgColor = Color.rgb(16, 19, 24),
                cardColor = Color.rgb(24, 28, 36),
                textColor = Color.rgb(241, 244, 248),
                mutedColor = Color.rgb(140, 154, 172),
                accentColor = Color.rgb(0, 240, 255),
                borderColor = Color.argb(75, 0, 240, 255),
            )
        }

    /**
     * Renders all slides in [deck] to 1080x1350 PNG files inside `cacheDir/carousel`.
     */
    suspend fun renderDeckToCacheFiles(
        context: Context,
        deck: CarouselDeck,
    ): List<File> = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "carousel").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        deck.slides.map { slide ->
            val bitmap = renderSlideBitmap(slide, deck.template)
            val outFile = File(dir, "prism_carousel_${timestamp}_slide_0${slide.index}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            outFile
        }
    }

    /**
     * Renders all slides and returns `file://` URIs suitable for `ComposeScreen(initialMedias = ...)`.
     */
    suspend fun renderDeckToFileUris(
        context: Context,
        deck: CarouselDeck,
    ): List<Uri> {
        val files = renderDeckToCacheFiles(context, deck)
        return files.map { Uri.fromFile(it) }
    }

    /**
     * Renders all slides to `MediaStore.Images.Media` (`Pictures/Prism Carousels`) so they
     * appear in the user's system Gallery / Photos app.
     */
    suspend fun saveDeckToGallery(
        context: Context,
        deck: CarouselDeck,
    ): Int = withContext(Dispatchers.IO) {
        val files = renderDeckToCacheFiles(context, deck)
        var savedCount = 0
        val resolver = context.contentResolver
        val timestamp = System.currentTimeMillis()

        files.forEachIndexed { idx, file ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Prism_Slide_${timestamp}_0${idx + 1}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Prism Carousels")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                savedCount++
            }
        }
        savedCount
    }

    /**
     * Launches Android's system Share Sheet (`ACTION_SEND_MULTIPLE`) with both the
     * rewritten standalone post text and all rendered 1080x1350 Carousel PNGs attached.
     */
    suspend fun shareDeckExternally(
        context: Context,
        postText: String,
        deck: CarouselDeck,
        includeImages: Boolean = true,
    ) {
        if (!includeImages) {
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, postText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Standalone Post"))
            }
            return
        }

        val files = renderDeckToCacheFiles(context, deck)
        val authority = "${context.packageName}.provider"
        val contentUris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, authority, it) })

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, postText)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (contentUris.isNotEmpty()) {
                    val clip = ClipData.newUri(context.contentResolver, "Carousel Slide", contentUris.first())
                    contentUris.drop(1).forEach { uri ->
                        clip.addItem(ClipData.Item(uri))
                    }
                    clipData = clip
                }
            }
            context.startActivity(Intent.createChooser(intent, "Share Rewritten Post & Carousel"))
        }
    }

    private fun renderSlideBitmap(
        slide: CarouselSlide,
        template: CarouselTemplate,
    ): Bitmap {
        val palette = paletteFor(template)
        val bitmap = Bitmap.createBitmap(SLIDE_WIDTH, SLIDE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Fill
        canvas.drawColor(palette.bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2. Editorial Inner Card & Frame
        val margin = 56f
        val cardRect = RectF(margin, margin, SLIDE_WIDTH - margin, SLIDE_HEIGHT - margin)
        paint.style = Paint.Style.FILL
        paint.color = palette.cardColor
        canvas.drawRoundRect(cardRect, 36f, 36f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = palette.borderColor
        canvas.drawRoundRect(cardRect, 36f, 36f, paint)

        // 3. Top Accent Bar inside Card
        paint.style = Paint.Style.FILL
        paint.color = palette.accentColor
        val topAccentRect = RectF(margin + 64f, margin, SLIDE_WIDTH - margin - 64f, margin + 8f)
        canvas.drawRoundRect(topAccentRect, 4f, 4f, paint)

        val contentLeft = margin + 68f
        val contentRight = SLIDE_WIDTH - margin - 68f
        val contentWidth = (contentRight - contentLeft).toInt()

        // 4. Top Header Row: Kicker Pill & Slide Index Badge
        val headerY = margin + 92f
        val kickerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentColor
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(slide.kicker.uppercase(), contentLeft, headerY, kickerPaint)

        val indexText = "0${slide.index} / 0${slide.totalSlides}"
        val indexPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.mutedColor
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(indexText, contentRight, headerY, indexPaint)

        // Subtle header divider
        paint.color = palette.borderColor
        paint.strokeWidth = 2f
        canvas.drawLine(contentLeft, headerY + 36f, contentRight, headerY + 36f, paint)

        // 5. Editorial Headline
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textColor
            textSize = if (slide.isCover) 64f else 56f
            typeface = Typeface.create(
                if (template == CarouselTemplate.WarmLinen) Typeface.SERIF else Typeface.SANS_SERIF,
                Typeface.BOLD,
            )
        }
        val headlineLayout = StaticLayout.Builder
            .obtain(slide.headline, 0, slide.headline.length, headlinePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.08f)
            .setIncludePad(false)
            .build()

        val headlineTop = headerY + 110f
        canvas.save()
        canvas.translate(contentLeft, headlineTop)
        headlineLayout.draw(canvas)
        canvas.restore()

        // 6. Accent Rule under Headline
        val ruleTop = headlineTop + headlineLayout.height + 44f
        paint.style = Paint.Style.FILL
        paint.color = palette.accentColor
        canvas.drawRoundRect(
            RectF(contentLeft, ruleTop, contentLeft + 110f, ruleTop + 10f),
            5f,
            5f,
            paint,
        )

        // 7. Main Narrative Body
        val bodyTop = ruleTop + 58f
        val bodyText = slide.body
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textColor
            alpha = 235
            textSize = when {
                bodyText.length > 260 -> 38f
                bodyText.length > 160 -> 42f
                else -> 46f
            }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val bodyLayout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.24f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(contentLeft, bodyTop)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 8. Bottom Editorial Footer
        val footerY = SLIDE_HEIGHT - margin - 64f
        paint.color = palette.borderColor
        paint.strokeWidth = 2f
        canvas.drawLine(contentLeft, footerY - 48f, contentRight, footerY - 48f, paint)

        // Progress dots
        val dotRadius = 7f
        val dotSpacing = 26f
        for (i in 1..slide.totalSlides) {
            val cx = contentLeft + (i - 1) * dotSpacing + dotRadius
            val cy = footerY - 10f
            paint.style = Paint.Style.FILL
            paint.color = if (i == slide.index) palette.accentColor else palette.mutedColor
            paint.alpha = if (i == slide.index) 255 else 90
            canvas.drawCircle(cx, cy, if (i == slide.index) dotRadius + 2f else dotRadius, paint)
        }

        val ctaText = slide.footerText ?: if (slide.isFinal) "SAVE & SHARE" else "SWIPE →"
        val ctaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentColor
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.06f
        }
        canvas.drawText(ctaText, contentRight, footerY, ctaPaint)

        return bitmap
    }
}
