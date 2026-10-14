# Hazelcast 5.5 security backport

## Specification
Base: upstream v5.5.0 (e6ef7a8a874e445754bf6b9ab04056d812d993da).
Reference: v5.7.0 (60c31e3750cbad64f5720e2e02f0a9830973193c).
Target advisory: GHSA-6v25-8wq6-xq4j / CVE-2026-107726.

The advisory does not identify a public fixing commit. The candidate backport follows
5.7 Compact reflective class restrictions (361979da12, 3cfb0dc0cb, deac38c985).
Do not claim complete advisory remediation until the relationship and regression
coverage are established. Do not change member/cluster protocol versions or join checks.

## Plan
1. Reproduce unsafe automatic materialization of internal classes without accessing memory.
2. Backport default class restrictions at automatic and declarative reflective registration.
3. Keep blocked incoming types as GenericRecord; preserve ordinary DTO and explicit serializer behavior.
4. Run regression tests against original, upstream 5.7, and patched implementations.
5. Test original/patched 5.5 members in separate JVMs, including rolling replacement and IMap/topic use.
6. Prepare Maven Central publication instructions under an owned namespace; do not publish yet.

## Implementation and review
The core change is confined to CompactStreamSerializer's default class restrictions,
registration checks and GenericRecord fallback. A separate build-only change uses
native Git because the bundled JGit version cannot open partial clones.
Verification and limitations are recorded in README.md; publication prerequisites
and dependency replacement are recorded in PUBLISHING.md. No artifact was deployed.

Self-review: source matches 5.7 default class lists; explicit trusted serializers
remain usable; no cluster/member version checks were weakened. No unrelated 5.7
features were introduced. Complete advisory coverage remains an open review item.
