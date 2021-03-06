/*

cx2341x HM12 conversion routines

(C) 2009 Hans Verkuil <hverkuil@xs4all.nl>

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation; either version 2.1 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Suite 500, Boston, MA  02110-1335  USA

 */

#include <string.h>
#include "libv4lconvert-priv.h"
#include "rgbyuv.h"

/* The HM12 format is used in the Conexant cx23415/6/8 MPEG encoder devices.
   It is a macroblock format with separate Y and UV planes, each plane
   consisting of 16x16 values. All lines are always 720 bytes long. If the
   width of the image is less than 720, then the remainder is padding.

   The height has to be a multiple of 32 in order to get correct chroma
   values.

   It is basically a by-product of the MPEG encoding inside the device,
   which is available for raw video as a 'bonus feature'.
 */

static const unsigned int stride = 720;

static void v4lconvert_hm12_to_rgb(const u8 *src, u8 *dest, u32 width, u32 height, bool rgb) {
	const u8 *y_base = src;
	const u8 *uv_base = src + stride * height;
	const unsigned int mb_size = 256;
	unsigned int r = rgb ? 0 : 2;
	unsigned int b = 2 - r;

	for (unsigned int y = 0; y < height; y += 16) {
		unsigned int mb_y = (y / 16) * (stride / 16);
		unsigned int mb_uv = (y / 32) * (stride / 16);
		unsigned int maxy = (height - y < 16 ? height - y : 16);

		for (unsigned int x = 0; x < width; x += 16, mb_y++, mb_uv++) {
			unsigned int maxx = (width - x < 16 ? width - x : 16);

			const u8* src_y = y_base + mb_y * mb_size;
			const u8* src_uv = uv_base + mb_uv * mb_size;

			if (y & 0x10)
				src_uv += mb_size / 2;

			for (unsigned int i = 0; i < maxy; i++) {
				unsigned int idx = (x + (y + i) * width) * 3;

				for (unsigned int j = 0; j < maxx; j++) {
					int y = FIX_Y(src_y[j]);
					int u = src_uv[j & ~1u] - 128;
					int v = src_uv[j | 1] - 128;
					int u1 = UV2U1(u, v);
					int rg = UV2RG(u, v);
					int v1 = UV2V1(u, v);

					//TODO it might be faster to just put an if statement here for RGB/BGR,
					//especially because branch prediction should help us out.
					dest[idx + r] = CLIP_RGB(y + v1);
					dest[idx + 1] = CLIP_RGB(y - rg);
					dest[idx + b] = CLIP_RGB(y + u1);
					idx += 3;
				}
				src_y += 16;
				if (i & 1)
					src_uv += 16;
			}
		}
	}
}

void v4lconvert_hm12_to_rgb24(const u8 *src, u8 *dest, u32 width, u32 height) {
	v4lconvert_hm12_to_rgb(src, dest, width, height, true);
}

void v4lconvert_hm12_to_bgr24(const u8 *src, u8 *dest, u32 width, u32 height) {
	v4lconvert_hm12_to_rgb(src, dest, width, height, true);
}

static inline void de_macro_uv(u8 *dstu, u8 *dstv, const u8 *src, unsigned int w, unsigned int h) {
	for (unsigned int y = 0; y < h; y += 16) {
		for (unsigned int x = 0; x < w; x += 8) {
			const u8 *src_uv = src + y * stride + x * 32;
			unsigned int maxy = (h - y < 16 ? h - y : 16);
			unsigned int maxx = (w - x < 8 ? w - x : 8);

			for (unsigned int i = 0; i < maxy; i++) {
				unsigned int idx = x + (y + i) * w;

				for (unsigned int j = 0; j < maxx; j++) {
					dstu[idx + j] = src_uv[2 * j];
					dstv[idx + j] = src_uv[2 * j + 1];
				}
				src_uv += 16;
			}
		}
	}
}

static inline void de_macro_y(u8 *dst, const u8 *src, unsigned int w, unsigned int h) {
	for (unsigned int y = 0; y < h; y += 16) {
		for (unsigned int x = 0; x < w; x += 16) {
			const u8 *src_y = src + y * stride + x * 16;
			unsigned int maxy = (h - y < 16 ? h - y : 16);
			unsigned int maxx = (w - x < 16 ? w - x : 16);

			for (unsigned int i = 0; i < maxy; i++) {
				memcpy(dst + x + (y + i) * w, src_y, maxx);
				src_y += 16;
			}
		}
	}
}

void v4lconvert_hm12_to_yuv420(const u8 *src, u8 *dest, u32 width, u32 height, bool yvu) {
	de_macro_y(dest, src, width, height);
	dest += width * height;
	src += stride * height;
	if (yvu)
		de_macro_uv(dest + width * height / 4, dest, src, width / 2, height / 2);
	else
		de_macro_uv(dest, dest + width * height / 4, src, width / 2, height / 2);
}
