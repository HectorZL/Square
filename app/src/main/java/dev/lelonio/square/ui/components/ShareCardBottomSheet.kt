package dev.lelonio.square.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ChatCircle
import com.adamglin.phosphoricons.regular.Export
import com.adamglin.phosphoricons.regular.LinkSimple
import com.adamglin.phosphoricons.regular.X
import dev.lelonio.square.R
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.library.openLink
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.theme.rememberArtworkColor
import dev.lelonio.square.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    val defaultPalette = remember(primaryArtworkColor, paletteColors) {
        val list = mutableListOf<Color>()
        primaryArtworkColor?.let { list.add(it) }
        list.addAll(paletteColors.take(3))
        if (list.size < 4) {
            list.add(Color(0xFF8B1E28)) // Deep Crimson
            list.add(Color(0xFF1DB954)) // Spotify Green
            list.add(Color(0xFF1E2638)) // Midnight Navy
        }
        list.add(Color(0xFF18181C)) // Dark Slate
        list.distinct()
    }

    var selectedColor by remember(defaultPalette) {
        mutableStateOf(defaultPalette.firstOrNull() ?: Color(0xFF8B1E28))
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
                    .padding(top = 16.dp, bottom = 28.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
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
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.X,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // The Card Preview (Styled like Spotify share card)
                Box(
                    modifier = Modifier
                        .width(250.dp)
                        .aspectRatio(9f / 14f)
                        .shadow(24.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    selectedColor.copy(alpha = 0.88f),
                                    Color(0xFF0F0F12),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(10.dp))

                        // Artwork inside card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(14.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                        ) {
                            Artwork(
                                url = track.artworkUrl,
                                title = track.name,
                                modifier = Modifier.fillMaxSize(),
                                corner = 16.dp,
                            )
                        }

                        Spacer(Modifier.height(18.dp))

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

                        Spacer(Modifier.weight(1f))

                        // Spotify Branding
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1DB954)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Spotify",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f),
                                letterSpacing = 0.5.sp,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Color Swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    defaultPalette.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                )
                                .clickable { selectedColor = color },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Share Buttons Row (Copiar enlace, WhatsApp, SMS, Compartir imagen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Copy Link
                    ShareActionButton(
                        icon = PhosphorIcons.Regular.LinkSimple,
                        label = stringResource(R.string.copy_link),
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                    ) {
                        clipboard.setText(AnnotatedString(track.openLink()))
                        Toast.makeText(context, context.getString(R.string.copy_link), Toast.LENGTH_SHORT).show()
                    }

                    // WhatsApp
                    ShareActionButton(
                        icon = PhosphorIcons.Regular.Export,
                        label = stringResource(R.string.share_whatsapp),
                        backgroundColor = Color(0xFF25D366),
                    ) {
                        if (isExporting) return@ShareActionButton
                        isExporting = true
                        scope.launch {
                            val uri = exportShareCard(context, track, selectedColor)
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
                                    // Fallback to standard chooser if WhatsApp not installed
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
                                        null,
                                    )
                                    context.startActivity(chooser)
                                }
                            }
                        }
                    }

                    // SMS
                    ShareActionButton(
                        icon = PhosphorIcons.Regular.ChatCircle,
                        label = stringResource(R.string.share_sms),
                        backgroundColor = Color.White.copy(alpha = 0.12f),
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

                    // Generic Image Share
                    ShareActionButton(
                        icon = PhosphorIcons.Regular.Export,
                        label = stringResource(R.string.share_image),
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                    ) {
                        if (isExporting) return@ShareActionButton
                        isExporting = true
                        scope.launch {
                            val uri = exportShareCard(context, track, selectedColor)
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
                                    null,
                                )
                                context.startActivity(chooser)
                            }
                        }
                    }
                }

                if (isExporting) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Renders a crisp, high-resolution 1080x1920 9:16 share card and caches it for sharing.
 */
suspend fun exportShareCard(
    context: Context,
    track: CatalogTrack,
    bgColor: Color,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background Gradient
        val bgShader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(bgColor.toArgb(), 0xFF0A0A0C.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = bgShader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw Card Background
        val cardMarginHoriz = 110f
        val cardMarginTop = 260f
        val cardWidth = width - (cardMarginHoriz * 2)
        val cardHeight = 1400f
        val cardRect = RectF(cardMarginHoriz, cardMarginTop, cardMarginHoriz + cardWidth, cardMarginTop + cardHeight)

        val cardShader = LinearGradient(
            0f,
            cardMarginTop,
            0f,
            cardMarginTop + cardHeight,
            intArrayOf(0x33FFFFFF.toInt(), 0x11000000.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = cardShader
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, cardPaint)

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x44FFFFFF.toInt()
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, cardBorderPaint)

        // 3. Load Artwork Bitmap
        val artworkSize = (cardWidth - 140f).toInt()
        val artworkReq = ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .allowHardware(false)
            .build()
        val artworkResult = (context.imageLoader.execute(artworkReq) as? SuccessResult)?.drawable?.toBitmap(artworkSize, artworkSize)

        val artLeft = cardMarginHoriz + 70f
        val artTop = cardMarginTop + 80f
        val artRect = RectF(artLeft, artTop, artLeft + artworkSize, artTop + artworkSize)

        if (artworkResult != null) {
            val artShader = BitmapShader(artworkResult, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = artShader }
            canvas.drawRoundRect(artRect, 40f, 40f, artPaint)
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222226.toInt() }
            canvas.drawRoundRect(artRect, 40f, 40f, placeholderPaint)
        }

        // 4. Draw Track Title
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleTop = artTop + artworkSize + 110f
        var titleText = track.name
        if (textPaint.measureText(titleText) > (cardWidth - 140f)) {
            while (titleText.length > 3 && textPaint.measureText("$titleText…") > (cardWidth - 140f)) {
                titleText = titleText.dropLast(1)
            }
            titleText = "$titleText…"
        }
        canvas.drawText(titleText, artLeft, titleTop, textPaint)

        // 5. Draw Artist Name
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artistTop = titleTop + 65f
        var artistText = track.artist
        if (artistPaint.measureText(artistText) > (cardWidth - 140f)) {
            while (artistText.length > 3 && artistPaint.measureText("$artistText…") > (cardWidth - 140f)) {
                artistText = artistText.dropLast(1)
            }
            artistText = "$artistText…"
        }
        canvas.drawText(artistText, artLeft, artistTop, artistPaint)

        // 6. Draw Spotify Logo & Text at bottom of card
        val spotifyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1DB954.toInt()
        }
        val spotifyBottom = cardMarginTop + cardHeight - 80f
        canvas.drawCircle(artLeft + 20f, spotifyBottom - 14f, 20f, spotifyPaint)

        val spotifyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Spotify", artLeft + 54f, spotifyBottom, spotifyTextPaint)

        // 7. Save to FileProvider cache
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val shareFile = File(shareDir, "share_card_${System.currentTimeMillis()}.png")
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    }.getOrNull()
}
