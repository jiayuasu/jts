# Datasyslab JTS fork

This branch builds JTS under `org.datasyslab:jts-core`. It is a fork of upstream
development code based on
[`7e2b0e5d`](https://github.com/locationtech/jts/commit/7e2b0e5d53fa411d6b58b8e5b395b1361f9711f8),
not an official JTS 1.21.0 release, and it keeps the `org.locationtech.jts.*`
Java packages and the upstream licenses. Use it in place of
`org.locationtech.jts:jts-core`, and exclude the upstream artifact from
transitive dependencies so both jars do not supply the same classes.
See [RELEASING.md](RELEASING.md) for build and publishing instructions.

## jts-io-patch

`org.datasyslab:jts-io-patch` is for applications that keep stock
`org.locationtech.jts:jts-core:1.20.0`. It provides the classes below under
`org.datasyslab`; geometry objects and stream interfaces stay
`org.locationtech.jts.*` types. Import the patched classes explicitly.

| Class | Purpose |
| --- | --- |
| `org.datasyslab.jts.io.WKBReader` | Reader with the fork's empty-geometry fixes that can keep declared coordinate layouts |
| `org.datasyslab.jts.io.WKTReader` | Reader that can keep declared coordinate layouts |
| `org.datasyslab.jts.io.WKBWriter` | Writer that can write declared coordinate layouts |
| `org.datasyslab.jts.io.WKTWriter` | Writer with the fork's empty-geometry fixes |
| `org.datasyslab.jts.geom.util.GeometryCopier` | Structure-preserving copies into a requested factory |
| `org.datasyslab.jts.geom.impl.DeclaredCoordinateSequence`, `DeclaredCoordinateSequenceFactory` | Sequences that record a declared XY, XYZ, XYM or XYZM layout |

### Reading declared coordinate layouts

Stock JTS reads `POINT (1 2)` and `POINT Z (1 2 NaN)` into the same coordinate
sequences, so a Z or M dimension whose values are all NaN, or the dimension of
an empty geometry, is lost. The opt-in readers keep it:

```java
import org.datasyslab.jts.io.WKBReader;
import org.datasyslab.jts.io.WKTReader;
import org.locationtech.jts.geom.Geometry;

Geometry fromWkb = WKBReader.forDeclaredDimensions().read(wkbBytes);
Geometry fromWkt = WKTReader.forDeclaredDimensions().read("POINT Z (1 2 NaN)");
// forDeclaredDimensions(4326) sets a default SRID.
```

- The WKB reader creates a `DeclaredCoordinateSequence` with the layout in each
  header, including empty sequences and all-NaN Z or M ordinates.
- The WKT reader does the same for geometries tagged `Z`, `M` or `ZM`. Untagged
  coordinates are read as by an ordinary reader, so the old JTS syntax
  `POINT (1 2 3)` still reads the third ordinate as Z.
- The geometries use `DeclaredCoordinateSequenceFactory.instance()`, which keeps
  the declaration when copying sequences. Sequences that JTS operations allocate
  later are ordinary, so an XY result does not gain a declared Z because JTS
  pads it with NaN. Use `GeometryCopier.copy` with this factory when a
  structure-preserving copy is needed.
- A collection with no members has no sequence, so its own layout cannot be kept.
- Other codecs can create declared sequences with their array or size
  constructors and inspect the marker type, dimension and measures.
- The existing reader constructors keep their behavior, including the one that
  accepts a separate input sequence factory.

`WKBReader` reads a point as empty only when its X and Y are both NaN, which is
how WKB encodes `POINT EMPTY`; `POINT (NaN 1)` keeps its ordinates.

### Writing declared coordinate layouts

Stock WKB and WKT writers ignore the declaration. The patched `WKBWriter` writes
it when asked:

```java
import org.datasyslab.jts.io.WKBWriter;
import org.locationtech.jts.io.ByteOrderValues;

WKBWriter writer = new WKBWriter(4, ByteOrderValues.LITTLE_ENDIAN, true);
writer.setPreserveCoordinateDimensions(true);
byte[] output = writer.write(geometry);
```

- The configured ordinates remain an upper bound: a 2D writer still omits Z and
  M, and a 3D writer can select M with `setOutputOrdinates(Ordinate.createXYM())`.
- Empty and all-NaN declared sequences keep their layouts, while ordinary XY
  coordinates with a padded NaN Z stay XY. Unmarked measured sequences also have
  an unambiguous layout.
- MultiPoint, MultiLineString and MultiPolygon use one layout for every member,
  padding missing ordinates with NaN. GeometryCollection members keep their own
  layouts; consumers such as PostGIS may reject a GeometryCollection with mixed
  layouts. A collection with no members is written as XY.
- The option is off by default and does not change SRID handling or WKT output.

### Copying geometries

`GeometryCopier.copy` copies stock JTS geometries into a requested factory,
keeping nested empty members, polygon holes and coordinate layouts. Like the
stock geometry factory's copy, it drops user data.

The upstream project documentation follows.

JTS Topology Suite
==================

The JTS Topology Suite is a Java library for creating and manipulating vector geometry.  It also provides a comprehensive set of geometry test cases, and the TestBuilder GUI application for working with and visualizing geometry and JTS functions.

![JTS logo](jts_logo.png)

[![GitHub Action Status](https://github.com/locationtech/jts/workflows/GitHub%20CI/badge.svg)](https://github.com/locationtech/jts/actions) 

[![Join the chat at https://gitter.im/locationtech/jts](https://badges.gitter.im/locationtech/jts.svg)](https://gitter.im/locationtech/jts?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

JTS is a project in the [LocationTech](https://www.locationtech.org) working group of the Eclipse Foundation.

![LocationTech](locationtech_mark.png) 

## Requirements

Currently JTS targets Java 8 and above.

## Resources

### Code
* [GitHub Repo](https://github.com/locationtech/jts)
* [Maven Central group](https://mvnrepository.com/artifact/org.locationtech.jts)

### Websites
* [LocationTech Home](https://locationtech.org/projects/technology.jts)
* [GitHub web site](https://locationtech.github.io/jts/)

### Communication
* [Mailing List](https://accounts.eclipse.org/mailing-list/jts-dev)
* [Gitter Channel](https://gitter.im/locationtech/jts)

### Forums
* [Stack Overflow](https://stackoverflow.com/questions/tagged/jts)
* [GIS Stack Exchange](https://gis.stackexchange.com/questions/tagged/jts-topology-suite)

## License

JTS is open source software.  It is dual-licensed under:

* [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html)
* [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) (a BSD Style License)

See also:

* [License details](LICENSES.md)
* Licensing [FAQ](FAQ-LICENSING.md)

## Documentation

* [**Javadoc**](https://locationtech.github.io/jts/javadoc) for the latest version of JTS
* [**FAQ**](https://locationtech.github.io/jts/jts-faq.html) - Frequently Asked Questions 
* [**User Guide**](USING.md) - Installing and using JTS 
* [**Tools**](doc/TOOLS.md) - Guide to tools included with JTS
* [**Developing Guide**](DEVELOPING.md) - how to build and develop for JTS
* [**Upgrade Guide**](MIGRATION.md) - How to migrate from previous versions of JTS

## History

* [**Version History**](https://github.com/locationtech/jts/blob/master/doc/JTS_Version_History.md)
* History from the previous JTS SourceForge repo is in the branch [`_old/history`](https://github.com/locationtech/jts/tree/_old/history)
* Older versions of JTS can be found on SourceForge
* There is an archive of distros of older versions [here](https://github.com/dr-jts/jts-versions)

## Contributing

If you are interested in contributing to JTS please read the [**Contributing Guide**](CONTRIBUTING.md).

## Downstream Projects

### Derivatives (ports to other languages)
* [**GEOS**](https://trac.osgeo.org/geos) - C++
* [**NetTopologySuite**](https://github.com/NetTopologySuite/NetTopologySuite) - .NET
* [**JSTS**](https://github.com/bjornharrtell/jsts) - JavaScript
* [**dart_jts**](https://github.com/moovida/dart_jts) - Dart
* [**KTS**](https://github.com/mipastgt/kts) - Kotlin-Multiplatform

### Via GEOS
* [**Shapely**](https://github.com/Toblerity/Shapely) - Python wrapper of GEOS
* [**R-GEOS**](https://cran.r-project.org/web/packages/rgeos/index.html) - R wrapper of GEOS
* [**rgeo**](https://github.com/rgeo/rgeo) - Ruby wrapper of GEOS
* [**GEOSwift**](https://github.com/GEOSwift/GEOSwift)- Swift library using GEOS

There are many projects using GEOS - for a list see the [GEOS wiki](https://trac.osgeo.org/geos/wiki/Applications).

