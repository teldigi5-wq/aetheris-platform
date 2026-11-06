import unittest
from osv_cyclonedx_gate import collect, parse_hits

class OsVContractTests(unittest.TestCase):
    def test_four_sboms_required(self):
        with self.assertRaises(ValueError):
            collect([])
    def test_missing_api_result_denied(self):
        with self.assertRaises(ValueError):
            parse_hits([], [("group", "library", "1")])
    def test_unread_pagination_denied(self):
        with self.assertRaises(ValueError):
            parse_hits([{"next_page_token":"more"}], [("group", "library", "1")])
    def test_single_reported_finding_preserved(self):
        self.assertEqual(parse_hits([{"vulns":[{"id":"GHSA-test"}]}],
            [("group", "library", "1")]), {(("group", "library", "1"), "GHSA-test")})

if __name__ == "__main__":
    unittest.main()
