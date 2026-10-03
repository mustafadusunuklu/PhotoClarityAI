# Synthetic device-test fixture

Device tests now generate the PNG in memory using `Phase1SyntheticMedia.kt`, a CC0-1.0 port of `tools/GenerateTestFixtures.java`. The bytes are identical to the existing baseline `app/src/test/resources/fixtures/base.png`: 64 × 64 RGB noise, 12,420 bytes, with no EXIF, location, people or user photos. No PNG is added in the Phase 1 commit. Any local `base.png` reference copy is ignored and is not required for tests.

SHA-256: `4771c5d32bf6255bc53cff931045cab5247ec67944d243cc60fab0b1e00b4f55`.

Each device-test setup checks byte size and SHA-256 before creating media rows. No network or user files are read to obtain test media.
