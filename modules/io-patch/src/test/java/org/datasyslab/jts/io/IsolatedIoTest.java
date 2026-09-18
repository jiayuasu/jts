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
import org.datasyslab.jts.geom.impl.DeclaredCoordinateSequence;
import org.datasyslab.jts.geom.impl.DeclaredCoordinateSequenceFactory;
import org.datasyslab.jts.geom.util.GeometryCopier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
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

  public void testSeparateInputFactoryUsesStockGeometryTypes() throws ParseException {
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(10), 4326,
        PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    WKBReader reader = new WKBReader(geometryFactory, new TrackingFactory());

    Geometry geometry = reader.read(emptyWkb(1, 0x80000000, 3));
    assertSame(geometryFactory, geometry.getFactory());
    assertTrue(sequence(geometry) instanceof TrackingSequence);
    assertEquals(3, sequence(geometry).getDimension());
    assertEquals(0, sequence(geometry).getMeasures());

    Point ordinary = geometryFactory.createPoint();
    assertFalse(ordinary.getCoordinateSequence() instanceof TrackingSequence);
  }

  public void testSeparateInputFactoryPreservesNestedSridsAndRepairsInput()
      throws ParseException {
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(10), 4326,
        PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    WKBReader reader = new WKBReader(geometryFactory, new TrackingFactory());

    GeometryCollection collection = (GeometryCollection) reader.read(collectionWkb(4326,
        emptyWkb(1, 0x80000000, 3),
        emptyWkbWithSrid(3, 0x40000000, 3, 3857)));
    assertSame(geometryFactory, collection.getFactory());
    assertEquals(4326, collection.getGeometryN(0).getSRID());
    assertEquals(3857, collection.getGeometryN(1).getSRID());
    assertEquals(3, sequence(collection.getGeometryN(0)).getDimension());
    assertEquals(0, sequence(collection.getGeometryN(0)).getMeasures());
    assertEquals(3, sequence(collection.getGeometryN(1)).getDimension());
    assertEquals(1, sequence(collection.getGeometryN(1)).getMeasures());

    ByteBuffer malformedLine = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN);
    malformedLine.put((byte) 1).putInt(2).putInt(1).putDouble(1.26).putDouble(2.24);
    LineString line = (LineString) reader.read(malformedLine.array());
    assertSame(geometryFactory, line.getFactory());
    assertTrue(line.getCoordinateSequence() instanceof TrackingSequence);
    assertEquals(2, line.getNumPoints());
    assertEquals(1.3, line.getCoordinateN(0).x);
    assertEquals(2.2, line.getCoordinateN(0).y);
  }

  public void testDeclaredReaderUsesIsolatedSequencesWithStockGeometryTypes() throws Exception {
    int[][] layouts = {{2, 0, 0}, {3, 0, 0x80000000}, {3, 1, 0x40000000}, {4, 1, 0xc0000000}};
    WKBReader reader = WKBReader.forDeclaredDimensions(4326);
    for (int[] layout : layouts) {
      for (int type : new int[] {1, 2, 3}) {
        Geometry original = reader.read(emptyWkb(type, layout[2], layout[0]));
        assertEquals(4326, original.getSRID());
        assertSame(GeometryFactory.class, original.getFactory().getClass());
        assertSame(DeclaredCoordinateSequenceFactory.instance(),
            original.getFactory().getCoordinateSequenceFactory());
        for (Geometry geometry : new Geometry[] {original, original.copy(), original.reverse(),
            GeometryCopier.copy(original, original.getFactory())}) {
          CoordinateSequence sequence = sequence(geometry);
          assertEquals("org.datasyslab.jts.geom.impl.DeclaredCoordinateSequence",
              sequence.getClass().getName());
          assertEquals(layout[0], sequence.getDimension());
          assertEquals(layout[1], sequence.getMeasures());
        }
      }
    }
    assertSame(Geometry.class, WKBReader.forDeclaredDimensions().getClass()
        .getMethod("read", byte[].class).getReturnType());
  }

  public void testIsolatedFactoryKeepsOrdinaryAllocationsUndeclared() throws ParseException {
    Geometry geometry = WKBReader.forDeclaredDimensions().read(emptyWkb(1, 0x80000000, 3));
    GeometryFactory factory = geometry.getFactory();
    assertFalse(factory.getCoordinateSequenceFactory().create(2, 3)
        instanceof DeclaredCoordinateSequence);
    assertFalse(factory.getCoordinateSequenceFactory().create(2, 3, 0)
        instanceof DeclaredCoordinateSequence);
    Point point = (Point) factory.createMultiPointFromCoords(
        new Coordinate[] {new Coordinate(1, 2)}).getGeometryN(0);
    assertFalse(point.getCoordinateSequence() instanceof DeclaredCoordinateSequence);
    assertTrue(Double.isNaN(point.getCoordinateSequence().getZ(0)));
    DeclaredCoordinateSequence declared = new DeclaredCoordinateSequence(
        new Coordinate[] {new Coordinate(1, 2)}, 3, 0);
    assertTrue(factory.getCoordinateSequenceFactory().create(declared)
        instanceof DeclaredCoordinateSequence);
    assertFalse(sequence(new WKBReader().read(emptyWkb(1, 0x80000000, 3)))
        instanceof DeclaredCoordinateSequence);
  }

  public void testIsolatedReaderRepairsMeasuredRingUsingStockJts() throws ParseException {
    for (int dimension : new int[] {3, 4}) {
      ByteBuffer bytes = ByteBuffer.allocate(13 + 3 * dimension * 8).order(ByteOrder.LITTLE_ENDIAN);
      bytes.put((byte) 1).putInt(dimension == 3 ? 2003 : 3003).putInt(1).putInt(3);
      for (int i = 0; i < 3; i++) {
        bytes.putDouble(i).putDouble(i + 1);
        if (dimension == 4) bytes.putDouble(100 + i);
        bytes.putDouble(200 + i);
      }
      CoordinateSequence repaired = sequence(WKBReader.forDeclaredDimensions().read(bytes.array()));
      assertTrue(repaired instanceof DeclaredCoordinateSequence);
      assertEquals(dimension, repaired.getDimension());
      assertEquals(1, repaired.getMeasures());
      assertEquals(4, repaired.size());
      for (int i = 0; i < 4; i++) {
        assertEquals(200.0 + i % 3, repaired.getM(i));
        if (dimension == 4) assertEquals(100.0 + i % 3, repaired.getZ(i));
      }
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

  private static byte[] emptyWkbWithSrid(int type, int typeFlags, int dimension, int srid) {
    byte[] withoutSrid = emptyWkb(type, typeFlags, dimension);
    ByteBuffer source = ByteBuffer.wrap(withoutSrid).order(ByteOrder.LITTLE_ENDIAN);
    source.get();
    int encodedType = source.getInt();
    ByteBuffer result = ByteBuffer.allocate(withoutSrid.length + 4).order(ByteOrder.LITTLE_ENDIAN);
    result.put((byte) 1).putInt(encodedType | 0x20000000).putInt(srid);
    result.put(withoutSrid, 5, withoutSrid.length - 5);
    return result.array();
  }

  private static byte[] collectionWkb(int srid, byte[]... children) {
    int size = 13;
    for (byte[] child : children) size += child.length;
    ByteBuffer result = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    result.put((byte) 1).putInt(7 | 0x20000000).putInt(srid).putInt(children.length);
    for (byte[] child : children) result.put(child);
    return result.array();
  }

  private static class TrackingFactory extends PackedCoordinateSequenceFactory {
    private static final long serialVersionUID = 1L;

    @Override
    public CoordinateSequence create(int size, int dimension) {
      return new TrackingSequence(size, dimension, 0);
    }

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
