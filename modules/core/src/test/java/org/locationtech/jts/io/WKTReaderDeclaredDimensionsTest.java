/*
 * Copyright (c) 2026 Jia Yu.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io;

import junit.framework.TestCase;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.DeclaredCoordinateSequence;
import org.locationtech.jts.geom.impl.DeclaredCoordinateSequenceFactory;

public class WKTReaderDeclaredDimensionsTest extends TestCase {

  public void testTaggedLayoutsAreDeclaredIncludingAllNanAndEmpty() throws ParseException {
    WKTReader reader = WKTReader.forDeclaredDimensions();
    // WKT, dimension, measures
    Object[][] cases = {
        {"POINT Z (1 2 NaN)", 3, 0},
        {"POINT M (1 2 NaN)", 3, 1},
        {"POINT ZM (1 2 NaN NaN)", 4, 1},
        {"LINESTRING Z (0 0 NaN, 1 1 NaN)", 3, 0},
        {"POLYGON Z ((0 0 NaN, 1 0 NaN, 1 1 NaN, 0 0 NaN))", 3, 0},
        {"MULTIPOINT Z ((0 0 NaN), (1 1 NaN))", 3, 0},
        {"MULTIPOINT Z (0 0 NaN, 1 1 NaN)", 3, 0},
        {"GEOMETRYCOLLECTION Z (POINT Z (1 2 NaN))", 3, 0},
        {"POINT Z EMPTY", 3, 0},
        {"LINESTRING ZM EMPTY", 4, 1},
        {"POLYGON M EMPTY", 3, 1},
    };
    for (Object[] c : cases) {
      CoordinateSequence sequence = firstSequence(reader.read((String) c[0]));
      assertTrue((String) c[0], sequence instanceof DeclaredCoordinateSequence);
      assertEquals((String) c[0], c[1], sequence.getDimension());
      assertEquals((String) c[0], c[2], sequence.getMeasures());
    }
  }

  public void testUntaggedCoordinatesAreReadAsBefore() throws ParseException {
    WKTReader declaring = WKTReader.forDeclaredDimensions();
    WKTReader ordinary = new WKTReader();
    for (String wkt : new String[] {"POINT (1 2)", "POINT (1 2 3)", "LINESTRING (0 0, 1 1)",
        "LINESTRING (0 0 5, 1 1 6)", "POINT EMPTY"}) {
      CoordinateSequence sequence = firstSequence(declaring.read(wkt));
      CoordinateSequence expected = firstSequence(ordinary.read(wkt));
      assertFalse(wkt, sequence instanceof DeclaredCoordinateSequence);
      assertEquals(wkt, expected.getDimension(), sequence.getDimension());
      if (sequence.size() > 0) {
        // The old JTS syntax still keeps a third ordinate as Z.
        assertEquals(wkt, expected.getZ(0), sequence.getZ(0), 0.0);
      }
    }
  }

  public void testOrdinaryReaderDoesNotDeclare() throws ParseException {
    CoordinateSequence sequence = firstSequence(new WKTReader().read("POINT Z (1 2 NaN)"));
    assertFalse(sequence instanceof DeclaredCoordinateSequence);
  }

  public void testGeometriesUseTheCopyPreservingFactoryAndSrid() throws ParseException {
    Geometry geometry = WKTReader.forDeclaredDimensions(4326).read("LINESTRING Z (0 0 NaN, 1 1 NaN)");
    assertEquals(4326, geometry.getSRID());
    assertSame(DeclaredCoordinateSequenceFactory.instance(),
        geometry.getFactory().getCoordinateSequenceFactory());
    assertTrue(firstSequence(geometry.copy()) instanceof DeclaredCoordinateSequence);
    // Allocations made by JTS operations are not declared.
    CoordinateSequence buffered = ((Polygon) geometry.buffer(1)).getExteriorRing().getCoordinateSequence();
    assertFalse(buffered instanceof DeclaredCoordinateSequence);
  }

  private static CoordinateSequence firstSequence(Geometry geometry) {
    if (geometry instanceof Point) {
      return ((Point) geometry).getCoordinateSequence();
    }
    if (geometry instanceof LineString) {
      return ((LineString) geometry).getCoordinateSequence();
    }
    if (geometry instanceof Polygon) {
      return ((Polygon) geometry).getExteriorRing().getCoordinateSequence();
    }
    return firstSequence(((GeometryCollection) geometry).getGeometryN(0));
  }
}
