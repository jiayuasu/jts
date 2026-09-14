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

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

import junit.framework.TestCase;

public class WKBReaderDimensionsTest extends TestCase {
  private static final CoordinateSequenceFactory[] FACTORIES = {
      CoordinateArraySequenceFactory.instance(),
      PackedCoordinateSequenceFactory.DOUBLE_FACTORY,
      PackedCoordinateSequenceFactory.FLOAT_FACTORY
  };
  private static final ByteOrder[] BYTE_ORDERS = {
      ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN
  };

  public void testEmptyPointXY() throws ParseException {
    checkEmpty(1, 0, 0, 2, 0);
  }

  public void testEmptyPointXYZ() throws ParseException {
    checkEmpty(1, 0x80000000, 1000, 3, 0);
  }

  public void testEmptyPointXYM() throws ParseException {
    checkEmpty(1, 0x40000000, 2000, 3, 1);
  }

  public void testEmptyPointXYZM() throws ParseException {
    checkEmpty(1, 0xc0000000, 3000, 4, 1);
  }

  public void testEmptyPolygonXY() throws ParseException {
    checkEmpty(3, 0, 0, 2, 0);
  }

  public void testEmptyPolygonXYZ() throws ParseException {
    checkEmpty(3, 0x80000000, 1000, 3, 0);
  }

  public void testEmptyPolygonXYM() throws ParseException {
    checkEmpty(3, 0x40000000, 2000, 3, 1);
  }

  public void testEmptyPolygonXYZM() throws ParseException {
    checkEmpty(3, 0xc0000000, 3000, 4, 1);
  }

  public void testEmptyLineStringDimensions() throws ParseException {
    checkEmpty(2, 0, 0, 2, 0);
    checkEmpty(2, 0x80000000, 1000, 3, 0);
    checkEmpty(2, 0x40000000, 2000, 3, 1);
    checkEmpty(2, 0xc0000000, 3000, 4, 1);
  }

  public void testPointWithNaNZ() throws ParseException {
    for (CoordinateSequenceFactory factory : FACTORIES) {
      WKBReader reader = new WKBReader(new GeometryFactory(factory));
      for (ByteOrder order : BYTE_ORDERS) {
        for (int type : new int[] {0x80000001, 1001}) {
          ByteBuffer wkb = ByteBuffer.allocate(29).order(order);
          wkb.put(endianByte(order)).putInt(type);
          wkb.putDouble(1).putDouble(2).putDouble(Double.NaN);
          Point point = (Point) reader.read(wkb.array());
          assertFalse(point.isEmpty());
          CoordinateSequence sequence = point.getCoordinateSequence();
          assertEquals(3, sequence.getDimension());
          assertEquals(0, sequence.getMeasures());
          assertEquals(1.0, sequence.getX(0));
          assertEquals(2.0, sequence.getY(0));
          assertTrue(Double.isNaN(sequence.getZ(0)));
        }
      }
    }
  }

  public void testNestedEmptyChildrenDimensionsAndSRID() throws ParseException {
    byte[] nested = collectionWKB(ByteOrder.BIG_ENDIAN, 0,
        emptyWKB(1, 2000, 3, ByteOrder.LITTLE_ENDIAN, 0),
        emptyWKB(3, 0xc0000000, 4, ByteOrder.BIG_ENDIAN, 3857));
    byte[] wkb = collectionWKB(ByteOrder.LITTLE_ENDIAN, 4326,
        nested,
        emptyWKB(1, 0, 2, ByteOrder.BIG_ENDIAN, 0),
        emptyWKB(3, 1000, 3, ByteOrder.LITTLE_ENDIAN, 0));
    for (CoordinateSequenceFactory factory : FACTORIES) {
      Geometry collection = new WKBReader(new GeometryFactory(factory)).read(wkb);
      assertEquals(4326, collection.getSRID());
      Geometry inner = collection.getGeometryN(0);
      assertEquals(4326, inner.getSRID());
      checkEmptyGeometry(inner.getGeometryN(0), 3, 1, 4326);
      checkEmptyGeometry(inner.getGeometryN(1), 4, 1, 3857);
      checkEmptyGeometry(collection.getGeometryN(1), 2, 0, 4326);
      checkEmptyGeometry(collection.getGeometryN(2), 3, 0, 4326);
    }
  }

  public void testReusedReaderResetsEmptyDimensions() throws ParseException {
    for (CoordinateSequenceFactory factory : FACTORIES) {
      WKBReader reader = new WKBReader(new GeometryFactory(factory));
      checkEmptyGeometry(reader.read(emptyWKB(1, 0xc0000000, 4,
          ByteOrder.LITTLE_ENDIAN, 4326)), 4, 1, 4326);
      checkEmptyGeometry(reader.read(emptyWKB(3, 0, 2,
          ByteOrder.BIG_ENDIAN, 0)), 2, 0, 0);
      checkEmptyGeometry(reader.read(emptyWKB(1, 2000, 3,
          ByteOrder.LITTLE_ENDIAN, 0)), 3, 1, 0);
    }
  }

  public void testEmptyGeometryUsesDimensionAwareSequenceFactory() throws ParseException {
    WKBReader reader = new WKBReader(new GeometryFactory(new DimensionAwareFactory()));
    for (int geometryType : new int[] {1, 2, 3}) {
      Geometry geometry = reader.read(emptyWKB(geometryType, 0xc0000000, 4,
          ByteOrder.LITTLE_ENDIAN, 0));
      checkEmptyGeometry(geometry, 4, 1, 0);
      assertTrue(sequence(geometry) instanceof DimensionAwareSequence);
    }
  }

  private void checkEmpty(int geometryType, int ewkbFlags, int isoOffset,
      int dimension, int measures) throws ParseException {
    for (CoordinateSequenceFactory factory : FACTORIES) {
      WKBReader reader = new WKBReader(new GeometryFactory(factory));
      for (ByteOrder order : BYTE_ORDERS) {
        for (int flags : new int[] {ewkbFlags, isoOffset}) {
          Geometry geometry = reader.read(emptyWKB(geometryType, flags, dimension, order, 0));
          checkEmptyGeometry(geometry, dimension, measures, 0);
        }
      }
    }
  }

  private void checkEmptyGeometry(Geometry geometry, int dimension, int measures, int srid) {
    assertTrue(geometry.isEmpty());
    assertEquals(srid, geometry.getSRID());
    CoordinateSequence sequence = sequence(geometry);
    assertEquals(0, sequence.size());
    assertEquals(dimension, sequence.getDimension());
    assertEquals(measures, sequence.getMeasures());
  }

  private static CoordinateSequence sequence(Geometry geometry) {
    if (geometry instanceof Point) {
      return ((Point) geometry).getCoordinateSequence();
    }
    if (geometry instanceof Polygon) {
      return ((Polygon) geometry).getExteriorRing().getCoordinateSequence();
    }
    return ((LineString) geometry).getCoordinateSequence();
  }

  // Build explicit WKB headers so the writer's handling of NaN ordinates cannot
  // change the dimensional declarations being tested.
  private static byte[] emptyWKB(int geometryType, int flags, int dimension,
      ByteOrder order, int srid) {
    int payloadSize = geometryType == 1 ? dimension * 8 : 4;
    ByteBuffer wkb = ByteBuffer.allocate(5 + (srid == 0 ? 0 : 4) + payloadSize).order(order);
    wkb.put(endianByte(order)).putInt((geometryType + flags) | (srid == 0 ? 0 : 0x20000000));
    if (srid != 0) wkb.putInt(srid);
    if (geometryType == 1) {
      for (int i = 0; i < dimension; i++) wkb.putDouble(Double.NaN);
    } else {
      wkb.putInt(0);
    }
    return wkb.array();
  }

  private static byte[] collectionWKB(ByteOrder order, int srid, byte[]... children) {
    int size = 9 + (srid == 0 ? 0 : 4);
    for (byte[] child : children) size += child.length;
    ByteBuffer wkb = ByteBuffer.allocate(size).order(order);
    wkb.put(endianByte(order)).putInt(7 | (srid == 0 ? 0 : 0x20000000));
    if (srid != 0) wkb.putInt(srid);
    wkb.putInt(children.length);
    for (byte[] child : children) wkb.put(child);
    return wkb.array();
  }

  private static byte endianByte(ByteOrder order) {
    return (byte) (order == ByteOrder.LITTLE_ENDIAN ? 1 : 0);
  }

  private static class DimensionAwareFactory extends PackedCoordinateSequenceFactory {
    private static final long serialVersionUID = 1L;

    @Override
    public CoordinateSequence create(int size, int dimension, int measures) {
      return new DimensionAwareSequence(size, dimension, measures);
    }
  }

  private static class DimensionAwareSequence extends CoordinateArraySequence {
    private static final long serialVersionUID = 1L;

    DimensionAwareSequence(int size, int dimension, int measures) {
      super(size, dimension, measures);
    }
  }
}
