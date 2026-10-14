# Community 5.5.0 Compact security backport

This is an unofficial source-maintenance branch based on Hazelcast Community
`v5.5.0` (`e6ef7a8a874e445754bf6b9ab04056d812d993da`). It backports the default
reflective Compact class restrictions from `v5.7.0`, without changing cluster
protocol, member version, join checks, or Java package names.

## Scope and attribution

Reference source: `v5.7.0` (`60c31e3750cbad64f5720e2e02f0a9830973193c`).
Relevant upstream commits:
- [361979da12](https://github.com/hazelcast/hazelcast/commit/361979da12f18950c24719db832ca6c5e7c0534f): reflective Compact class filtering.
- [3cfb0dc0cb](https://github.com/hazelcast/hazelcast/commit/3cfb0dc0cb): default blocklist extension.
- [deac38c985](https://github.com/hazelcast/hazelcast/commit/deac38c985f87ef9acbb3becac91279e42c807ce): preserve explicit serializer overrides.

The blocked types/prefixes are taken from 5.7's `ReflectiveClassFilter` and
`DefaultSerializationServiceBuilder`. Automatic materialization of a blocked
remote type returns GenericRecord. Reflective `addClass` registration is rejected;
a trusted, explicitly implemented CompactSerializer remains permitted as in 5.7.
The existing primitive/JDK restrictions remain. Ordinary DTO and record serialization
and the 5.5 wire format are unchanged.

This intentionally does not backport the new configurable filter API/XML/YAML
surface or the option to disable defaults. A blocked nested value in a typed DTO
can fail deserialization rather than convert the whole DTO to GenericRecord as 5.7
does; this is fail-closed, but applications relying on such values need review.

The native Git build setting fixes the old JGit plugin's inability to read a
modern partial-clone repository; it does not change runtime behavior.

## Security status

Target advisory: [GHSA-6v25-8wq6-xq4j / CVE-2026-107726](https://github.com/hazelcast/hazelcast/security/advisories/GHSA-6v25-8wq6-xq4j).
The public advisory identifies affected/fixed releases but not a fixing commit.
The relationship between this restriction and the complete advisory is inferred
from the 5.7 source and observed behavior, not confirmed by the vendor.

The safe probe demonstrates that original 5.5 materializes HeapData and Packet
from a Compact schema naming those classes. On the same probe, 5.7 and this
backport preserve them as GenericRecord. Original 5.5's UnsafeObjectDataInput
probe reaches field validation and fails on Version; it is not evidence of a
successful arbitrary-memory read. No arbitrary-memory access is attempted.

Treat this as a reviewed-candidate backport until complete CVE coverage has been
established. It does not claim to backport all other 5.7 security fixes. During a
rolling upgrade, old members retain their original security exposure until replaced.

## Verification (JDK 21, compiled with release 17)

- Full core reactor `package`: passed.
- CompactSerializationTest: 29 passed.
- CompactSecurityBackportTest: 6 passed.
- Changed executable production lines: 14/14 covered with JaCoCo 0.8.12 (100%);
  this is diff line coverage, not whole-module coverage or proof of CVE completeness.
- Scoped Checkstyle and reactor validation: passed.
- Original 5.5 vs upstream 5.7 vs packaged backport safe probe: observed expected results.
- Three separate JVMs using the packaged JAR: two original 5.5 members and one
  patched member joined. Both original members were gracefully replaced one at
  a time. Membership, IMap value preservation, putIfAbsent ownership, and Topic
  delivery passed at each stage.
- No production deployment, full upstream suite, or real application workflow test.

Local build used public Maven Central with a separate settings file, skipping
license attribution generation and Javadoc for the development build. Release
packaging must restore those publication requirements. One original test exposed
an error-message regression; the backport preserves its expected diagnostic and
all 35 tests now pass.

### Reproduce

```sh
mvn -B -pl hazelcast -am package \
  -Dtest=CompactSecurityBackportTest,CompactSerializationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
mkdir -p target/fork-verification
javac --release 17 -cp hazelcast/target/hazelcast-5.5.0.jar \
  -d target/fork-verification fork/verification/*.java
java -cp target/fork-verification:hazelcast/target/hazelcast-5.5.0.jar \
  forkverification.CompactRestrictionProbe safe
```

Use the original Central 5.5.0 JAR and pass `vulnerable` to run the baseline probe;
use the Central 5.7.0 JAR and pass `safe` for the reference. For rolling tests,
ensure loopback TCP ports 15971-15973 are free and run:

```sh
python3 fork/verification/verify-rolling.py \
  "$JAVA_HOME/bin/java" "$PWD/target/fork-verification" \
  /path/to/original/hazelcast-5.5.0.jar \
  "$PWD/hazelcast/target/hazelcast-5.5.0.jar" \
  "$PWD/target/fork-verification/rolling"
```

The script creates only local test members, uses a random cluster name, and shuts
them down on completion/failure. It tests graceful replacement, not network
partitions, crash recovery, every serializer, or application-level lock semantics.

See [PUBLISHING.md](PUBLISHING.md) for the proposed Maven Central coordinates,
account setup, packaging requirements, and Spring/transitive dependency exclusions.
