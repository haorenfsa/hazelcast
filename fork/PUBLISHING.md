# Publishing the maintenance fork

This repository is an unofficial maintenance fork. No artifact has been published.
Do not upload until the security backport has been reviewed, the complete packaged
JAR has passed the verification suite, and the advisory coverage is confirmed.

## Coordinates and runtime version

Proposed public coordinate: `io.github.haorenfsa:hazelcast:5.5.0-hf.1`.
Do not publish under upstream `com.hazelcast`, and do not replace 5.5.0 artifacts.
Keep Java packages and the runtime member/cluster major.minor at 5.5. The fork suffix
identifies the Maven artifact; it must not be implemented by bypassing join checks.
Record the source commit and SHA-256 in the release notes/SBOM.

For Maven Central, log into https://central.sonatype.com using the GitHub account
`haorenfsa` and verify `io.github.haorenfsa`. GitHub login normally provisions this
namespace automatically. The GitHub token used for the fork is not a Central token.

References:
- https://central.sonatype.org/register/namespace/
- https://central.sonatype.org/publish/requirements/
- https://central.sonatype.org/publish/publish-portal-upload/
- https://central.sonatype.org/publish/publish-portal-maven/

## Build and assemble

Use JDK 17 or 21. The source reactor retains the upstream GAVs for building; do not
run `mvn deploy` on the root reactor (its distribution settings belong to upstream).
Do not install the fork under the original 5.5.0 coordinate in a shared local cache.

1. Build the full shaded core JAR with the targeted tests:

   ```sh
   mvn -B -pl hazelcast -am package \
     -Dtest=CompactSecurityBackportTest,CompactSerializationTest \
     -Dsurefire.failIfNoSpecifiedTests=false
   ```

2. Produce source and Javadoc JARs from this exact checkout. Do not substitute
   upstream sources or documentation for the patched source. The reactor has release profiles
   such as `release-devel` that enable Javadoc and shaded source generation;
   alternatively explicitly set `maven.source.skip=false`,
   `maven.shade.createSourcesJar=true` and run the Javadoc goal with the same
   dependency settings. Do not invoke deployment from those upstream profiles.
3. Derive a standalone publication POM from the shaded artifact's dependency-reduced
   **effective** POM. Retain runtime dependency versions, scopes and optional flags;
   do not accidentally reintroduce bundled dependencies from the pre-shade POM.
   Remove build/reporting/distribution configuration and upstream parent inheritance.
   Set the proposed GAV, fork name/description/URL, Apache-2.0 license, developer
   identity, and SCM URL/tag for this fork. Reject any unresolved `${...}` values.
4. Create this layout, with no extra directory above `io/`:

   ```text
   io/github/haorenfsa/hazelcast/5.5.0-hf.1/
     hazelcast-5.5.0-hf.1.pom
     hazelcast-5.5.0-hf.1.jar
     hazelcast-5.5.0-hf.1-sources.jar
     hazelcast-5.5.0-hf.1-javadoc.jar
   ```

5. GPG-sign each of the four files (ASCII detached `.asc` signature); publish the
   matching public key to a Central-supported keyserver. GitHub Verified alone does
   not make a key discoverable to Central. Add `.md5` and `.sha1` checksums (and
   preferably `.sha256`/`.sha512`) for the artifacts. Preserve upstream LICENSE and
   NOTICE content in the JAR; mark the distribution as modified.
6. ZIP the repository layout and upload through Central Portal. Validate first;
   publish only after reviewing the validated deployment. Central releases are
   immutable, so a correction requires a new fork version.

For automation, use a separate publication POM with
`org.sonatype.central:central-publishing-maven-plugin`, `autoPublish=false`, source
and Javadoc attachment, and GPG signing. Store the Portal token in Maven server
credentials or CI secrets, never in this repository. The plugin alone does not
create source/Javadoc/signatures or fix missing POM metadata.

## Consuming alongside hazelcast-spring

Changing groupId means Maven does not treat the fork and upstream as the same
dependency. Keep exactly one core Hazelcast JAR on the runtime classpath.

```xml
<dependency>
  <groupId>io.github.haorenfsa</groupId>
  <artifactId>hazelcast</artifactId>
  <version>5.5.0-hf.1</version>
</dependency>
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-spring</artifactId>
  <version>5.5.0</version>
  <exclusions>
    <exclusion>
      <groupId>com.hazelcast</groupId>
      <artifactId>hazelcast</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

Exclude original core from every other transitive path too; dependencyManagement
alone cannot replace a different groupId. Check `mvn dependency:tree` and the final
application JAR/image, then run actual scheduler/cache integration tests. Do not
claim a scanner alert is fixed merely because changing coordinates hides the match;
retain the original component identity and backport evidence in the SBOM/VEX review.

For internal-only distribution, Nexus hosted Maven is an alternative. Use a unique
fork coordinate/version and the organization's approved hosted repository, never
overwrite the upstream 5.5.0 release. No Nexus deployment was attempted here.
