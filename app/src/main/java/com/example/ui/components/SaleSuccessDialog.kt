package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Sale
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryNavy
import com.example.util.BengaliFormatter
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Confetti particle data structure
 */
private data class ConfettiParticle(
    val initialXRatio: Float,
    val initialYRatio: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color,
    val size: Float,
    val isCircle: Boolean,
    val rotationSpeed: Float
)

/**
 * Animated Sale Success Screen Overlay / Dialog.
 *
 * Displays:
 * 1. Bouncing green circle with white checkmark
 * 2. "বিক্রি সফল হয়েছে!" headline
 * 3. Authentic invoice number and total bill
 * 4. Lightweight celebratory confetti shower
 * 5. Auto-dismiss after 2.8 seconds or instant skip on tap to proceed to invoice
 */
@Composable
fun SaleSuccessDialog(
    sale: Sale,
    onDismiss: () -> Unit
) {
    // Entrance animations
    val circleScale = remember { Animatable(0f) }
    val checkmarkScale = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }

    // Pulsing outer halo for the green circle
    val infiniteTransition = rememberInfiniteTransition(label = "halo_pulse")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )

    // Pre-calculate randomized particles once
    val particles = remember {
        val colors = listOf(
            Color(0xFF10B981), // Emerald green
            Color(0xFF22C55E), // Light green
            Color(0xFFF59E0B), // Amber / Gold
            Color(0xFF3B82F6), // Sky Blue
            Color(0xFFEC4899), // Pink / Coral
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4)  // Cyan
        )
        val rng = Random(sale.invoiceNumber.hashCode())
        List(42) {
            ConfettiParticle(
                initialXRatio = rng.nextFloat() * 0.7f + 0.15f,
                initialYRatio = rng.nextFloat() * 0.25f + 0.25f,
                speedX = (rng.nextFloat() - 0.5f) * 450f,
                speedY = rng.nextFloat() * 380f + 120f,
                color = colors[rng.nextInt(colors.size)],
                size = rng.nextFloat() * 12f + 8f,
                isCircle = rng.nextBoolean(),
                rotationSpeed = (rng.nextFloat() - 0.5f) * 720f
            )
        }
    }

    // Trigger animations sequentially
    LaunchedEffect(Unit) {
        // Step 1: Green circle pops with spring
        circleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        // Step 2: White checkmark pops in
        checkmarkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessHigh
            )
        )
    }

    // Confetti animation
    LaunchedEffect(Unit) {
        confettiProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
    }

    // Auto-dismiss after 2.8s
    LaunchedEffect(Unit) {
        delay(2800)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("sale_success_dialog"),
            contentAlignment = Alignment.Center
        ) {
            // Confetti Canvas Layer (Full Screen)
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val progress = confettiProgress.value
                val alpha = (1f - (progress * 0.85f)).coerceIn(0f, 1f)
                val canvasWidth = size.width
                val canvasHeight = size.height

                particles.forEach { p ->
                    val x = (p.initialXRatio * canvasWidth) + (p.speedX * progress)
                    val y = (p.initialYRatio * canvasHeight) + (p.speedY * progress) + (0.5f * 980f * progress * progress)

                    if (x in 0f..canvasWidth && y in 0f..canvasHeight) {
                        val rotation = p.rotationSpeed * progress
                        rotate(degrees = rotation, pivot = Offset(x, y)) {
                            if (p.isCircle) {
                                drawCircle(
                                    color = p.color.copy(alpha = alpha),
                                    radius = p.size / 2f,
                                    center = Offset(x, y)
                                )
                            } else {
                                drawRect(
                                    color = p.color.copy(alpha = alpha),
                                    topLeft = Offset(x - p.size / 2f, y - p.size / 3f),
                                    size = Size(p.size, p.size * 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Central Success Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* prevent closing when clicking inside card body */ }
                    )
                    .testTag("card_sale_success_content"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated Green Circle with Pulsing Ring & White Checkmark
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Soft green pulsing outer ring
                        Box(
                            modifier = Modifier
                                .size(98.dp)
                                .scale(haloPulse)
                                .background(
                                    color = Color(0xFF10B981).copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                        )

                        // Main Vibrant Green Circle
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(circleScale.value)
                                .background(
                                    color = Color(0xFF10B981), // Emerald Success Green
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // White Checkmark
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "সফল",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(46.dp)
                                    .scale(checkmarkScale.value)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "বিক্রি সফল হয়েছে!" Headline
                    AnimatedVisibility(
                        visible = circleScale.value > 0.4f,
                        enter = fadeIn(animationSpec = tween(350)) + slideInVertically(
                            animationSpec = tween(350),
                            initialOffsetY = { 20 }
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "বিক্রি সফল হয়েছে!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "বিক্রির তথ্য ডাটাবেসে সফলভাবে সংরক্ষিত",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Authentic Invoice & Bill Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Invoice Number
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ইনভয়েস নম্বর:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "#${sale.invoiceNumber}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                            }

                            // Customer Name
                            if (sale.customerName.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "কাস্টমার:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = sale.customerName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                thickness = 0.8.dp
                            )

                            // Total Bill Amount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "মোট বিল:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = BengaliFormatter.formatCurrency(sale.totalAmount),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Button: View Invoice Now
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_view_invoice_success"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                    ) {
                        Text(
                            text = "ইনভয়েস দেখুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "স্বয়ংক্রিয়ভাবে ইনভয়েসে নিয়ে যাওয়া হচ্ছে...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
