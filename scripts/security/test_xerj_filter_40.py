import tempfile
import unittest
from pathlib import Path
from xerj_filter_40 import FilterError, PIN, filter_source

class FilterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.scratch = Path(self.tmp.name)
        self.source = self.scratch / "xerj-pinned-fake"
        self.output = self.scratch / "xerj-filtered-fake"
        self.source.mkdir()
    def test_missing_files_rejected(self):
        with self.assertRaises(FilterError):
            filter_source(self.scratch, self.source, self.output, PIN)
        self.assertFalse(self.output.exists())
    def test_wrong_pin_rejected(self):
        with self.assertRaises(FilterError):
            filter_source(self.scratch, self.source, self.output, "0"*40)
    def test_existing_output_preserved(self):
        self.output.mkdir()
        (self.output / "sentinel").write_text("keep")
        with self.assertRaises(FilterError):
            filter_source(self.scratch, self.source, self.output, PIN)
        self.assertEqual((self.output / "sentinel").read_text(), "keep")
    def test_symlink_source_rejected(self):
        link = self.scratch / "xerj-pinned-alias"
        link.symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(FilterError):
            filter_source(self.scratch, link, self.output, PIN)

if __name__ == "__main__":
    unittest.main()
