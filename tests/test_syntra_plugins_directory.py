"""Fail-closed static contract for the Syntra read-only global extension directory."""
import json
import re
import unittest
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "syntra-desktop"
UI = APP / "ui"
EXPECTED_TYPES = {"repository": 14, "skill": 5, "directory": 6}
SOURCE_HOSTS = {
    "github.com", "agentskills.io", "registry.modelcontextprotocol.io",
    "pypi.org", "www.npmjs.com", "huggingface.co", "ollama.com",
}
EXPECTED_SKILLS = {
    "github-readonly", "incident-investigation", "mcp-extension-review",
    "license-provenance-review", "local-model-readiness",
}
EXPECTED_REPOS = {
    "agentskills/agentskills", "modelcontextprotocol/registry", "modelcontextprotocol/servers",
    "github/github-mcp-server", "microsoft/playwright-mcp", "ollama/ollama",
    "Comfy-Org/ComfyUI", "SYSTRAN/faster-whisper", "OHF-Voice/piper1-gpl",
    "qdrant/qdrant", "chroma-core/chroma", "langchain-ai/langchain",
    "run-llama/llama_index", "n8n-io/n8n",
}


class PluginDirectoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (UI / "index.html").read_text(encoding="utf-8")
        cls.css = (UI / "styles.css").read_text(encoding="utf-8")
        cls.app_js = (UI / "app.js").read_text(encoding="utf-8")
        cls.directory_js = (UI / "plugins-directory.js").read_text(encoding="utf-8")
        cls.catalog_js = (UI / "plugins-catalog.js").read_text(encoding="utf-8")
        cls.contract = json.loads((APP / "plugins-directory.contract.json").read_text(encoding="utf-8"))
        block = cls.catalog_js.split("  const payload = ", 1)[1].split(";\n  for (const entry", 1)[0]
        cls.catalog = json.loads(block)

    def test_source_snapshot_is_bounded_and_immutable(self):
        c = self.catalog
        self.assertEqual(1, c["schemaVersion"])
        self.assertEqual("SNAPSHOT_READ_ONLY", c["catalogMode"])
        self.assertTrue(re.fullmatch(r"[0-9a-f]{40}", c["snapshotSourceHead"]))
        self.assertEqual("BLOCKED_PENDING_HARDWARE", c["physicalPcStatus"])
        self.assertTrue(c["noExternalFetch"])
        self.assertFalse(c["automaticInstallAllowed"])
        self.assertFalse(c["executionAllowed"])
        entries = c["entries"]
        self.assertEqual(25, len(entries))
        self.assertEqual(25, len({x["id"] for x in entries}))
        for kind, count in EXPECTED_TYPES.items():
            self.assertEqual(count, len([x for x in entries if x["type"] == kind]))
        self.assertIn("Object.freeze(entry)", self.catalog_js)
        self.assertIn("Object.defineProperty(window, \"SyntraExtensionCatalog\"", self.catalog_js)

    def test_exact_reviewed_identity_sources_only(self):
        entries = self.catalog["entries"]
        repos = {x["sourceUrl"].replace("https://github.com/", "") for x in entries if x["type"] == "repository"}
        self.assertEqual(EXPECTED_REPOS, repos)
        skills = {x["id"] for x in entries if x["type"] == "skill"}
        self.assertEqual(EXPECTED_SKILLS, skills)
        for x in entries:
            with self.subTest(name=x["id"]):
                url = urlparse(x["sourceUrl"])
                self.assertEqual("https", url.scheme)
                self.assertIn(url.hostname, SOURCE_HOSTS)
                self.assertFalse(url.username or url.password)
                self.assertFalse(url.query or url.fragment)
                self.assertFalse(x["installed"])
                self.assertFalse(x["enabled"])
                self.assertFalse(x["executionEnabled"])
                self.assertFalse(x["sourceRevisionReviewed"])
                self.assertEqual("NOT_REVIEWED", x["licenseReview"])
                self.assertIn(x["status"], {"TEMPLATE_ONLY", "REFERENCE_ONLY"})
                self.assertIsInstance(x["permissionReview"], list)
                self.assertIsInstance(x["capabilityHints"], list)

    def test_view_is_accessible_and_script_ordered(self):
        for marker in ('data-section="plugins"', 'data-view="plugins"',
                       'id="extension-search"', 'id="extension-kind"',
                       'id="extension-category"', 'id="extension-list"',
                       'id="extension-review"', 'id="extension-results-count"',
                       'id="extension-clear"', 'aria-live="polite"', "Ctrl+0"):
            self.assertIn(marker, self.html)
        a = self.html.index('src="./plugins-catalog.js"')
        b = self.html.index('src="./app.js"')
        c = self.html.index('src="./plugins-directory.js"')
        self.assertLess(a, b)
        self.assertLess(b, c)
        self.assertIn('plugins: "Plugins & Skills"', self.app_js)
        self.assertIn('event.key === "0"', self.app_js)
        self.assertIn('button:focus-visible', self.css)
        self.assertIn('.extension-grid', self.css)
        self.assertIn('@media(max-width:1150px)', self.css)

    def test_view_never_contacts_or_executes_plugins(self):
        for marker in (
            "fetch(", "XMLHttpRequest", "eval(", "new Function(", "import(",
            "window.open(", "__TAURI__", "localStorage", "sessionStorage",
            "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write",
            "window.location", "navigator.sendBeacon", "WebSocket", "EventSource",
        ):
            with self.subTest(marker=marker):
                self.assertNotIn(marker, self.directory_js)
                self.assertNotIn(marker, self.catalog_js)
        self.assertNotIn('type="submit"', self.html.split('data-view="plugins"', 1)[1].split('data-view="media"', 1)[0])
        self.assertIn("document.createElement(", self.directory_js)
        self.assertIn("textContent", self.directory_js)
        self.assertIn("sourceHosts", self.directory_js)

    def test_no_false_claims_of_existing_installation_or_license(self):
        c = self.contract
        self.assertEqual("SYNTRA_EXTENSIONS_RESEARCH_DIRECTORY", c["feature"])
        self.assertEqual("PACKAGED_OFFLINE_RESEARCH_ONLY", c["scope"])
        self.assertEqual(EXPECTED_TYPES["repository"], c["entryCounts"]["repositories"])
        self.assertEqual(EXPECTED_TYPES["skill"], c["entryCounts"]["skills"])
        self.assertEqual(EXPECTED_TYPES["directory"], c["entryCounts"]["directories"])
        self.assertEqual("STATIC_SNAPSHOT_ONLY", c["catalogRefreshMode"])
        self.assertEqual("HYPOTHETICAL_NOT_GRANTED", c["permissionReviewMode"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE", c["physicalPcStatus"])
        self.assertTrue(c["noSyntheticSuccessClaims"])
        for key in ("pluginCodeDownloadEnabled","externalHttpFromExtensionView",
                    "runtimeSkillExecutionEnabled","pluginInstallEnabled",
                    "nativeCredentialBrokerWired","systemPermissionsGranted",
                    "ownerApprovalCanBeBypassed","sourceLicenseReviewed","modelGenerationEnabled"):
            self.assertFalse(c[key], key)
        self.assertIn("REVIEW ONLY", self.html)
        self.assertIn("0</strong><span>Installed / enabled", self.html)

    def test_native_surface_was_not_expanded_to_unsafe_tools(self):
        tauri = (APP / "src-tauri" / "src" / "main.rs").read_text(encoding="utf-8")
        self.assertIn("BLOCKED_PENDING_HARDWARE", tauri)
        self.assertNotIn("install_extension", tauri)
        self.assertNotIn("execute_skill", tauri)
        self.assertNotIn("register_mcp_server", tauri)


if __name__ == "__main__":
    unittest.main()
