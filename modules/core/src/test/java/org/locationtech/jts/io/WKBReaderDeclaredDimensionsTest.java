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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.DeclaredCoordinateSequence;
import org.locationtech.jts.geom.impl.DeclaredCoordinateSequenceFactory;
import org.locationtech.jts.geom.util.GeometryCopier;
import org.locationtech.jts.shape.random.RandomPointsBuilder;

public class WKBReaderDeclaredDimensionsTest extends TestCase {

  // dimension, measures, ISO offset, EWKB flags
  private static final int[][] LAYOUTS = {
      {2, 0, 0, 0}, {3, 0, 1000, 0x80000000},
      {3, 1, 2000, 0x40000000}, {4, 1, 3000, 0xc0000000}
  };

  public void testEmptyAndAllNanLayoutsSurviveCopies() throws ParseException {
    WKBReader reader = WKBReader.forDeclaredDimensions();
    for (int[] layout : LAYOUTS) {
      for (int flags : new int[] {layout[2], layout[3]}) {
        for (int type : new int[] {1, 2, 3}) {
          for (boolean empty : new boolean[] {false, true}) {
            Geometry original = reader.read(primitiveWkb(type, flags, layout[0], empty));
            assertEquals(empty, original.isEmpty());
            for (Geometry copy : new Geometry[] {
                original.copy(), original.reverse(),
                GeometryCopier.copy(original, original.getFactory())}) {
              assertNotSame(original, copy);
              CoordinateSequence source = sequence(original);
              CoordinateSequence copied = sequence(copy);
              assertNotSame(source, copied);
              assertDeclared(source, layout[0], layout[1]);
              assertDeclared(copied, layout[0], layout[1]);
              assertEquals(source.size(), copied.size());
              if (!empty) {
                for (int index = 0; index < copied.size(); index++) {
                  for (int ordinate = 2; ordinate < layout[0]; ordinate++) {
                    assertTrue(Double.isNaN(copied.getOrdinate(index, ordinate)));
                  }
                }
                copied.setOrdinate(0, 0, 123);
                assertFalse(source.getX(0) == 123);
              }
            }
          }
        }
      }
    }
  }

  public void testCopyFactoryOnlyPreservesExistingDeclarations() {
    DeclaredCoordinateSequenceFactory factory = DeclaredCoordinateSequenceFactory.instance();
    DeclaredCoordinateSequence sequence = new DeclaredCoordinateSequence(
        new Coordinate[] {new Coordinate(1, 2)}, 3, 0);
    CoordinateSequence copied = factory.create(sequence);
    assertDeclared(copied, 3, 0);
    assertNotSame(sequence, copied);
    copied.setOrdinate(0, 0, 9);
    assertEquals(1.0, sequence.getX(0));
    assertOrdinary(factory.create(new Coordinate[] {new Coordinate(1, 2)}));
    assertOrdinary(factory.create(2, 3));
    assertOrdinary(factory.create(2, 3, 0));
    assertOrdinary(factory.create(CoordinateArraySequenceFactory.instance().create(2, 3)));
  }

  public void testDerivedPointsDoNotInheritDeclaration() throws ParseException {
    Geometry source = WKBReader.forDeclaredDimensions().read(
        primitiveWkb(3, 0, 2, false));
    GeometryFactory factory = source.getFactory();
    assertSame(GeometryFactory.class, factory.getClass());
    assertSame(DeclaredCoordinateSequenceFactory.instance(),
        factory.getCoordinateSequenceFactory());
    assertOrdinary(factory.getCoordinateSequenceFactory().create(2, 3));
    assertOrdinary(factory.getCoordinateSequenceFactory().create(2, 3, 0));
    Geometry points = factory.createMultiPointFromCoords(
        new Coordinate[] {new Coordinate(3, 4)});
    assertOrdinary(sequence(points.getGeometryN(0)));

    RandomPointsBuilder builder = new RandomPointsBuilder(factory);
    builder.setExtent(source);
    builder.setNumPoints(5);
    Geometry generated = builder.getGeometry();
    assertEquals(5, generated.getNumGeometries());
    for (int i = 0; i < generated.getNumGeometries(); i++) {
      CoordinateSequence seq = sequence(generated.getGeometryN(i));
      assertOrdinary(seq);
      assertTrue(Double.isNaN(seq.getZ(0)));
    }
  }

  public void testLegacyReaderDoesNotDeclareDimensions() throws ParseException {
    for (WKBReader reader : new WKBReader[] {
        new WKBReader(), new WKBReader(new GeometryFactory())}) {
      assertOrdinary(sequence(reader.read(primitiveWkb(1, 0x80000000, 3, false))));
    }
  }

  public void testDefaultAndMemberSridsAndReaderReuse() throws ParseException {
    WKBReader reader = WKBReader.forDeclaredDimensions(4326);
    Geometry first = reader.read(primitiveWkb(1, 0x80000000, 3, true));
    assertEquals(4326, first.getSRID());
    GeometryCollection collection = (GeometryCollection) reader.read(collectionWkb(3857,
        primitiveWkb(1, 0x80000000, 3, true),
        withSrid(primitiveWkb(1, 0x40000000, 3, true), 27700)));
    assertEquals(3857, collection.getSRID());
    assertEquals(3857, collection.getGeometryN(0).getSRID());
    assertEquals(27700, collection.getGeometryN(1).getSRID());
    assertDeclared(sequence(collection.getGeometryN(0)), 3, 0);
    assertDeclared(sequence(collection.getGeometryN(1)), 3, 1);
    Geometry last = reader.read(primitiveWkb(1, 0, 2, false));
    assertEquals(4326, last.getSRID());
    assertDeclared(sequence(last), 2, 0);
    assertEquals(0, WKBReader.forDeclaredDimensions().read(
        primitiveWkb(1, 0, 2, false)).getSRID());
  }

  public void testRepairRetainsDeclaredXyzLayout() throws ParseException {
    WKBReader reader = WKBReader.forDeclaredDimensions();
    ByteBuffer lineBytes = ByteBuffer.allocate(9 + 24).order(ByteOrder.LITTLE_ENDIAN);
    lineBytes.put((byte) 1).putInt(1002).putInt(1);
    lineBytes.putDouble(1).putDouble(2).putDouble(Double.NaN);
    LineString line = (LineString) reader.read(lineBytes.array());
    assertEquals(2, line.getNumPoints());
    assertDeclared(sequence(line), 3, 0);

    ByteBuffer polygonBytes = ByteBuffer.allocate(13 + 3 * 24).order(ByteOrder.LITTLE_ENDIAN);
    polygonBytes.put((byte) 1).putInt(1003).putInt(1).putInt(3);
    for (double[] xy : new double[][] {{0, 0}, {10, 0}, {10, 10}}) {
      polygonBytes.putDouble(xy[0]).putDouble(xy[1]).putDouble(Double.NaN);
    }
    Polygon polygon = (Polygon) reader.read(polygonBytes.array());
    assertEquals(4, polygon.getNumPoints());
    assertDeclared(sequence(polygon), 3, 0);
    assertEquals(sequence(polygon).getX(0), sequence(polygon).getX(3));
    assertEquals(sequence(polygon).getY(0), sequence(polygon).getY(3));
  }

  public void testRepairPreservesMeasuresAndOrdinateValues() throws ParseException {
    for (WKBReader reader : new WKBReader[] {new WKBReader(), WKBReader.forDeclaredDimensions()}) {
      for (int dimension : new int[] {3, 4}) {
        for (int count : new int[] {1, 3, 4}) {
          int type = count == 1 ? 2 : 3;
          ByteBuffer bytes = ByteBuffer.allocate(9 + (type == 3 ? 4 : 0) + count * dimension * 8)
              .order(ByteOrder.LITTLE_ENDIAN);
          bytes.put((byte) 1).putInt(type + (dimension == 3 ? 2000 : 3000));
          if (type == 3) bytes.putInt(1);
          bytes.putInt(count);
          for (int i = 0; i < count; i++) {
            bytes.putDouble(i).putDouble(i + 1);
            if (dimension == 4) bytes.putDouble(100 + i);
            bytes.putDouble(200 + i);
          }
          CoordinateSequence repaired = sequence(reader.read(bytes.array()));
          assertEquals(dimension, repaired.getDimension());
          assertEquals(1, repaired.getMeasures());
          int repairedSize = count == 1 ? 2 : count == 3 ? 4 : 5;
          assertEquals(repairedSize, repaired.size());
          for (int i = 0; i < repairedSize; i++) {
            int sourceIndex = i < count ? i : 0;
            assertEquals((double) sourceIndex, repaired.getX(i));
            assertEquals((double) sourceIndex + 1, repaired.getY(i));
            if (dimension == 4) assertEquals((double) sourceIndex + 100, repaired.getZ(i));
            assertEquals((double) sourceIndex + 200, repaired.getM(i));
          }
        }
      }
    }
  }

  public void testSequenceAndFactoryJavaSerialization() throws Exception {
    DeclaredCoordinateSequence seq = new DeclaredCoordinateSequence(0, 4, 1);
    assertDeclared((CoordinateSequence) javaRoundTrip(seq), 4, 1);
    assertSame(DeclaredCoordinateSequenceFactory.instance(),
        javaRoundTrip(DeclaredCoordinateSequenceFactory.instance()));
  }

  private static Object javaRoundTrip(Object object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(object);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Object copy = input.readObject();
    input.close();
    return copy;
  }

  private static void assertDeclared(CoordinateSequence sequence, int dimension, int measures) {
    assertTrue(sequence instanceof DeclaredCoordinateSequence);
    assertEquals(dimension, sequence.getDimension());
    assertEquals(measures, sequence.getMeasures());
  }

  private static void assertOrdinary(CoordinateSequence sequence) {
    assertFalse(sequence instanceof DeclaredCoordinateSequence);
  }

  private static CoordinateSequence sequence(Geometry geometry) {
    if (geometry instanceof Point) return ((Point) geometry).getCoordinateSequence();
    if (geometry instanceof Polygon) return ((Polygon) geometry).getExteriorRing().getCoordinateSequence();
    return ((LineString) geometry).getCoordinateSequence();
  }

  private static byte[] primitiveWkb(int type, int flags, int dimension, boolean empty) {
    int count = type == 1 ? 1 : empty ? 0 : type == 2 ? 2 : 4;
    int size = 5 + count * dimension * 8 + (type == 1 ? 0 : 4)
        + (type == 3 && !empty ? 4 : 0);
    ByteBuffer bytes = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    bytes.put((byte) 1).putInt(type + flags);
    if (type == 2) bytes.putInt(count);
    if (type == 3) {
      bytes.putInt(empty ? 0 : 1);
      if (!empty) bytes.putInt(count);
    }
    double[][] points = {{0, 0}, {10, 0}, {10, 10}, {0, 0}};
    for (int i = 0; i < count; i++) {
      bytes.putDouble(empty ? Double.NaN : points[i][0]);
      bytes.putDouble(empty ? Double.NaN : points[i][1]);
      for (int ordinate = 2; ordinate < dimension; ordinate++) bytes.putDouble(Double.NaN);
    }
    return bytes.array();
  }

  private static byte[] withSrid(byte[] wkb, int srid) {
    ByteBuffer source = ByteBuffer.wrap(wkb).order(ByteOrder.LITTLE_ENDIAN);
    source.get();
    ByteBuffer output = ByteBuffer.allocate(wkb.length + 4).order(ByteOrder.LITTLE_ENDIAN);
    output.put((byte) 1).putInt(source.getInt() | 0x20000000).putInt(srid);
    output.put(wkb, 5, wkb.length - 5);
    return output.array();
  }

  private static byte[] collectionWkb(int srid, byte[]... children) {
    int size = 13;
    for (byte[] child : children) size += child.length;
    ByteBuffer output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    output.put((byte) 1).putInt(7 | 0x20000000).putInt(srid).putInt(children.length);
    for (byte[] child : children) output.put(child);
    return output.array();
  }
}
