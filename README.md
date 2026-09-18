# Datasyslab JTS fork

This branch builds JTS under `org.datasyslab:jts-core`. It retains the
`org.locationtech.jts.*` Java packages and the upstream licenses.

The first fork version is `1.21.0-datasyslab-1`, based on upstream commit
[`7e2b0e5d`](https://github.com/locationtech/jts/commit/7e2b0e5d53fa411d6b58b8e5b395b1361f9711f8).
This is a fork of upstream development code, not an official JTS 1.21.0 release.
See [RELEASING.md](RELEASING.md) for build and publishing instructions.

Use the fork in place of `org.locationtech.jts:jts-core`. Exclude the upstream
artifact from transitive dependencies so both jars do not supply the same classes.

For applications that must retain stock JTS 1.20 geometry classes, the separate
`org.datasyslab:jts-io-patch` artifact provides the fork's empty-geometry fixes as
`org.datasyslab.jts.io.WKBReader` and `org.datasyslab.jts.io.WKTWriter`. It depends
on `org.locationtech.jts:jts-core:1.20.0`; geometry objects and stream interfaces
continue to use `org.locationtech.jts.*` types.
Callers must import the patched reader or writer explicitly.

The unpublished `1.21.0-datasyslab-2` candidate also includes
`org.datasyslab.jts.geom.util.GeometryCopier.copy`, which copies stock JTS
geometries into a requested factory while preserving nested empty members,
polygon holes, and coordinate layouts. It drops user data, like the stock
geometry factory's copy operation.

To retain declared XY, XYZ, XYM and XYZM layouts through copies, opt in when
reading WKB:

```java
import org.datasyslab.jts.io.WKBReader;
import org.locationtech.jts.geom.Geometry;

Geometry geometry = WKBReader.forDeclaredDimensions().read(wkbBytes);
// Alternatively: WKBReader.forDeclaredDimensions(4326) for a default SRID.
```

The reader creates `org.datasyslab.jts.geom.impl.DeclaredCoordinateSequence`
instances even for empty sequences and all-NaN Z or M ordinates. Its ordinary
JTS geometry factory uses `DeclaredCoordinateSequenceFactory.instance()` to
retain the declaration when copying sequences. Later array-based and sized
allocations remain ordinary sequences, so operations producing XY coordinates
do not gain a declared Z merely because JTS pads them with NaN.
Other binary codecs can construct declared sequences directly using their
array or size constructors and inspect the marker type, dimension and measures.
The existing reader constructors keep their original behavior, including the
constructor accepting a separate input sequence factory.

This metadata applies to coordinate sequences. A collection with no members
has no sequence, so its own WKB header's layout cannot be retained. Arbitrary
JTS operations that allocate new sequences are also not guaranteed to preserve
a source declaration. Use `GeometryCopier.copy` with the declaration-preserving
sequence factory when a structure-preserving copy is needed. Stock WKB and WKT
writers do not interpret the declaration marker.

The version-2 candidate also exports `org.datasyslab.jts.io.WKBWriter`.
To preserve declared dimensions on output, enable its explicit option:

```java
import org.datasyslab.jts.io.WKBWriter;
import org.locationtech.jts.io.ByteOrderValues;

WKBWriter writer = new WKBWriter(4, ByteOrderValues.LITTLE_ENDIAN, true);
writer.setPreserveCoordinateDimensions(true);
byte[] output = writer.write(geometry);
```

The configured ordinates remain an upper bound. A 2D writer still omits Z and M;
a 3D writer can select M with `setOutputOrdinates(Ordinate.createXYM())`.
Empty and all-NaN declared sequences retain their layouts, while ordinary XY
coordinates with a padded NaN Z remain XY. Unmarked measured sequences also
have an unambiguous layout. Collection members keep their individual layouts;
a collection with no members is written as XY because it has no declaration.
The option defaults to false and does not change SRID handling or WKT output.

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

