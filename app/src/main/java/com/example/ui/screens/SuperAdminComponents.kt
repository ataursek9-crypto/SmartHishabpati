package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.util.BengaliFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("metric_card_${title}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BusinessStatusBadge(status: BusinessAccountStatus) {
    val (bgColor, textColor, text) = when (status) {
        BusinessAccountStatus.PENDING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "অপেক্ষমাণ")
        BusinessAccountStatus.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "সক্রিয়")
        BusinessAccountStatus.SUSPENDED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "স্থগিত")
        BusinessAccountStatus.DEACTIVATED -> Triple(Color(0xFFECEFF1), Color(0xFF546E7A), "নিষ্ক্রিয়")
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SubscriptionStatusBadge(status: SubscriptionStatus) {
    val (bgColor, textColor, text) = when (status) {
        SubscriptionStatus.ACTIVE -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "সক্রিয় প্ল্যান")
        SubscriptionStatus.TRIAL -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "ট্রায়াল")
        SubscriptionStatus.GRACE_PERIOD -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "গ্রেস পিরিয়ড")
        SubscriptionStatus.EXPIRED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "মেয়াদোত্তীর্ণ")
        SubscriptionStatus.CANCELLED -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), "বাতিল")
        SubscriptionStatus.SUSPENDED -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "স্থগিত")
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SuspendBusinessDialog(
    business: PlatformBusiness,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("ব্যবসা প্রতিষ্ঠান সাময়িক স্থগিতকরণ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "আপনি কি নিশ্চিতভাবে '${business.businessName}' সাময়িকভাবে স্থগিত করতে চান? প্রতিষ্ঠানটির ব্যবহারকারীরা সাময়িকভাবে সিস্টেমে প্রবেশ করতে পারবেন না, তবে ডেটা সম্পূর্ণ নিরাপদ থাকবে।",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("স্থগিতের কারণ (বাধ্যতামূলক)") },
                    placeholder = { Text("যেমন: সাবস্ক্রিপশন বকেয়া বা নীতি লঙ্ঘন") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isNotBlank()) {
                        onConfirm(reason)
                    }
                },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("স্থগিত নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun ChangePlanDialog(
    business: PlatformBusiness,
    plans: List<SubscriptionPlan>,
    onConfirm: (planId: String, days: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPlanId by remember { mutableStateOf(business.planId) }
    var durationDaysStr by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CardMembership, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("প্ল্যান ও মেয়াদ নির্ধারণ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("প্রতিষ্ঠান: ${business.businessName}", fontWeight = FontWeight.Bold)
                Text("প্ল্যান নির্বাচন করুন:", style = MaterialTheme.typography.labelMedium)
                plans.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedPlanId == p.id,
                            onClick = { selectedPlanId = p.id }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(p.nameBn, fontWeight = FontWeight.SemiBold)
                            Text("৳${BengaliFormatter.formatNumber(p.priceMonthly.toInt())}/মাস • সর্বোচ্চ কর্মী: ${p.maxStaff}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(
                    value = durationDaysStr,
                    onValueChange = { durationDaysStr = it.filter { char -> char.isDigit() } },
                    label = { Text("মেয়াদ বৃদ্ধি (দিন)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = durationDaysStr.toIntOrNull() ?: 30
                    onConfirm(selectedPlanId, days)
                }
            ) {
                Text("প্ল্যান আপডেট করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun ReviewPaymentDialog(
    payment: PlatformPayment,
    onApprove: () -> Unit,
    onReject: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isRejectMode by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("bn", "BD")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (isRejectMode) "পেমেন্ট বাতিলকরণ" else "পেমেন্ট যাচাই ও অনুমোদন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isRejectMode) {
                    Text("প্রতিষ্ঠান: ${payment.businessName}", fontWeight = FontWeight.Bold)
                    Text("পেমেন্ট মাধ্যম: ${payment.paymentMethod.name}")
                    Text("প্রেরক নম্বর: ${payment.senderNumber}", fontWeight = FontWeight.SemiBold)
                    Text("ট্রানজ্যাকশন আইডি: ${payment.transactionReference}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("পরিশোধিত অঙ্ক: ৳${BengaliFormatter.formatCurrency(payment.amount)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("মেয়াদ: ${payment.durationMonths} মাস")
                    Text("জমা দেওয়ার সময়: ${dateFormat.format(Date(payment.submittedAt))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    if (payment.notes.isNotBlank()) {
                        Text("নোট: ${payment.notes}", fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "অনুমোদন করলে সংশ্লিষ্ট ব্যবসা প্রতিষ্ঠানের সাবস্ক্রিপশন স্বয়ংক্রিয়ভাবে ${payment.durationMonths} মাসের জন্য বৃদ্ধি পাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("বাতিল করার কারণ উল্লেখ করুন:")
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("বাতিলের কারণ") },
                        placeholder = { Text("যেমন: ভুয়া ট্রানজ্যাকশন আইডি বা অপর্যাপ্ত টাকা") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            if (!isRejectMode) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("অনুমোদন করুন")
                }
            } else {
                Button(
                    onClick = {
                        if (rejectionReason.isNotBlank()) {
                            onReject(rejectionReason)
                        }
                    },
                    enabled = rejectionReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("বাতিল নিশ্চিত করুন")
                }
            }
        },
        dismissButton = {
            if (!isRejectMode) {
                OutlinedButton(
                    onClick = { isRejectMode = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("বাতিল করুন")
                }
            } else {
                TextButton(onClick = { isRejectMode = false }) { Text("পিছনে") }
            }
        }
    )
}
