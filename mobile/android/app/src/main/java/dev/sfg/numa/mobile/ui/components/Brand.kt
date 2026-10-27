/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.numa.connect.R

@Composable
fun NumaMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "NÜMA",
) {
    Image(
        painter = painterResource(R.drawable.numa_mark_rounded),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
fun NumaWordmark(modifier: Modifier = Modifier) {
    Row(modifier) {
        NumaMark(Modifier.size(28.dp))
        Spacer(Modifier.width(9.dp))
        Text("NÜMA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}
