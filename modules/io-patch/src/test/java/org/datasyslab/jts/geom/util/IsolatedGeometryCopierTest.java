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
package org.datasyslab.jts.geom.util;

import junit.framework.TestCase;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceComparator;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryComponentFilter;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.GeometryFilter;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.util.AssertionFailedException;

public class IsolatedGeometryCopierTest extends TestCase {
  private final GeometryFactory sourceFactory = new GeometryFactory(new PrecisionModel(), 4326,
      PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
  private final GeometryFactory targetFactory = new GeometryFactory(new PrecisionModel(10), 3857,
      CoordinateArraySequenceFactory.instance());

  public void testPublicApiUsesStockJtsTypes() throws Exception {
    assertSame(Geometry.class,
        GeometryCopier.class.getMethod("copy", Geometry.class, GeometryFactory.class).getReturnType());
    assertEquals("org.locationtech.jts.geom.Point", targetFactory.createPoint().getClass().getName());
  }

  public void testNestedAndMultipartEmptyMembersArePreserved() throws ParseException {
    Geometry source = new WKTReader(sourceFactory).read(
        "GEOMETRYCOLLECTION (POINT EMPTY, MULTIPOINT (EMPTY, (1 2), EMPTY), "
        + "MULTILINESTRING (EMPTY, (0 0, 1 1)), "
        + "MULTIPOLYGON (EMPTY, ((0 0, 4 0, 4 4, 0 0))), "
        + "GEOMETRYCOLLECTION (POINT EMPTY, LINESTRING EMPTY, GEOMETRYCOLLECTION EMPTY))");
    source.apply((GeometryComponentFilter) geometry -> geometry.setUserData("source metadata"));
    Geometry copy = GeometryCopier.copy(source, targetFactory);

    assertCopy(source, copy);
    assertEquals(5, copy.getNumGeometries());
    assertEquals(3, copy.getGeometryN(1).getNumGeometries());
    assertEquals(2, copy.getGeometryN(2).getNumGeometries());
    assertEquals(2, copy.getGeometryN(3).getNumGeometries());
    assertEquals(3, copy.getGeometryN(4).getNumGeometries());
    assertEquals("source metadata", source.getUserData());
  }

  public void testAllEmptyMultipartGeometriesKeepMembers() throws ParseException {
    for (String wkt : new String[] {
        "MULTIPOINT (EMPTY, EMPTY)",
        "MULTILINESTRING (EMPTY, EMPTY)",
        "MULTIPOLYGON (EMPTY, EMPTY)"
    }) {
      Geometry source = new WKTReader(sourceFactory).read(wkt);
      Geometry copy = GeometryCopier.copy(source, targetFactory);

      assertTrue(copy.isEmpty());
      assertEquals(2, copy.getNumGeometries());
      assertCopy(source, copy);
    }
  }

  public void testEmptyPolygonIsCopiedIntoTargetFactory() {
    Polygon source = sourceFactory.createPolygon();
    source.setUserData("source metadata");

    Polygon copy = (Polygon) GeometryCopier.copy(source, targetFactory);

    assertTrue(copy.isEmpty());
    assertNotSame(source, copy);
    assertSame(targetFactory, copy.getFactory());
    assertEquals(3857, copy.getSRID());
    assertEquals(4326, source.getSRID());
    assertEquals("source metadata", source.getUserData());
    assertNull(copy.getUserData());
  }

  public void testEmptyShellsAndHolesKeepPackedLayouts() {
    for (int dimension : new int[] {3, 4}) {
      Polygon source = sourceFactory.createPolygon(ring(dimension, true), new LinearRing[] {
          ring(dimension, true), ring(dimension, true)
      });
      Polygon copy = (Polygon) GeometryCopier.copy(source, targetFactory);

      assertCopy(source, copy);
      assertTrue(copy.isEmpty());
      assertEquals(2, copy.getNumInteriorRing());
      assertEquals(dimension, copy.getExteriorRing().getCoordinateSequence().getDimension());
      assertEquals(1, copy.getExteriorRing().getCoordinateSequence().getMeasures());
    }
  }

  public void testPopulatedShellsAndEmptyHolesKeepPackedLayouts() {
    for (int dimension : new int[] {3, 4}) {
      Polygon source = sourceFactory.createPolygon(ring(dimension, false), new LinearRing[] {
          ring(dimension, true), ring(dimension, false), ring(dimension, true)
      });
      Polygon copy = (Polygon) GeometryCopier.copy(source, targetFactory);

      assertCopy(source, copy);
      assertEquals(3, copy.getNumInteriorRing());
      assertTrue(copy.getInteriorRingN(0).isEmpty());
      assertFalse(copy.getInteriorRingN(1).isEmpty());
      assertTrue(copy.getInteriorRingN(2).isEmpty());
    }
  }

  public void testCopiesCoordinatesWithoutRoundingOrSharingStorage() {
    CoordinateSequence coordinates = sourceFactory.getCoordinateSequenceFactory().create(1, 4, 1);
    coordinates.setOrdinate(0, 0, 1.25);
    coordinates.setOrdinate(0, 1, 2.75);
    coordinates.setOrdinate(0, 2, 3.5);
    coordinates.setOrdinate(0, 3, 4.5);
    Point source = sourceFactory.createPoint(coordinates);
    Object metadata = new Object();
    source.setUserData(metadata);
    Point copy = (Point) GeometryCopier.copy(source, targetFactory);

    assertCopy(source, copy);
    assertEquals(1.25, copy.getX());
    assertEquals(2.75, copy.getY());
    assertSame(metadata, source.getUserData());
    copy.getCoordinateSequence().setOrdinate(0, 0, 99);
    copy.getCoordinateSequence().setOrdinate(0, 3, 88);
    assertEquals(1.25, coordinates.getX(0));
    assertEquals(4.5, coordinates.getM(0));
    coordinates.setOrdinate(0, 1, 77);
    assertEquals(2.75, copy.getY());
    assertEquals(4326, source.getSRID());
  }

  public void testNullSourceReturnsNull() {
    assertNull(GeometryCopier.copy(null, targetFactory));
  }

  public void testUnsupportedGeometryRetainsAssertionContract() {
    try {
      GeometryCopier.copy(new UnsupportedGeometry(sourceFactory), targetFactory);
      fail("Expected unsupported geometry to fail");
    } catch (AssertionFailedException expected) {
      assertTrue(expected.getMessage().contains("Unsupported Geometry class"));
    }
  }

  private LinearRing ring(int dimension, boolean empty) {
    CoordinateSequence coordinates = sourceFactory.getCoordinateSequenceFactory()
        .create(empty ? 0 : 4, dimension, 1);
    if (!empty) {
      double[][] xy = {{0, 0}, {4, 0}, {4, 4}, {0, 0}};
      for (int i = 0; i < xy.length; i++) {
        coordinates.setOrdinate(i, 0, xy[i][0]);
        coordinates.setOrdinate(i, 1, xy[i][1]);
        for (int ordinate = 2; ordinate < dimension; ordinate++) {
          coordinates.setOrdinate(i, ordinate, ordinate * 10 + (i == 3 ? 0 : i));
        }
      }
    }
    return sourceFactory.createLinearRing(coordinates);
  }

  private void assertCopy(Geometry source, Geometry copy) {
    assertNotSame(source, copy);
    assertEquals(source.getClass(), copy.getClass());
    assertSame(targetFactory, copy.getFactory());
    assertEquals(3857, copy.getSRID());
    assertEquals(source.isEmpty(), copy.isEmpty());
    assertNull(copy.getUserData());
    if (source instanceof Point) {
      assertSequenceCopy(((Point) source).getCoordinateSequence(),
          ((Point) copy).getCoordinateSequence());
    } else if (source instanceof LineString) {
      assertSequenceCopy(((LineString) source).getCoordinateSequence(),
          ((LineString) copy).getCoordinateSequence());
    } else if (source instanceof Polygon) {
      Polygon sourcePolygon = (Polygon) source;
      Polygon copyPolygon = (Polygon) copy;
      assertCopy(sourcePolygon.getExteriorRing(), copyPolygon.getExteriorRing());
      assertEquals(sourcePolygon.getNumInteriorRing(), copyPolygon.getNumInteriorRing());
      for (int i = 0; i < sourcePolygon.getNumInteriorRing(); i++) {
        assertCopy(sourcePolygon.getInteriorRingN(i), copyPolygon.getInteriorRingN(i));
      }
    } else if (source instanceof GeometryCollection) {
      assertEquals(source.getNumGeometries(), copy.getNumGeometries());
      for (int i = 0; i < source.getNumGeometries(); i++) {
        assertCopy(source.getGeometryN(i), copy.getGeometryN(i));
      }
    }
  }

  private void assertSequenceCopy(CoordinateSequence source, CoordinateSequence copy) {
    assertNotSame(source, copy);
    assertTrue(copy instanceof CoordinateArraySequence);
    assertEquals(source.size(), copy.size());
    assertEquals(source.getDimension(), copy.getDimension());
    assertEquals(source.getMeasures(), copy.getMeasures());
    for (int i = 0; i < source.size(); i++) {
      for (int ordinate = 0; ordinate < source.getDimension(); ordinate++) {
        assertEquals(source.getOrdinate(i, ordinate), copy.getOrdinate(i, ordinate), 0);
      }
    }
  }

  private static class UnsupportedGeometry extends Geometry {
    private static final long serialVersionUID = 1L;

    UnsupportedGeometry(GeometryFactory factory) { super(factory); }
    @Override public String getGeometryType() { return "UnsupportedGeometry"; }
    @Override public Coordinate getCoordinate() { throw new UnsupportedOperationException(); }
    @Override public Coordinate[] getCoordinates() { throw new UnsupportedOperationException(); }
    @Override public int getNumPoints() { throw new UnsupportedOperationException(); }
    @Override public boolean isEmpty() { throw new UnsupportedOperationException(); }
    @Override public int getDimension() { throw new UnsupportedOperationException(); }
    @Override public Geometry getBoundary() { throw new UnsupportedOperationException(); }
    @Override public int getBoundaryDimension() { throw new UnsupportedOperationException(); }
    @Override protected Geometry reverseInternal() { throw new UnsupportedOperationException(); }
    @Override public boolean equalsExact(Geometry geometry, double tolerance) {
      throw new UnsupportedOperationException();
    }
    @Override public void apply(CoordinateFilter filter) { throw new UnsupportedOperationException(); }
    @Override public void apply(CoordinateSequenceFilter filter) {
      throw new UnsupportedOperationException();
    }
    @Override public void apply(GeometryFilter filter) { throw new UnsupportedOperationException(); }
    @Override public void apply(GeometryComponentFilter filter) {
      throw new UnsupportedOperationException();
    }
    @Override protected Geometry copyInternal() { throw new UnsupportedOperationException(); }
    @Override public void normalize() { throw new UnsupportedOperationException(); }
    @Override protected Envelope computeEnvelopeInternal() { throw new UnsupportedOperationException(); }
    @Override protected int compareToSameClass(Object geometry) {
      throw new UnsupportedOperationException();
    }
    @Override protected int compareToSameClass(Object geometry, CoordinateSequenceComparator comparator) {
      throw new UnsupportedOperationException();
    }
    @Override protected int getTypeCode() { return -1; }
  }
}
