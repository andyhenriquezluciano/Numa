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

#version 440

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 size;
    vec2 cropScale;
    float radius;
    float bottomRadius;
};

layout(binding = 1) uniform sampler2D source;

void main() {
    vec2 p = qt_TexCoord0 * size;
    vec2 half_ = size * 0.5;

    // Screen pixel footprint in local coordinate space (isotropic & invariant across fragment quads)
    float px_x = length(vec2(dFdx(p.x), dFdy(p.x)));
    float px_y = length(vec2(dFdx(p.y), dFdy(p.y)));
    float px = max(0.5 * (px_x + px_y), 1e-4);

    float targetRadius = p.y < half_.y ? radius : bottomRadius;
    float r = max(0.0, min(targetRadius, min(half_.x, half_.y)));

    vec2 q = abs(p - half_) - (half_ - r);
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;

    // Standard linear coverage anti-aliasing: smooth 1px transition, zero stairstepping, zero inset
    float alpha = clamp(0.5 - d / px, 0.0, 1.0);

    // Center crop matches Image.PreserveAspectCrop on the full source texture.
    vec2 uv = 0.5 + (qt_TexCoord0 - 0.5) * cropScale;
    fragColor = texture(source, uv) * alpha * qt_Opacity;
}
