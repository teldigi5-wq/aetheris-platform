"""Exact-source Aetheris roadmap safety checks (documentation, not release certification)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
MASTER = ROOT / "docs/master-roadmap.md"
CURRENT = ROOT / "docs/current-release-roadmap-20261025.md"

class RoadmapIntegrity(unittest.TestCase):
    def test_referenced_files_are_present(self):
        for file in (README, MASTER, CURRENT):
            self.assertTrue(file.is_file(), file)
            self.assertFalse(file.is_symlink(), file)
            self.assertLess(file.stat().st_size, 100_000)

    def test_historical_stage34_preserved_not_restarted(self):
        m=MASTER.read_text(encoding="utf-8")
        r=README.read_text(encoding="utf-8")
        self.assertIn("34 / 34 COMPLETE",m)
        self.assertIn("Stage 34 / 34",r)
        self.assertIn("There is no Stage 35",r)
        self.assertNotIn("## Stage 35",m)
        self.assertIn("PRE_PC_HARDENED",m)

    def test_physical_hardware_and_releases_stay_no_go(self):
        for path in (README,MASTER,CURRENT):
            content=path.read_text(encoding="utf-8")
            self.assertIn("BLOCKED_PENDING_HARDWARE",content,path.name)
            self.assertNotIn("PRODUCTION CERTIFIED",content)
        latest=CURRENT.read_text(encoding="utf-8")
        self.assertIn("NO_GO",latest)
        self.assertIn("Stage45",latest)
        self.assertIn("13 unresolved safety blockers",latest)

    def test_security_milestones_and_ci_recovery_truth(self):
        c=CURRENT.read_text(encoding="utf-8")
        for issue in ("#242","#251","#255","#259"):
            self.assertIn(issue,c)
        for pr in ("PR #262","PR #264"):
            self.assertIn(pr,c)
        self.assertIn("30/30",c)
        self.assertIn("HTTP 502",c)
        self.assertIn("unchanged",c)
        self.assertIn("failed data-recovery job",c)
        
    def test_enterprise_tracks_distinct_and_linked(self):
        c=CURRENT.read_text(encoding="utf-8")
        for marker in ("E3.9G-12B4D","E3.9H","E4","E5","E6","E7","E8","E9",
                       "nine runtime release gates","Execution Phase A","Stage45"):
            self.assertIn(marker,c)
        self.assertIn("docs/current-release-roadmap-20261025.md",README.read_text())
        self.assertIn("current-release-roadmap-20261025.md",MASTER.read_text())

if __name__ == "__main__":
    unittest.main()
