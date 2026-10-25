"""Source and deployment wiring regression for direct-backend JWT authorization."""
import re, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class BackendJwtWiring(unittest.TestCase):
    def test_backend_filters_verify_original_signed_bearer(self):
        for svc,pkg,route,scope in (
            ("user-service","users","users","users"),
            ("audit-service","audit","events","services")):
            path=ROOT/svc/"src/main/java/io/aetheris"/pkg/"security/DirectApiJwtFilter.java"
            t=path.read_text(encoding="utf-8")
            for marker in ('verifyWith(key)','parseSignedClaims','getExpiration()',
                           'getIssuedAt()','Objects.equals(role, "ADMIN")',
                           'HttpHeaders.AUTHORIZATION','SC_UNAUTHORIZED','SC_FORBIDDEN',
                           f'"/api/{route}"',f'"{scope}:read"'):
                self.assertIn(marker,t)
            self.assertNotIn('getHeader("X-Aetheris-',t)
            pom=(ROOT/svc/'pom.xml').read_text(encoding="utf-8")
            self.assertIn('<artifactId>jjwt-api</artifactId>',pom)
            self.assertIn('<artifactId>jjwt-impl</artifactId>',pom)
            y=(ROOT/svc/'src/main/resources/application.yml').read_text(encoding="utf-8")
            self.assertIn("secret: ${AETHERIS_JWT_SECRET}",y)
            test=(ROOT/svc/'src/test/java/io/aetheris'/pkg/'security/DirectApiJwtFilterTest.java').read_text(encoding="utf-8")
            for m in ('isUnauthorized()','isForbidden()','isOk()','fake.unsigned.token'):
                self.assertIn(m,test)
    def test_compose_containers_receive_required_secret(self):
        for name in ('docker-compose.yml','docker-compose.core.yml','docker-compose.integration-external.yml'):
            y=(ROOT/name).read_text(encoding="utf-8")
            for svc in ('user-service','audit-service'):
                match=re.search(rf"(?m)^  {svc}:\s*$([\s\S]*?)(?=^  [a-z][\w-]*:\s*$|\Z)",y)
                self.assertIsNotNone(match,(name,svc))
                self.assertIn('AETHERIS_JWT_SECRET: ${AETHERIS_JWT_SECRET:?',match.group(1))
    def test_helm_secret_injected(self):
        y=(ROOT/'deploy/helm/aetheris/templates/core.yaml').read_text(encoding="utf-8")
        self.assertIn('eq $app.name "user-service") (eq $app.name "audit-service"',y)
        self.assertIn('secretKeyRef: { name: aetheris-secrets, key: AETHERIS_JWT_SECRET }',y)
if __name__=='__main__':unittest.main(verbosity=2)
