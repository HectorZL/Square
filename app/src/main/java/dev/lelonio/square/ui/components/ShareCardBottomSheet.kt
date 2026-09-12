package dev.lelonio.square.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ChatCircle
import com.adamglin.phosphoricons.regular.DotsThree
import com.adamglin.phosphoricons.regular.LinkSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.X
import dev.lelonio.square.R
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.library.openLink
import dev.lelonio.square.ui.theme.rememberArtworkColor
import dev.lelonio.square.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Representation of one of the 3 Spotify-style card color tonalities.
 */
data class CardTonality(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val isGradient: Boolean = false,
    val isDark: Boolean = false,
    val innerBgColor: Color,
    val innerBorderColor: Color,
)

@Composable
fun ShareCardBottomSheet(
    track: CatalogTrack?,
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
) {
    if (track == null) return

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val primaryArtworkColor by rememberArtworkColor(track.artworkUrl)
    val paletteColors by rememberArtworkPalette(track.artworkUrl)

    var cycleOffset by remember { mutableIntStateOf(0) }

    val tonalities = remember(primaryArtworkColor, paletteColors, cycleOffset) {
        buildTonalities(primaryArtworkColor, paletteColors, cycleOffset)
    }

    var selectedTonality by remember(tonalities) {
        mutableStateOf(tonalities.first())
    }

    // Keep selected tonality aligned when palette updates or cycles
    LaunchedEffect(tonalities) {
        selectedTonality = tonalities.find { it.id == selectedTonality.id } ?: tonalities.first()
    }

    var isExporting by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF141416))
                    .clickable(enabled = false) {}
                    .padding(top = 12.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                )

                Spacer(Modifier.height(10.dp))

                // Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.share_card),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.X,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // The Card Preview (Authentic Spotify Share Card: Outer colored canvas + inner floating dark card)
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(9f / 15f)
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            when {
                                selectedTonality.isDark -> Brush.verticalGradient(
                                    listOf(Color(0xFF1C1C22), Color(0xFF0F0F12)),
                                )
                                selectedTonality.isGradient -> Brush.linearGradient(
                                    listOf(selectedTonality.primaryColor, selectedTonality.secondaryColor),
                                )
                                else -> Brush.verticalGradient(
                                    listOf(
                                        selectedTonality.primaryColor,
                                        selectedTonality.primaryColor.copy(alpha = 0.92f),
                                    ),
                                )
                            },
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Inner floating music card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(selectedTonality.innerBgColor)
                            .border(1.dp, selectedTonality.innerBorderColor, RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        // Artwork inside card (square, crisp rounded corners)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            Artwork(
                                url = track.artworkUrl,
                                title = track.name,
                                modifier = Modifier.fillMaxSize(),
                                corner = 12.dp,
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(16.dp))

                        // Official Spotify Branding (Wave icon + wordmark)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_spotify),
                                contentDescription = "Spotify",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Spotify",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.3.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 3 Color Tonalities Row ("con 3 tonalidades")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tonalities.forEach { tonality ->
                        val isSelected = tonality.id == selectedTonality.id
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (tonality.isGradient) {
                                        Brush.linearGradient(listOf(tonality.primaryColor, tonality.secondaryColor))
                                    } else {
                                        SolidColor(tonality.primaryColor)
                                    },
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.28f),
                                    shape = CircleShape,
                                )
                                .clickable { selectedTonality = tonality },
                        )
                    }

                    // 4th edit/palette cycle button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable {
                                cycleOffset++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.PencilSimple,
                            contentDescription = "Cambiar tonos",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Well-Organized Share Actions Row (Copiar enlace, WhatsApp, SMS, Estado, Más)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. Copy Link
                    ShareActionButton(
                        iconVector = PhosphorIcons.Regular.LinkSimple,
                        label = stringResource(R.string.copy_link),
                        backgroundColor = Color(0xFF222226),
                    ) {
                        clipboard.setText(AnnotatedString(track.openLink()))
                        Toast.makeText(context, context.getString(R.string.copy_link), Toast.LENGTH_SHORT).show()
                    }

                    // 2. WhatsApp
                    ShareActionButton(
                        iconPainter = painterResource(R.drawable.ic_whatsapp),
                        label = stringResource(R.string.share_whatsapp),
                        backgroundColor = Color(0xFF25D366),
                    ) {
                        if (isExporting) return@ShareActionButton
                        isExporting = true
                        scope.launch {
                            val uri = exportShareCard(context, track, selectedTonality)
                            isExporting = false
                            if (uri != null) {
                                val waIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${track.name} • ${track.artist}\n${track.openLink()}",
                                    )
                                    setPackage("com.whatsapp")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(waIntent)
                                }.onFailure {
                                    val chooser = Intent.createChooser(
                                        waIntent.apply { `package` = null },
                                        null,
                                    )
                                    context.startActivity(chooser)
                                }
                            }
                        }
                    }

                    // 3. SMS
                    ShareActionButton(
                        iconVector = PhosphorIcons.Regular.ChatCircle,
                        label = stringResource(R.string.share_sms),
                        backgroundColor = Color(0xFF222226),
                    ) {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                            putExtra(
                                "sms_body",
                                "${track.name} • ${track.artist}\n${track.openLink()}",
                            )
                        }
                        runCatching { context.startActivity(smsIntent) }
                    }

                    // 4. Estado / Stories
                    ShareActionButton(
                        iconPainter = painterResource(R.drawable.ic_stories),
                        label = stringResource(R.string.share_status),
                        backgroundColor = Color(0xFF1DB954),
                    ) {
                        if (isExporting) return@ShareActionButton
                        isExporting = true
                        scope.launch {
                            val uri = exportShareCard(context, track, selectedTonality)
                            isExporting = false
                            if (uri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${track.name} • ${track.artist}\n${track.openLink()}",
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.share_status)),
                                )
                            }
                        }
                    }

                    // 5. Más (System chooser)
                    ShareActionButton(
                        iconVector = PhosphorIcons.Regular.DotsThree,
                        label = stringResource(R.string.more),
                        backgroundColor = Color(0xFF222226),
                    ) {
                        if (isExporting) return@ShareActionButton
                        isExporting = true
                        scope.launch {
                            val uri = exportShareCard(context, track, selectedTonality)
                            isExporting = false
                            if (uri != null) {
                                val chooser = Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${track.name} • ${track.artist}\n${track.openLink()}",
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    context.getString(R.string.share),
                                )
                                context.startActivity(chooser)
                            }
                        }
                    }
                }

                if (isExporting) {
                    Spacer(Modifier.height(14.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareActionButton(
    label: String,
    backgroundColor: Color,
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Builds 3 curated tonalities (Vibrante, Gradiente, Oscuro) from the artwork.
 */
private fun buildTonalities(
    primaryColor: Color?,
    palette: List<Color>,
    cycleOffset: Int = 0,
): List<CardTonality> {
    val candidates = if (palette.isNotEmpty()) palette else listOfNotNull(primaryColor)
    val baseVibrant = if (candidates.isNotEmpty()) {
        val safeIndex = Math.floorMod(cycleOffset, candidates.size)
        candidates[safeIndex]
    } else {
        Color(0xFF13E2AE) // Spotify default cyan/teal
    }

    val secondary = if (candidates.size > 1) {
        val safeSecIndex = Math.floorMod(cycleOffset + 1, candidates.size)
        candidates[safeSecIndex]
    } else {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseVibrant.toArgb(), hsv)
        hsv[0] = (hsv[0] + 32f) % 360f
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0.4f, 1f)
        hsv[2] = (hsv[2] * 0.45f).coerceIn(0.18f, 0.55f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Tone 1: Vibrant (Solid vivid tone from cover)
    val toneVibrant = CardTonality(
        id = "vibrant",
        name = "Vibrante",
        primaryColor = baseVibrant,
        secondaryColor = baseVibrant,
        isGradient = false,
        isDark = false,
        innerBgColor = run {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(baseVibrant.toArgb(), hsv)
            hsv[1] = (hsv[1] * 0.68f).coerceIn(0.25f, 0.85f)
            hsv[2] = 0.12f // Deep rich dark tone tinted with vibrant color
            Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = 0.94f)
        },
        innerBorderColor = Color.White.copy(alpha = 0.10f),
    )

    // Tone 2: Gradient (Rich dual-tone gradient)
    val toneGradient = CardTonality(
        id = "gradient",
        name = "Gradiente",
        primaryColor = baseVibrant,
        secondaryColor = secondary,
        isGradient = true,
        isDark = false,
        innerBgColor = run {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(secondary.toArgb(), hsv)
            hsv[1] = (hsv[1] * 0.52f).coerceIn(0.20f, 0.75f)
            hsv[2] = 0.10f
            Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = 0.94f)
        },
        innerBorderColor = Color.White.copy(alpha = 0.10f),
    )

    // Tone 3: Dark / Midnight
    val toneDark = CardTonality(
        id = "dark",
        name = "Oscuro",
        primaryColor = Color(0xFF141417),
        secondaryColor = Color(0xFF09090B),
        isGradient = false,
        isDark = true,
        innerBgColor = Color(0xFF1E1E24),
        innerBorderColor = Color.White.copy(alpha = 0.12f),
    )

    return listOf(toneVibrant, toneGradient, toneDark)
}

/**
 * Renders a crisp, high-resolution 1080x1920 9:16 share card formatted exactly
 * like Spotify's official share card (outer colored canvas + inner floating dark card).
 */
suspend fun exportShareCard(
    context: Context,
    track: CatalogTrack,
    tonality: CardTonality,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Canvas Background (Subtle dark gradient)
        val storyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF0F0F12.toInt(), 0xFF050507.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), storyBgPaint)

        // 2. Draw Outer Main Card (The Spotify vertical card, 9:16 aspect ratio)
        val cardMarginHoriz = 90f
        val cardMarginTop = 180f
        val cardWidth = width - (cardMarginHoriz * 2f) // 900f
        val cardHeight = 1560f
        val cardRect = RectF(cardMarginHoriz, cardMarginTop, cardMarginHoriz + cardWidth, cardMarginTop + cardHeight)
        val cardRadius = 72f

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = when {
                tonality.isDark -> LinearGradient(
                    0f, cardRect.top, 0f, cardRect.bottom,
                    intArrayOf(0xFF1E1E24.toInt(), 0xFF0E0E12.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
                tonality.isGradient -> LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    intArrayOf(tonality.primaryColor.toArgb(), tonality.secondaryColor.toArgb()),
                    null,
                    Shader.TileMode.CLAMP,
                )
                else -> LinearGradient(
                    0f, cardRect.top, 0f, cardRect.bottom,
                    intArrayOf(tonality.primaryColor.toArgb(), blendWithBlack(tonality.primaryColor.toArgb(), 0.12f)),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x2EFFFFFF.toInt()
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBorderPaint)

        // 3. Draw Inner Floating Music Card
        val innerMarginHoriz = cardRect.left + 54f
        val innerWidth = cardWidth - 108f // 792f
        val innerHeight = cardHeight - 140f // 1420f
        val innerMarginTop = cardRect.top + 70f
        val innerRect = RectF(innerMarginHoriz, innerMarginTop, innerMarginHoriz + innerWidth, innerMarginTop + innerHeight)
        val innerRadius = 54f

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tonality.innerBgColor.toArgb()
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerPaint)

        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = tonality.innerBorderColor.toArgb()
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerBorderPaint)

        // 4. Load & Draw Artwork Bitmap (Strict clipping, NO clamp stretching bugs)
        val artPadding = 48f
        val artLeft = innerRect.left + artPadding
        val artTop = innerRect.top + artPadding
        val artworkSize = (innerWidth - (artPadding * 2f)).toInt() // 696
        val artRect = RectF(artLeft, artTop, artLeft + artworkSize, artTop + artworkSize)
        val artRadius = 38f

        val artworkReq = ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .allowHardware(false)
            .build()
        val artworkResult = (context.imageLoader.execute(artworkReq) as? SuccessResult)?.drawable?.toBitmap(artworkSize, artworkSize)

        if (artworkResult != null) {
            val artPath = Path().apply {
                addRoundRect(artRect, artRadius, artRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(artPath)
            val srcRect = Rect(0, 0, artworkResult.width, artworkResult.height)
            val dstRect = Rect(artLeft.toInt(), artTop.toInt(), (artLeft + artworkSize).toInt(), (artTop + artworkSize).toInt())
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(artworkResult, srcRect, dstRect, bitmapPaint)
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222226.toInt() }
            canvas.drawRoundRect(artRect, artRadius, artRadius, placeholderPaint)
        }

        // 5. Draw Track Title
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleTop = artTop + artworkSize + 92f
        var titleText = track.name
        val maxTextWidth = innerWidth - (artPadding * 2f)
        if (textPaint.measureText(titleText) > maxTextWidth) {
            while (titleText.length > 3 && textPaint.measureText("$titleText…") > maxTextWidth) {
                titleText = titleText.dropLast(1)
            }
            titleText = "$titleText…"
        }
        canvas.drawText(titleText, artLeft, titleTop, textPaint)

        // 6. Draw Artist Name
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBFFFFFFF.toInt()
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artistTop = titleTop + 62f
        var artistText = track.artist
        if (artistPaint.measureText(artistText) > maxTextWidth) {
            while (artistText.length > 3 && artistPaint.measureText("$artistText…") > maxTextWidth) {
                artistText = artistText.dropLast(1)
            }
            artistText = "$artistText…"
        }
        canvas.drawText(artistText, artLeft, artistTop, artistPaint)

        // 7. Draw Spotify Logo & Wordmark at Bottom
        val brandBottom = innerRect.bottom - 54f
        val spotifyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_spotify)
        val iconSize = 46
        if (spotifyDrawable != null) {
            val iconTop = (brandBottom - iconSize + 4).toInt()
            spotifyDrawable.setBounds(artLeft.toInt(), iconTop, (artLeft + iconSize).toInt(), iconTop + iconSize)
            spotifyDrawable.setTint(android.graphics.Color.WHITE)
            spotifyDrawable.draw(canvas)
        }

        val spotifyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Spotify", artLeft + iconSize + 16f, brandBottom - 6f, spotifyTextPaint)

        // 8. Save to FileProvider cache
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val shareFile = File(shareDir, "share_card_${System.currentTimeMillis()}.png")
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    }.getOrNull()
}

private fun blendWithBlack(color: Int, blackRatio: Float): Int {
    val a = android.graphics.Color.alpha(color)
    val r = (android.graphics.Color.red(color) * (1f - blackRatio)).toInt().coerceIn(0, 255)
    val g = (android.graphics.Color.green(color) * (1f - blackRatio)).toInt().coerceIn(0, 255)
    val b = (android.graphics.Color.blue(color) * (1f - blackRatio)).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(a, r, g, b)
}
