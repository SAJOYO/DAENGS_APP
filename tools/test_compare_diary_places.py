# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx==0.28.1"]
# ///
"""Offline regressions for the local comparison tool. No credentials or provider calls."""

import unittest
from types import SimpleNamespace

import httpx
from compare_diary_places import assemble_result, collect_places


class ComparePlacesTest(unittest.IsolatedAsyncioTestCase):
    async def test_missing_location_never_queries_provider(self):
        def handler(request):
            self.fail("Unlocated scene must not query a provider")

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            evidence, status = await collect_places(
                client, {"point": None}, 1, "https://example.test", "key", "now"
            )
        self.assertEqual([], evidence)
        self.assertEqual(["no_location"], status)

    async def test_empty_catalog_fallback_filters_radius_wrong_park_category_and_duplicates(
        self,
    ):
        def handler(request):
            if request.url.host == "example.test":
                self.assertNotIn("Authorization", request.headers)
                return httpx.Response(200, json={"groups": []})
            self.assertEqual("KakaoAK test-only", request.headers["Authorization"])
            if "coord2address" in request.url.path:
                return httpx.Response(200, json={"documents": []})
            if "keyword" in request.url.path:
                return httpx.Response(
                    200,
                    json={
                        "documents": [
                            {
                                "id": "far",
                                "place_name": "먼 공원",
                                "distance": "300",
                                "category_name": "공원",
                            },
                            {
                                "id": "fake",
                                "place_name": "공원카페",
                                "distance": "10",
                                "category_name": "카페",
                            },
                        ]
                    },
                )
            hit = {
                "id": "cafe",
                "place_name": "근처 카페",
                "distance": "40",
                "category_name": "카페",
            }
            return httpx.Response(200, json={"documents": [hit, hit]})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            evidence, _ = await collect_places(
                client,
                {"point": [37.5, 127]},
                2,
                "https://example.test",
                "test-only",
                "now",
            )
        self.assertEqual(1, len(evidence))
        self.assertEqual("근처 카페", evidence[0]["facts"]["name"])
        self.assertEqual(40, evidence[0]["facts"]["distance_m"])

    def test_writer_cannot_add_scene_or_cite_another_scenes_place(self):
        request = {"scenes": [{"id": "a"}, {"id": "b"}]}
        evidence = {"a": [{"id": "place-a", "description": "A 주변"}], "b": []}
        wrong_citation = SimpleNamespace(
            scenes=[
                SimpleNamespace(
                    scene_id="a", background="설명", evidence_ids=["place-b"]
                )
            ]
        )
        wrong_scene = SimpleNamespace(
            scenes=[
                SimpleNamespace(
                    scene_id="b", background="설명", evidence_ids=["place-a"]
                )
            ]
        )
        for writing in (wrong_citation, wrong_scene):
            with self.assertRaises(ValueError):
                assemble_result(request, "digest", evidence, writing, "model", "now")
        valid = SimpleNamespace(
            scenes=[
                SimpleNamespace(
                    scene_id="a", background="설명", evidence_ids=["place-a"]
                )
            ]
        )
        result = assemble_result(request, "digest", evidence, valid, "model", "now")
        self.assertEqual(["a", "b"], [scene["id"] for scene in result["scenes"]])
        self.assertEqual("", result["scenes"][1]["background"])


if __name__ == "__main__":
    unittest.main()
