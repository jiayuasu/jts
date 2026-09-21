# JTS isolated IO patch 1.21.0-datasyslab-2

This prepares the next `org.datasyslab:jts-io-patch` release. Publication is pending
separate approval. The release contains the IO patch POM, binary jar, sources,
and Javadoc. Parent POMs and the full `org.datasyslab:jts-core` fork remain at
`1.21.0-datasyslab-1` and are not republished.

The artifact depends on stock `org.locationtech.jts:jts-core:1.20.0` and targets
Java 8. Its classes use the `org.datasyslab.jts` namespace, so applications can
keep their existing stock JTS jar. Callers opt into the patched APIs explicitly.
This is a fork artifact, not an official upstream JTS 1.21.0 release.

## Included changes

- Allow WKB input sequences to use a separate sequence factory while parsed
  geometries retain the caller's geometry factory ([#9](https://github.com/jiayuasu/jts/pull/9)).
- Add `GeometryCopier.copy` to preserve nested empty members, polygon holes,
  coordinate layouts, and the requested factory/SRID. Copies are independent,
  including empty polygons. User data is dropped, matching the stock factory's
  copy policy ([#10](https://github.com/jiayuasu/jts/pull/10)).
- Add `WKBReader.forDeclaredDimensions()` and shared declared coordinate
  sequences. Empty and all-NaN XY/XYZ/XYM/XYZM input keeps its declared layout
  through sequence copies. Ordinary allocations remain ordinary, so later XY
  operations do not acquire a declared Z. The reader work from
  [#11](https://github.com/jiayuasu/jts/pull/11) was included in the #12 merge.
- Export `WKBWriter` with `setPreserveCoordinateDimensions(true)`. The selected
  output ordinates remain an upper bound. All members of MultiPoint,
  MultiLineString, and MultiPolygon share one layout, padding missing ordinates
  with NaN. The isolated writer tests are generated from the core suite
  ([#12](https://github.com/jiayuasu/jts/pull/12)).

The empty-WKB and WKT-EMPTY fixes included in IO patch version 1 remain included.

## Compatibility

Existing reader constructors and writer defaults retain their behavior. Dimension
preservation requires the explicit reader/writer options. Stock JTS writers do
not interpret the declaration marker. Operations that create new sequences are
not guaranteed to retain a source declaration.

GeometryCollection members keep their individual layouts. Mixed-layout
GeometryCollections work in JTS and Sedona, but PostGIS may reject them. A
collection with no members has no coordinate sequence carrying its own layout;
its header declaration cannot be retained.

The jar retains the upstream Eclipse license files and includes Apache 2.0
licensing and attribution for the sequence helpers adapted from Sedona.

## Snapshot validation

Validation used a local `1.21.0-datasyslab-2-SNAPSHOT` build of
[`1a382cc4`](https://github.com/jiayuasu/jts/commit/1a382cc402f495adf1ea39c4905cafbac491e40f),
the merged code for this release. The only source-tree override was the IO
module version. The snapshot jar SHA-256 was
`d13da51b4c6316928e7194de9d437e748767233115589f7699728ffc216ff45a`.

The tested Sedona PRs were:

| PR | Tested head |
| --- | --- |
| [#3377](https://github.com/apache/sedona/pull/3377): geometry copies | `b7970fdb3e88c6448181ae5adb547547e85abb14` |
| [#3374](https://github.com/apache/sedona/pull/3374): serialization | `7d1b8d3e43bb21a0d8712b4e3963074a1f459dcc` |
| [#3378](https://github.com/apache/sedona/pull/3378): WKB readers | `21302aac2a4811a72f021c665a9133497cd6f112` |
| [#3381](https://github.com/apache/sedona/pull/3381): WKB output | `76f59c947971e06657d01229a4f940863d3e5253` |

The first three heads are included in the #3381 stack. All checks below passed:

| Check | Passed |
| --- | ---: |
| Isolated IO tests against stock JTS 1.20 | 32 |
| Full Sedona common suite on the combined stack | 1,396 |
| Selected Java/SQL tests on Spark 3.5.0 / Scala 2.12 | 324 |
| Selected Java/SQL tests on Spark 4.1.1 / Scala 2.13 | 324 |
| Python tests across both Spark versions, native and fallback modes | 96 |
| Primitive WKB/EWKB output checks across both versions | 144 |
| Multipart SQL read/write/readback checks across both versions | 18 |

Spark 4.1 ran with two executor JVMs; both executed output checks. The loaded
isolated classes matched the snapshot, and Spark's bundled JTS jar remained
unchanged. Ordinary `ST_GeneratePoints` output remained XY. The complete Sedona
CI matrix and other execution engines were outside this validation scope.

## Publication

Follow [the release guide](../../RELEASING.md#build-the-isolated-io-patch) to
build, sign, and verify the IO artifact. Compare its class files with the tested
snapshot before publication; rerun consumer checks if implementation changes.
Publish only this module from the approved
`jts-io-patch-1.21.0-datasyslab-2` tag after review and explicit approval.
