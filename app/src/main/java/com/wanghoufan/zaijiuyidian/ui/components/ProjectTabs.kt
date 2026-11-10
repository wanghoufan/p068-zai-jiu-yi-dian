package com.wanghoufan.zaijiuyidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme

/** 标签行的一项。历史页可把「全部项目」作为虚拟项传入。 */
data class TabItem(val id: String, val name: String)

/**
 * 项目切换标签：≤4 项按内容取宽平铺居中，>4 项横向滑动、不收「更多」；
 * 选中项高亮、长名省略号截断不换行。三页（时长/次数/历史）共用同一样式。
 */
@Composable
fun ProjectTabs(
    items: List<TabItem>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    emptyText: String? = null,
    onManage: (() -> Unit)? = null
) {
    val p = AppTheme.palette
    if (items.isEmpty() && emptyText != null && onManage != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.card)
                .border(1.dp, p.hair, RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onManage)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(emptyText, color = p.sub, fontSize = 15.sp)
            Text("＋", color = p.accentDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    if (items.size <= 4) {
        // 等分宽度在 4 个项目 + 应用字号 115% 时会把四字项目名截成「平板…」，
        // 改为按内容取宽、放不下时横向滑动，名字不被截断。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                ProjectTab(
                    name = item.name,
                    selected = item.id == selectedId,
                    enabled = enabled
                ) { onSelect(item.id) }
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                ProjectTab(
                    name = item.name,
                    selected = item.id == selectedId,
                    enabled = enabled
                ) { onSelect(item.id) }
            }
        }
    }
}

@Composable
private fun ProjectTab(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val p = AppTheme.palette
    val base = Modifier
        .height(44.dp)
        .widthIn(min = 56.dp, max = 150.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) p.accent2 else p.field)
        .then(if (selected) Modifier.border(1.dp, p.accent, RoundedCornerShape(12.dp)) else Modifier)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 12.dp)
    Box(modifier = base, contentAlignment = Alignment.Center) {
        Text(
            text = name,
            color = if (selected) p.accentDark else p.sub,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
