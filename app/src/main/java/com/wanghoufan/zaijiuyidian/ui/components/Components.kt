package com.wanghoufan.zaijiuyidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.ui.Tab
import com.wanghoufan.zaijiuyidian.ui.ToastMsg
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Int = 14,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = AppTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(shape.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(shape.dp))
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = AppTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) p.accent else p.accent.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = AppTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Transparent)
            .border(1.dp, p.red.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TextAction(text: String, color: Color? = null, onClick: () -> Unit) {
    val p = AppTheme.palette
    Text(
        text = text,
        color = color ?: p.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (actionText != null && onAction != null) TextAction(actionText, onClick = onAction)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) p.accent2 else p.card)
            .border(1.dp, if (selected) p.accent else p.hair, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) p.accentDark else p.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PillTabs(
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.field)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { (key, label) ->
            val on = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) p.card else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) p.ink else p.sub,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = AppTheme.palette
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(CircleShape)
            .background(if (checked) p.accent else p.hair)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (checked) 20.dp else 3.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun TopTitle(
    title: String,
    eyebrow: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = p.ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
        if (action != null) {
            action()
        } else if (eyebrow != null) {
            Text(eyebrow, color = p.sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val p = AppTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.card)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf(
                Triple(Tab.TRAIN, "训练", NavKind.TIMER),
                Triple(Tab.HISTORY, "历史", NavKind.CHART),
                Triple(Tab.SETTINGS, "设置", NavKind.SETTINGS)
            ).forEach { (tab, label, kind) ->
                val active = tab == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavIcon(kind, if (active) p.accent else p.sub)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        color = if (active) p.accent else p.sub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

enum class NavKind { TIMER, CHART, SETTINGS }

@Composable
private fun NavIcon(kind: NavKind, color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.075f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (kind) {
            NavKind.TIMER -> {
                drawCircle(color, radius = w * 0.33f, center = Offset(w / 2, h * 0.56f), style = stroke)
                drawLine(color, Offset(w / 2, h * 0.56f), Offset(w * 0.68f, h * 0.40f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.06f), Offset(w * 0.64f, h * 0.06f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w / 2, h * 0.06f), Offset(w / 2, h * 0.18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            NavKind.CHART -> {
                val baseline = h * 0.88f
                val bars = listOf(0.22f to 0.48f, 0.42f to 0.75f, 0.62f to 0.38f, 0.82f to 0.62f)
                bars.forEach { (x, top) ->
                    drawLine(
                        color,
                        Offset(w * x, baseline),
                        Offset(w * x, h * top),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
            NavKind.SETTINGS -> {
                val y1 = h * 0.32f
                val y2 = h * 0.68f
                drawLine(color, Offset(w * 0.12f, y1), Offset(w * 0.88f, y1), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, y2), Offset(w * 0.88f, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.36f, y1))
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.64f, y2))
            }
        }
    }
}

@Composable
fun ToastHost(
    toast: ToastMsg?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.palette
    AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (toast != null) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(p.ink)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    toast.text,
                    color = p.card,
                    fontSize = 12.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Start
                )
                if (toast.actionLabel != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        toast.actionLabel,
                        color = p.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onAction)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        titleContentColor = p.ink,
        textContentColor = p.sub,
        title = { Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 13.sp, lineHeight = 20.sp) },
        confirmButton = {
            Text(
                confirmText,
                color = if (danger) p.red else p.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = p.sub,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Int = 0
) {
    val p = AppTheme.palette
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier),
        placeholder = { Text(placeholder, color = p.sub, fontSize = 13.sp) },
        singleLine = singleLine,
        shape = RoundedCornerShape(11.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = p.ink, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = p.field,
            unfocusedContainerColor = p.field,
            focusedBorderColor = p.accent,
            unfocusedBorderColor = p.hair,
            cursorColor = p.accent
        )
    )
}

@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.palette
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.card)
                .border(1.dp, p.hair, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(value, color = p.ink, fontSize = 12.sp, maxLines = 1)
            Text("⌄", color = p.sub, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = p.card
        ) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            color = if (key == options.firstOrNull { it.second == value }?.first) p.accent else p.ink,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, suffix: String? = null, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Text(label, color = p.sub, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = p.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (suffix != null) {
                Text(
                    suffix,
                    color = p.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(modifier.fillMaxWidth().height(1.dp).background(p.hair))
}

@Composable
fun EmptyBlock(title: String) {
    val p = AppTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("▥", color = p.sub, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = p.sub, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}
