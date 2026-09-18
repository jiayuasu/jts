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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import junit.framework.TestCase;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

/** Checks headers and payloads independently of the reader used for round trips. */
public class WKBWriterDimensionsTest extends TestCase {
  private static final int[] DIMENSIONS = {2, 3, 3, 4};
  private static final int[] MEASURES = {0, 0, 1, 1};
  private static final int[] FLAGS = {0, 0x80000000, 0x40000000, 0xc0000000};

  public void testDeclaredPrimitiveLayoutsIncludingEmptyAndNaN() throws Exception {
    WKBWriter writer = writer(4, ByteOrderValues.LITTLE_ENDIAN, false);
    for (int layout = 0; layout < 4; layout++) {
      for (int type : new int[] {1, 2, 3}) {
        for (boolean empty : new boolean[] {false, true}) {
          byte[] input = primitive(type, layout, empty);
          Geometry geometry = WKBReader.forDeclaredDimensions().read(input);
          for (Geometry copy : new Geometry[] {geometry, geometry.copy(), geometry.reverse()}) {
            byte[] output = writer.write(copy);
            assertEquals(type | FLAGS[layout], type(output));
            assertEquals(input.length, output.length);
            CoordinateSequence sequence = sequence(WKBReader.forDeclaredDimensions().read(output));
            assertEquals(DIMENSIONS[layout], sequence.getDimension());
            assertEquals(MEASURES[layout], sequence.getMeasures());
            if (!empty) {
              CoordinateSequence expected = sequence(copy);
              for (int index = 0; index < expected.size(); index++) {
                assertEquals(expected.getX(index), sequence.getX(index));
                assertEquals(expected.getY(index), sequence.getY(index));
                for (int ordinate = 2; ordinate < DIMENSIONS[layout]; ordinate++) {
                  assertTrue(Double.isNaN(sequence.getOrdinate(index, ordinate)));
                }
              }
            }
          }
        }
      }
    }
  }

  public void testExplicitProjectionAndMeasureSelection() throws Exception {
    Geometry geometry = WKBReader.forDeclaredDimensions().read(primitive(1, 3, false));
    WKBWriter xy = writer(2, ByteOrderValues.LITTLE_ENDIAN, false);
    assertEquals(1, type(xy.write(geometry)));
    assertEquals(21, xy.write(geometry).length);
    WKBWriter xyz = writer(3, ByteOrderValues.LITTLE_ENDIAN, false);
    assertEquals(0x80000001, type(xyz.write(geometry)));
    assertEquals(29, xyz.write(geometry).length);
    xyz.setOutputOrdinates(Ordinate.createXYM());
    assertEquals(0x40000001, type(xyz.write(geometry)));
    assertEquals(29, xyz.write(geometry).length);
    assertEquals(1, type(xyz.write(WKBReader.forDeclaredDimensions().read(primitive(1, 1, false)))));
  }

  public void testDefaultModeRemainsUnchanged() throws Exception {
    Geometry point = WKBReader.forDeclaredDimensions().read(primitive(1, 1, false));
    WKBWriter writer = new WKBWriter(3, ByteOrderValues.LITTLE_ENDIAN);
    assertEquals(1, type(writer.write(point)));
    Geometry empty = WKBReader.forDeclaredDimensions().read(primitive(1, 0, true));
    assertEquals(0x80000001, type(writer.write(empty)));
    writer.setPreserveCoordinateDimensions(true);
    assertEquals(0x80000001, type(writer.write(point)));
    assertEquals(1, type(writer.write(empty)));
    writer.setPreserveCoordinateDimensions(false);
    assertEquals(1, type(writer.write(point)));
  }

  public void testOrdinaryXYAndLaterNonNaNZ() {
    GeometryFactory factory = new GeometryFactory();
    WKBWriter writer = writer(4, ByteOrderValues.LITTLE_ENDIAN, false);
    assertEquals(1, type(writer.write(factory.createPoint(new Coordinate(1, 2)))));
    assertEquals(1, type(writer.write(factory.createPoint())));
    assertEquals(2, type(writer.write(factory.createLineString(new Coordinate[] {
        new Coordinate(1, 2), new Coordinate(3, 4)}))));
    assertEquals(0x80000002, type(writer.write(factory.createLineString(new Coordinate[] {
        new Coordinate(1, 2), new Coordinate(3, 4, 5)}))));
  }

  public void testUnambiguousMeasuredSequences() {
    GeometryFactory factory = new GeometryFactory();
    WKBWriter writer = writer(4, ByteOrderValues.LITTLE_ENDIAN, false);
    for (int layout : new int[] {2, 3}) {
      assertEquals(1 | FLAGS[layout], type(writer.write(factory.createPoint(
          new CoordinateArraySequence(0, DIMENSIONS[layout], MEASURES[layout])))));
    }
    Point point = factory.createPoint(new CoordinateXYZM(1, 2, 3, 4));
    WKBWriter m = writer(3, ByteOrderValues.LITTLE_ENDIAN, false);
    m.setOutputOrdinates(Ordinate.createXYM());
    byte[] output = m.write(point);
    assertEquals(0x40000001, type(output));
    assertEquals(4.0, ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).getDouble(21));
  }

  public void testMixedCollectionMembersKeepTheirOwnLayouts() throws Exception {
    GeometryFactory factory = new GeometryFactory();
    Geometry xy = WKBReader.forDeclaredDimensions().read(primitive(1, 0, false));
    Geometry z = WKBReader.forDeclaredDimensions().read(primitive(1, 1, true));
    Geometry m = WKBReader.forDeclaredDimensions().read(primitive(2, 2, false));
    Geometry nested = factory.createGeometryCollection(new Geometry[] {z, m});
    Geometry collection = factory.createGeometryCollection(new Geometry[] {xy, nested});
    byte[] output = writer(4, ByteOrderValues.LITTLE_ENDIAN, false).write(collection);
    assertEquals(0xc0000007, type(output));
    ByteBuffer buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(1, buffer.getInt(10));
    assertEquals(0xc0000007, buffer.getInt(31));
    assertEquals(0x80000001, buffer.getInt(40));
    assertEquals(0x40000002, buffer.getInt(69));
    Geometry roundTrip = WKBReader.forDeclaredDimensions().read(output);
    assertEquals(2, sequence(roundTrip.getGeometryN(0)).getDimension());
    assertEquals(3, sequence(roundTrip.getGeometryN(1).getGeometryN(0)).getDimension());
    assertEquals(1, sequence(roundTrip.getGeometryN(1).getGeometryN(1)).getMeasures());
  }

  public void testMultiPointUsesOneLayoutForEveryMember() throws Exception {
    checkMultipart(1);
  }

  public void testMultiLineStringUsesOneLayoutForEveryMember() throws Exception {
    checkMultipart(2);
  }

  public void testMultiPolygonUsesOneLayoutForEveryMember() throws Exception {
    checkMultipart(3);
  }

  public void testNestedMultipartDoesNotPromoteCollectionSibling() throws Exception {
    GeometryFactory factory = new GeometryFactory();
    Point z = (Point) WKBReader.forDeclaredDimensions().read(primitive(1, 1, true));
    Point xy = factory.createPoint(new Coordinate(1, 2));
    Geometry collection = factory.createGeometryCollection(new Geometry[] {
        factory.createMultiPoint(new Point[] {z, xy}), xy});
    Geometry output = WKBReader.forDeclaredDimensions().read(
        writer(4, ByteOrderValues.LITTLE_ENDIAN, false).write(collection));
    assertEquals(3, sequence(output.getGeometryN(0).getGeometryN(1)).getDimension());
    assertEquals(2, sequence(output.getGeometryN(1)).getDimension());
  }

  public void testPolygonUnionsMeasuredShellAndZOnlyHole() throws Exception {
    Polygon shell = (Polygon) WKBReader.forDeclaredDimensions().read(primitive(3, 2, false));
    Polygon hole = (Polygon) WKBReader.forDeclaredDimensions().read(primitive(3, 1, false));
    CoordinateSequence shellSequence = sequence(shell);
    CoordinateSequence holeSequence = sequence(hole);
    double[][] shellXY = {{0, 0}, {10, 0}, {0, 10}, {0, 0}};
    double[][] holeXY = {{1, 1}, {2, 1}, {1, 2}, {1, 1}};
    for (int i = 0; i < 4; i++) {
      shellSequence.setOrdinate(i, 0, shellXY[i][0]);
      shellSequence.setOrdinate(i, 1, shellXY[i][1]);
      shellSequence.setOrdinate(i, 2, 11);
      holeSequence.setOrdinate(i, 0, holeXY[i][0]);
      holeSequence.setOrdinate(i, 1, holeXY[i][1]);
      holeSequence.setOrdinate(i, 2, 6);
    }
    Polygon polygon = new GeometryFactory().createPolygon(shell.getExteriorRing(),
        new LinearRing[] {hole.getExteriorRing()});
    for (int endian : new int[] {ByteOrderValues.BIG_ENDIAN, ByteOrderValues.LITTLE_ENDIAN}) {
      byte[] bytes = writer(4, endian, false).write(polygon);
      assertEquals(0xc0000003, type(bytes));
      assertEquals(273, bytes.length);
      Polygon output = (Polygon) WKBReader.forDeclaredDimensions().read(bytes);
      CoordinateSequence outer = sequence(output);
      CoordinateSequence inner = output.getInteriorRingN(0).getCoordinateSequence();
      assertEquals(4, outer.getDimension());
      assertEquals(1, outer.getMeasures());
      assertEquals(4, inner.getDimension());
      assertEquals(1, inner.getMeasures());
      for (int i = 0; i < 4; i++) {
        assertEquals(shellXY[i][0], outer.getX(i));
        assertEquals(shellXY[i][1], outer.getY(i));
        assertTrue(Double.isNaN(outer.getZ(i)));
        assertEquals(11.0, outer.getM(i));
        assertEquals(holeXY[i][0], inner.getX(i));
        assertEquals(holeXY[i][1], inner.getY(i));
        assertEquals(6.0, inner.getZ(i));
        assertTrue(Double.isNaN(inner.getM(i)));
      }
    }
  }

  private void checkMultipart(int primitiveType) throws Exception {
    GeometryFactory factory = new GeometryFactory();
    for (boolean emptyZ : new boolean[] {false, true}) {
      Geometry z = WKBReader.forDeclaredDimensions().read(primitive(primitiveType, 1, emptyZ));
      Geometry m = WKBReader.forDeclaredDimensions().read(primitive(primitiveType, 2, false));
      for (int i = 0; i < sequence(z).size(); i++) sequence(z).setOrdinate(i, 2, 6);
      for (int i = 0; i < sequence(m).size(); i++) sequence(m).setOrdinate(i, 2, 11);
      Coordinate[] xy = primitiveType == 1 ? new Coordinate[] {new Coordinate(1, 2)}
          : primitiveType == 2 ? new Coordinate[] {new Coordinate(1, 2), new Coordinate(3, 4)}
          : new Coordinate[] {new Coordinate(1, 2), new Coordinate(3, 4),
              new Coordinate(1, 4), new Coordinate(1, 2)};
      Geometry ordinary = primitiveType == 1 ? factory.createPoint(xy[0])
          : primitiveType == 2 ? factory.createLineString(xy) : factory.createPolygon(xy);
      Geometry multipart = primitiveType == 1
          ? factory.createMultiPoint(new Point[] {(Point) z, (Point) ordinary, (Point) m})
          : primitiveType == 2
              ? factory.createMultiLineString(new LineString[] {(LineString) z, (LineString) ordinary, (LineString) m})
              : factory.createMultiPolygon(new Polygon[] {(Polygon) z, (Polygon) ordinary, (Polygon) m});
      multipart.setSRID(4326);
      for (int layout = 0; layout < 4; layout++) {
        for (int endian : new int[] {ByteOrderValues.BIG_ENDIAN, ByteOrderValues.LITTLE_ENDIAN}) {
          for (boolean includeSrid : new boolean[] {false, true}) {
            WKBWriter writer = writer(DIMENSIONS[layout], endian, includeSrid);
            if (layout == 2) writer.setOutputOrdinates(Ordinate.createXYM());
            byte[] bytes = writer.write(multipart);
            assertEquals(primitiveType + 3 | FLAGS[layout] | (includeSrid ? 0x20000000 : 0), type(bytes));
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(bytes[0] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            Geometry output = WKBReader.forDeclaredDimensions().read(bytes);
            int offset = includeSrid ? 13 : 9;
            for (int member = 0; member < 3; member++) {
              assertEquals(primitiveType | FLAGS[layout], buffer.getInt(offset + 1));
              Geometry component = output.getGeometryN(member);
              CoordinateSequence seq = sequence(component);
              assertEquals(DIMENSIONS[layout], seq.getDimension());
              assertEquals(MEASURES[layout], seq.getMeasures());
              assertEquals(includeSrid ? 4326 : 0, component.getSRID());
              assertEquals(member == 0 && emptyZ, component.isEmpty());
              for (int i = 0; i < seq.size(); i++) {
                assertEquals(sequence(multipart.getGeometryN(member)).getX(i), seq.getX(i));
                assertEquals(sequence(multipart.getGeometryN(member)).getY(i), seq.getY(i));
                if (layout == 1 || layout == 3) {
                  if (member == 0) assertEquals(6.0, seq.getZ(i));
                  else assertTrue(Double.isNaN(seq.getZ(i)));
                }
                if (layout == 2 || layout == 3) {
                  if (member == 2) assertEquals(11.0, seq.getM(i));
                  else assertTrue(Double.isNaN(seq.getM(i)));
                }
              }
              offset += primitiveType == 1 ? 5 + DIMENSIONS[layout] * 8
                  : primitiveType == 2 ? 9 + seq.size() * DIMENSIONS[layout] * 8
                  : component.isEmpty() ? 9 : 13 + seq.size() * DIMENSIONS[layout] * 8;
            }
            assertEquals(bytes.length, offset);
          }
        }
      }
      assertEquals(0, sequence(ordinary).getMeasures());
      assertTrue(Double.isNaN(sequence(ordinary).getZ(0)));
    }
  }

  public void testEmptyCollectionHasNoDeclaration() {
    byte[] output = writer(4, ByteOrderValues.LITTLE_ENDIAN, false).write(
        new GeometryFactory().createGeometryCollection());
    assertEquals(7, type(output));
    assertEquals(9, output.length);
  }

  public void testEndianSridAndWriterReuse() throws Exception {
    Geometry zm = WKBReader.forDeclaredDimensions().read(primitive(1, 3, false));
    zm.setSRID(4326);
    Geometry xy = new GeometryFactory().createPoint(new Coordinate(1, 2));
    for (int endian : new int[] {ByteOrderValues.BIG_ENDIAN, ByteOrderValues.LITTLE_ENDIAN}) {
      for (boolean includeSrid : new boolean[] {false, true}) {
        WKBWriter writer = writer(4, endian, includeSrid);
        byte[] output = writer.write(zm);
        assertEquals(endian == ByteOrderValues.LITTLE_ENDIAN ? 1 : 0, output[0]);
        assertEquals(0xc0000001 | (includeSrid ? 0x20000000 : 0), type(output));
        assertEquals(includeSrid ? 41 : 37, output.length);
        assertEquals(includeSrid ? 4326 : 0, WKBReader.forDeclaredDimensions().read(output).getSRID());
        assertEquals(1 | (includeSrid ? 0x20000000 : 0), type(writer.write(xy)));
        Geometry collection = new GeometryFactory().createGeometryCollection(new Geometry[] {zm});
        collection.setSRID(3857);
        Geometry result = WKBReader.forDeclaredDimensions().read(writer.write(collection));
        assertEquals(includeSrid ? 3857 : 0, result.getGeometryN(0).getSRID());
        assertEquals(0xc0000001 | (includeSrid ? 0x20000000 : 0), type(writer.write(zm)));
      }
    }
  }

  private static WKBWriter writer(int dimension, int byteOrder, boolean includeSrid) {
    WKBWriter writer = new WKBWriter(dimension, byteOrder, includeSrid);
    writer.setPreserveCoordinateDimensions(true);
    return writer;
  }

  private static int type(byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(bytes[0] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).getInt(1);
  }

  private static CoordinateSequence sequence(Geometry geometry) {
    if (geometry instanceof Point) return ((Point) geometry).getCoordinateSequence();
    if (geometry instanceof Polygon) return ((Polygon) geometry).getExteriorRing().getCoordinateSequence();
    return ((LineString) geometry).getCoordinateSequence();
  }

  private static byte[] primitive(int type, int layout, boolean empty) {
    int size = empty ? 0 : (type == 1 ? 1 : type == 2 ? 2 : 4);
    int countFields = type == 1 ? 0 : type == 3 && !empty ? 8 : 4;
    int coordinateCount = type == 1 ? 1 : size;
    ByteBuffer buffer = ByteBuffer.allocate(5 + countFields + coordinateCount * DIMENSIONS[layout] * 8)
        .order(ByteOrder.LITTLE_ENDIAN);
    buffer.put((byte) 1).putInt(type | FLAGS[layout]);
    if (type == 3) buffer.putInt(empty ? 0 : 1);
    if (type == 2 || (type == 3 && !empty)) buffer.putInt(size);
    double[][] xy = {{1, 2}, {3, 4}, {1, 4}, {1, 2}};
    for (int i = 0; i < coordinateCount; i++) {
      buffer.putDouble(empty ? Double.NaN : xy[i][0]).putDouble(empty ? Double.NaN : xy[i][1]);
      for (int d = 2; d < DIMENSIONS[layout]; d++) buffer.putDouble(Double.NaN);
    }
    return buffer.array();
  }
}
