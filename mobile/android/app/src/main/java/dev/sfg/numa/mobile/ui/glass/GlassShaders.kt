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

package dev.sfg.numa.mobile.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect

/** A single texture lookup per low-resolution fragment; no blur loop or bitmap readback. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidLens {
    private val shader = RuntimeShader(LIQUID_LENS_SHADER)
    private var lastSize = Size.Unspecified
    private var lastDensity = Float.NaN
    private var lastDownscale = Float.NaN
    private val lastCorners = FloatArray(4) { Float.NaN }
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(size: Size, corners: FloatArray, density: Float, downscale: Float): androidx.compose.ui.graphics.RenderEffect {
        if (size != lastSize || density != lastDensity || downscale != lastDownscale || !corners.contentEquals(lastCorners)) {
            shader.setFloatUniform("extent", size.width / downscale, size.height / downscale)
            shader.setFloatUniform("radii", corners[0] / downscale, corners[1] / downscale,
                corners[2] / downscale, corners[3] / downscale)
            shader.setFloatUniform("bevel", (10f * density / downscale).coerceAtLeast(1f))
            // RenderEffect snapshots uniforms: replace only when geometry changes.
            cached = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
                .asComposeRenderEffect()
            lastSize = size
            lastDensity = density
            lastDownscale = downscale
            corners.copyInto(lastCorners)
        }
        return checkNotNull(cached)
    }
}

private const val LIQUID_LENS_SHADER = """
uniform shader backdrop;
uniform float2 extent;
uniform float4 radii;
uniform float bevel;
half4 main(float2 coord) {
    float2 p = coord - float2(4.0) - extent * 0.5;
    float r = p.x > 0.0 ? (p.y > 0.0 ? radii.z : radii.y)
                         : (p.y > 0.0 ? radii.w : radii.x);
    float2 q = abs(p) - extent * 0.5 + r;
    float2 outer = max(q, float2(0.0));
    float len = length(outer);
    float distance = min(max(q.x, q.y), 0.0) + len - r;
    float2 normal = len > 0.001 ? outer / max(len, 0.001)
        : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    normal *= sign(p);
    float edge = clamp(1.0 + distance / bevel, 0.0, 1.0);
    // Curved edge bends the transmitted image inward, leaving the body undistorted.
    float2 sampleAt = coord - normal * (edge * edge * min(bevel * 0.22, 1.5));
    half4 color = backdrop.eval(sampleAt);
    return color;
}
"""

internal val SEEDS = floatArrayOf(0f, 137.5f, 311.7f, 523.9f, 719.3f, 941.1f)

/**
 * One pass: rounded mask, contrast undercoat, tinted film, velvety satin micro-frost,
 * restrained etched rim scattering, and dimensional grounding, all laid over the blurred
 * backdrop the pane has already drawn.
 *
 * Returns premultiplied alpha, which is what Skia expects back from a runtime shader.
 */
internal const val GLASS_SHADER = """
uniform float2 uSize;
uniform float4 uRadius;
uniform float uSeed;
uniform float uFilm;
uniform float uTintMix;
uniform float uUndercoat;
uniform float uScale;
uniform float uBorder;
layout(color) uniform half4 uBase;
layout(color) uniform half4 uTint;

float roundedBox(float2 p, float2 halfExtent, float4 radii) {
    float2 pair = p.x > 0.0 ? float2(radii.y, radii.z) : float2(radii.x, radii.w);
    float r = p.y > 0.0 ? pair.y : pair.x;
    float2 q = abs(p) - halfExtent + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}
half4 main(float2 coord) {
    float d = roundedBox(coord - uSize * 0.5, uSize * 0.5, uRadius);
    float mask = clamp(0.5 - d, 0.0, 1.0);
    if (mask <= 0.0) return half4(0.0);
    float2 uv = coord / max(uSize, float2(1.0));
    float3 body = mix(float3(uBase.rgb), float3(uTint.rgb), uTintMix);
    float alpha = uFilm + uUndercoat * (1.0 - uFilm);
    body += (fract(sin(dot(coord + uSeed, float2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.0002;
    // PopupBloomSurface's neutral horizontal sheen.
    float sheen = mix(0.0314, 0.0784, 1.0 - abs(uv.x * 2.0 - 1.0));
    body = (body * alpha + float3(sheen) * (1.0 - alpha)) / max(alpha + sheen * (1.0 - alpha), 0.001);
    alpha += sheen * (1.0 - alpha);
    float heightDp = uSize.y / max(uScale, 0.1);
    float topHeight = heightDp < 80.0 ? uSize.y * 0.5 : min(uSize.y * 0.35, 120.0 * uScale);
    float top = 0.06 * clamp(1.0 - coord.y / max(topHeight, 1.0), 0.0, 1.0);
    float inset = -d / max(uScale, 0.1);
    float outer = 0.188 * (1.0 - smoothstep(0.5, 1.0, inset));
    float middle = 0.10 * (1.0 - smoothstep(0.5, 1.0, abs(inset - 1.5)));
    float inner = 0.04 * (1.0 - smoothstep(0.5, 1.0, abs(inset - 2.5)));
    float frame = clamp((outer + middle + inner + top) * uBorder, 0.0, 1.0);
    float total = alpha + frame * (1.0 - alpha);
    float3 rgb = (body * alpha * (1.0 - frame) + float3(frame)) / max(total, 0.001);
    return half4(half3(clamp(rgb, 0.0, 1.0) * total * mask), half(total * mask));
}
"""

