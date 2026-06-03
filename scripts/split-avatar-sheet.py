#!/usr/bin/env python3
"""将 frontend/public/avatars 下的合图自动识别每个头像并导出 01.png …"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

AVATARS_DIR = Path(__file__).resolve().parent.parent / "frontend" / "public" / "avatars"
OUTPUT_SIZE = 256
WHITE_THRESHOLD = 245
MIN_BLOB_AREA = 8000
EXPECTED_COUNT = 15


def find_source(explicit: str | None) -> Path:
    if explicit:
        path = AVATARS_DIR / explicit
        if not path.exists():
            raise SystemExit(f"找不到文件: {path}")
        return path
    candidates = [
        p for p in AVATARS_DIR.glob("*.png") if not re.fullmatch(r"\d{2}\.png", p.name)
    ]
    if len(candidates) != 1:
        raise SystemExit(
            "请在 avatars 目录只保留一张合图，或执行: python scripts/split-avatar-sheet.py 合图文件名.png"
        )
    return candidates[0]


def detect_avatar_boxes(arr: np.ndarray) -> list[tuple[int, int, int, int]]:
    white = (
        (arr[:, :, 0] >= WHITE_THRESHOLD)
        & (arr[:, :, 1] >= WHITE_THRESHOLD)
        & (arr[:, :, 2] >= WHITE_THRESHOLD)
    )
    labeled, _ = ndimage.label(~white)
    boxes: list[tuple[int, int, int, int, int]] = []
    for label_id in range(1, int(labeled.max()) + 1):
        ys, xs = np.where(labeled == label_id)
        area = len(xs)
        if area < MIN_BLOB_AREA:
            continue
        boxes.append((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1, area))

    if len(boxes) != EXPECTED_COUNT:
        raise SystemExit(
            f"识别到 {len(boxes)} 个头像区域（期望 {EXPECTED_COUNT}），请检查合图或调整参数"
        )

    # 先按行再按列排序（左→右，上→下）
    boxes.sort(key=lambda b: (b[1] // 320, b[0]))
    return [(b[0], b[1], b[2], b[3]) for b in boxes]


def is_near_white(r: int, g: int, b: int) -> bool:
    return r >= WHITE_THRESHOLD and g >= WHITE_THRESHOLD and b >= WHITE_THRESHOLD


def edge_background_color(im: Image.Image) -> tuple[int, int, int]:
    """取四边（避开圆角白隙）的主色，补成正方形时与头像底一致。"""
    w, h = im.size
    margin = max(6, min(w, h) // 7)
    samples: list[tuple[int, int, int]] = []
    for x in range(margin, w - margin):
        samples.append(im.getpixel((x, margin)))
        samples.append(im.getpixel((x, h - 1 - margin)))
    for y in range(margin, h - margin):
        samples.append(im.getpixel((margin, y)))
        samples.append(im.getpixel((w - 1 - margin, y)))

    colored = [s for s in samples if not is_near_white(*s)]
    if not colored:
        colored = samples

    buckets: dict[tuple[int, int, int], int] = {}
    for r, g, b in colored:
        key = (r // 8 * 8, g // 8 * 8, b // 8 * 8)
        buckets[key] = buckets.get(key, 0) + 1
    return max(buckets, key=buckets.get)


def trim_white_border(im: Image.Image) -> Image.Image:
    rgb = im.convert("RGB")
    w, h = rgb.size
    pixels = rgb.load()

    def is_border(r: int, g: int, b: int) -> bool:
        return r >= WHITE_THRESHOLD and g >= WHITE_THRESHOLD and b >= WHITE_THRESHOLD

    def column_has_content(x: int) -> bool:
        return sum(1 for y in range(h) if not is_border(*pixels[x, y])) / h >= 0.01

    def row_has_content(y: int) -> bool:
        return sum(1 for x in range(w) if not is_border(*pixels[x, y])) / w >= 0.01

    left, right, top, bottom = 0, w - 1, 0, h - 1
    while left < w and not column_has_content(left):
        left += 1
    while right > left and not column_has_content(right):
        right -= 1
    while top < h and not row_has_content(top):
        top += 1
    while bottom > top and not row_has_content(bottom):
        bottom -= 1
    if right <= left or bottom <= top:
        return im
    return rgb.crop((left, top, right + 1, bottom + 1))


def fill_corner_whites(im: Image.Image, bg: tuple[int, int, int] | None = None) -> Image.Image:
    """从四角泛洪，把圆角外白色空隙填成头像底色（不碰画面中央的白）。"""
    rgb = im.convert("RGB")
    w, h = rgb.size
    pixels = rgb.load()
    if bg is None:
        bg = edge_background_color(rgb)

    visited: set[tuple[int, int]] = set()
    stack = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    while stack:
        x, y = stack.pop()
        if (x, y) in visited:
            continue
        if x < 0 or x >= w or y < 0 or y >= h:
            continue
        visited.add((x, y))
        r, g, b = pixels[x, y]
        if not is_near_white(r, g, b):
            continue
        pixels[x, y] = bg
        stack.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    return rgb


def to_square(im: Image.Image) -> Image.Image:
    w, h = im.size
    side = max(w, h)
    bg = edge_background_color(im)
    canvas = Image.new("RGB", (side, side), bg)
    canvas.paste(im, ((side - w) // 2, (side - h) // 2))
    return canvas


def process_tile(tile: Image.Image) -> Image.Image:
    tile = trim_white_border(tile)
    bg = edge_background_color(tile)
    tile = fill_corner_whites(tile, bg)
    tile = to_square(tile)
    return tile.resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.Resampling.LANCZOS)


def fill_existing_outputs() -> None:
    for index in range(1, EXPECTED_COUNT + 1):
        path = AVATARS_DIR / f"{index:02d}.png"
        if not path.exists():
            continue
        tile = Image.open(path).convert("RGB")
        bg = edge_background_color(tile)
        tile = fill_corner_whites(tile, bg)
        tile.save(path, format="PNG", optimize=True)
        print(f"  圆角填色 {path.name}")
    print("完成")


def main() -> None:
    if "--fill-only" in sys.argv:
        fill_existing_outputs()
        return

    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    explicit = argv[0] if argv else None
    source = find_source(explicit)
    im = Image.open(source).convert("RGB")
    arr = np.array(im)
    boxes = detect_avatar_boxes(arr)

    print(f"合图 {source.name}: {im.size[0]}x{im.size[1]} → 自动识别 {len(boxes)} 张")

    for index, (left, top, right, bottom) in enumerate(boxes, start=1):
        tile = im.crop((left, top, right, bottom))
        tile = process_tile(tile)
        out = AVATARS_DIR / f"{index:02d}.png"
        tile.save(out, format="PNG", optimize=True)
        print(f"  {out.name}  ({right - left}x{bottom - top})")

    for stale in AVATARS_DIR.glob("*.png"):
        if stale.name in {f"{i:02d}.png" for i in range(16, 21)}:
            stale.unlink()
            print(f"删除旧文件 {stale.name}")

    print("完成")


if __name__ == "__main__":
    main()
