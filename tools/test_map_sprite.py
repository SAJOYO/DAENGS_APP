"""Run: uv run --with pillow python -m unittest discover -s tools -p test_map_sprite.py"""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from PIL import Image, ImageDraw
from map_sprite import prepare_map_sprite, SIZE, FOOT
import room_cutout


class MapSpriteTest(unittest.TestCase):
    def test_light_matte_removal_preserves_enclosed_white_poster(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "sprite.png"
            image = Image.new("RGB", (100, 240), (238, 238, 238))
            draw = ImageDraw.Draw(image)
            draw.rectangle((30, 10, 70, 230), fill=(150, 130, 110), outline=(30, 25, 20), width=3)
            draw.rectangle((34, 100, 52, 135), fill="white", outline="black", width=2)
            image.save(source)
            result = prepare_map_sprite(source)
            self.assertEqual(SIZE, result.size)
            self.assertEqual(0, result.getpixel((0, 0))[3])
            self.assertGreater(result.getpixel((128, FOOT[1] - 3))[3], 200)
            self.assertTrue(any(r > 245 and g > 245 and b > 245 and a == 255 for r, g, b, a in result.getdata()))
            self.assertEqual(26, room_cutout.TOLERANCE)

    def test_existing_alpha_and_asymmetric_arms_keep_foot_center(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "sprite.png"
            image = Image.new("RGBA", (100, 240))
            draw = ImageDraw.Draw(image)
            draw.rectangle((30, 10, 70, 230), fill=(150, 130, 110, 255))
            draw.rectangle((5, 30, 60, 40), fill=(50, 50, 50, 255))
            image.save(source)
            result = prepare_map_sprite(source)
            row = [x for x in range(SIZE[0]) if result.getpixel((x, FOOT[1] - 4))[3] > 200]
            self.assertAlmostEqual(FOOT[0], (min(row) + max(row)) / 2, delta=1)
            self.assertEqual(0, result.getpixel((128, FOOT[1] + 3))[3])

    def test_empty_sprite_is_rejected(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "empty.png"
            Image.new("RGBA", (20, 20)).save(source)
            with self.assertRaises(ValueError):
                prepare_map_sprite(source)

    def test_nearly_transparent_noise_does_not_move_foot_or_change_scale(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "sprite.png"
            image = Image.new("RGBA", (100, 240))
            ImageDraw.Draw(image).rectangle((30, 10, 70, 230), fill=(150, 130, 110, 255))
            image.save(source)
            clean = prepare_map_sprite(source)
            image.putpixel((2, 239), (0, 0, 0, 1))
            image.putpixel((98, 1), (0, 0, 0, 16))
            image.save(source)
            self.assertEqual(clean.tobytes(), prepare_map_sprite(source).tobytes())
