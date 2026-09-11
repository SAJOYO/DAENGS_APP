"""Run: uv run --with pillow python -m unittest discover -s tools -p test_prepared_map.py"""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from PIL import Image, ImageDraw
from import_room_assets import main, import_prepared_map


class PreparedMapTest(unittest.TestCase):
    def test_import_preserves_canvas_foot_shadow_and_faint_glow_losslessly(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'drop/map_prepared/territory_pole_mine_verified.png'
            source.parent.mkdir(parents=True)
            image = Image.new('RGBA', (256, 640))
            draw = ImageDraw.Draw(image)
            draw.rectangle((100, 250, 145, 623), fill=(132, 111, 80, 255))
            draw.line((128, 620, 240, 570), fill=(80, 65, 51, 76), width=5)
            image.putpixel((5, 18), (20, 240, 100, 1))
            image.putpixel((198, 282), (255, 255, 255, 255))
            image.save(source)
            with patch('map_sprite.prepare_map_sprite', side_effect=AssertionError('must not recrop')):
                main(root / 'drop', root / 'out')
            result = Image.open(root / 'out/map_territory_pole_mine_verified_ready.webp').convert('RGBA')
            self.assertEqual(image.size, result.size)
            self.assertEqual(image.tobytes(), result.tobytes())
            self.assertEqual(image.tobytes(), Image.open(source).tobytes())

    def test_wrong_canvas_missing_alpha_and_empty_image_are_rejected(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            images = [Image.new('RGBA', (48, 120)), Image.new('RGB', (256, 640)),
                      Image.new('RGBA', (256, 640)), Image.new('RGBA', (256, 640), 'white')]
            for index, image in enumerate(images):
                with self.subTest(index=index):
                    source = root / f'{index}.png'; image.save(source)
                    with self.assertRaises(ValueError):
                        import_prepared_map(source, root / f'{index}.webp')
                    self.assertFalse((root / f'{index}.webp').exists())
