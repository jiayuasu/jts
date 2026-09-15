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
package org.datasyslab.jts.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import junit.framework.TestCase;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;

public class IsolatedIoTest extends TestCase {

  public void testEmptyPointAndPolygonPreserveEveryDeclaredLayout() throws ParseException {
    int[] dimensions = {2, 3, 3, 4};
    int[] measures = {0, 0, 1, 1};
    int[] ewkbFlags = {0, 0x80000000, 0x40000000, 0xc0000000};
    int[] isoOffsets = {0, 1000, 2000, 3000};
    for (int i = 0; i < dimensions.length; i++) {
      for (int type : new int[] {1, 3}) {
        for (int typeFlags : new int[] {ewkbFlags[i], isoOffsets[i]}) {
          checkEmpty(type, typeFlags, dimensions[i], measures[i]);
        }
      }
    }
  }

  public void testEmptyGeometryUsesThreeArgumentFactoryDispatch() throws ParseException {
    WKBReader reader = new WKBReader(new GeometryFactory(new TrackingFactory()));
    for (int type : new int[] {1, 3}) {
      Geometry geometry = reader.read(emptyWkb(type, 0xc0000000, 4));
      assertTrue(sequence(geometry) instanceof TrackingSequence);
      assertEquals(4, sequence(geometry).getDimension());
      assertEquals(1, sequence(geometry).getMeasures());
    }
  }

  public void testNestedEmptyWktSeparatesDimensionMarkerFromEmpty() throws ParseException {
    GeometryFactory factory = new GeometryFactory();
    Point populated = factory.createPoint(new Coordinate(1, 2, 3));
    Point empty = (Point) new WKBReader().read(emptyWkb(1, 0x80000000, 3));
    GeometryCollection nested = factory.createGeometryCollection(new Geometry[] {
        populated, factory.createGeometryCollection(new Geometry[] {empty})
    });
    WKTWriter writer = new WKTWriter(3);
    writer.setOutputOrdinates(Ordinate.createXYZ());
    String text = writer.write(nested);
    assertTrue(text, text.contains("POINT Z EMPTY"));
    assertFalse(text, text.contains("ZEMPTY"));
    new WKTReader().read(text);
  }

  public void testPublicApiUsesStockJtsTypesAndWriter() throws Exception {
    assertSame(Geometry.class, WKBReader.class.getMethod("read", byte[].class).getReturnType());
    assertSame(org.locationtech.jts.io.InStream.class,
        WKBReader.class.getMethod("read", org.locationtech.jts.io.InStream.class)
            .getParameterTypes()[0]);
    assertSame(java.io.Writer.class,
        WKTWriter.class.getMethod("write", Geometry.class, java.io.Writer.class)
            .getParameterTypes()[1]);
    assertEquals("org.locationtech.jts.io.WKBWriter", WKBWriter.class.getName());
  }

  public void testMalformedInputKeepsParseExceptionContract() {
    try {
      new WKBReader().read(new byte[] {1, 1, 0});
      fail("Expected malformed WKB to fail");
    } catch (ParseException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  private void checkEmpty(int type, int typeFlags, int dimension, int measures)
      throws ParseException {
    Geometry geometry = new WKBReader().read(emptyWkb(type, typeFlags, dimension));
    assertTrue(geometry.isEmpty());
    assertSame(type == 1 ? Point.class : Polygon.class, geometry.getClass());
    assertEquals(dimension, sequence(geometry).getDimension());
    assertEquals(measures, sequence(geometry).getMeasures());
  }

  private static CoordinateSequence sequence(Geometry geometry) {
    if (geometry instanceof Point) return ((Point) geometry).getCoordinateSequence();
    if (geometry instanceof Polygon) return ((Polygon) geometry).getExteriorRing().getCoordinateSequence();
    return ((LineString) geometry).getCoordinateSequence();
  }

  private static byte[] emptyWkb(int type, int typeFlags, int dimension) {
    ByteBuffer buffer = ByteBuffer.allocate(5 + (type == 1 ? dimension * 8 : 4))
        .order(ByteOrder.LITTLE_ENDIAN);
    buffer.put((byte) 1).putInt(type + typeFlags);
    if (type == 1) {
      for (int i = 0; i < dimension; i++) buffer.putDouble(Double.NaN);
    } else {
      buffer.putInt(0);
    }
    return buffer.array();
  }

  private static class TrackingFactory extends PackedCoordinateSequenceFactory {
    private static final long serialVersionUID = 1L;

    @Override
    public CoordinateSequence create(int size, int dimension, int measures) {
      return new TrackingSequence(size, dimension, measures);
    }
  }

  private static class TrackingSequence extends CoordinateArraySequence {
    private static final long serialVersionUID = 1L;

    TrackingSequence(int size, int dimension, int measures) {
      super(size, dimension, measures);
    }
  }
}
