/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jts.geom;

import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.geom.util.GeometryCopier;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.util.AssertionFailedException;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;


/**
 * Tests for {@link GeometryFactory}.
 *
 * @version 1.13
 */
public class GeometryFactoryTest extends GeometryTestCase {

  PrecisionModel precisionModel = new PrecisionModel();
  GeometryFactory geometryFactory = new GeometryFactory(precisionModel, 0);
  WKTReader reader = new WKTReader(geometryFactory);

  public static void main(String args[]) {
    TestRunner.run(GeometryFactoryTest.class);
  }

  public GeometryFactoryTest(String name) { super(name); }

  public void testCreateGeometry()
  {
    checkCreateGeometryExact("POINT ( 10 20 )");
    checkCreateGeometryExact("MULTIPOINT ( (10 20), (30 40) )");
    checkCreateGeometryExact("LINESTRING(0 0, 10 10)");
    checkCreateGeometryExact("MULTILINESTRING ((50 100, 100 200), (100 100, 150 200))");
    checkCreateGeometryExact("POLYGON ((100 200, 200 200, 200 100, 100 100, 100 200))");
    checkCreateGeometryExact("MULTIPOLYGON (((100 200, 200 200, 200 100, 100 100, 100 200)), ((300 200, 400 200, 400 100, 300 100, 300 200)))");
    checkCreateGeometryExact("GEOMETRYCOLLECTION (POLYGON ((100 200, 200 200, 200 100, 100 100, 100 200)), LINESTRING (250 100, 350 200), POINT (350 150))");
  }
  
  public void testCreateGeometryEmpty() {
    checkCreateGeometryExact("POINT EMPTY");
    checkCreateGeometryExact("LINESTRING EMPTY");
    checkCreateGeometryExact("POLYGON EMPTY");
    checkCreateGeometryExact("MULTIPOINT EMPTY");
    checkCreateGeometryExact("MULTILINESTRING EMPTY");
    checkCreateGeometryExact("MULTIPOLYGON EMPTY");
    checkCreateGeometryExact("GEOMETRYCOLLECTION EMPTY");
  }
  
  public void testCreateEmpty() {
    checkEmpty( geometryFactory.createEmpty(0), Point.class);
    checkEmpty( geometryFactory.createEmpty(1), LineString.class);
    checkEmpty( geometryFactory.createEmpty(2), Polygon.class);
    
    checkEmpty( geometryFactory.createPoint(), Point.class);
    checkEmpty( geometryFactory.createLineString(), LineString.class);
    checkEmpty( geometryFactory.createPolygon(), Polygon.class);
    
    checkEmpty( geometryFactory.createMultiPoint(), MultiPoint.class);
    checkEmpty( geometryFactory.createMultiLineString(), MultiLineString.class);
    checkEmpty( geometryFactory.createMultiPolygon(), MultiPolygon.class);
    checkEmpty( geometryFactory.createGeometryCollection(), GeometryCollection.class);
  }
  
  private void checkEmpty(Geometry geom, Class clz) {
    assertTrue(geom.isEmpty());
    assertTrue( geom.getClass() == clz );
  }

  public void testDeepCopy() 
  {
    Point g = (Point) read("POINT ( 10 10) ");
    Geometry g2 = geometryFactory.createGeometry(g);
    g.getCoordinateSequence().setOrdinate(0, 0, 99);
    assertTrue(! g.equalsExact(g2));
  }
  
  public void testMultiPointCS()
  {
    GeometryFactory gf = new GeometryFactory(new PackedCoordinateSequenceFactory());
    CoordinateSequence mpSeq = gf.getCoordinateSequenceFactory().create(1, 4);
    mpSeq.setOrdinate(0, 0, 50);
    mpSeq.setOrdinate(0, 1, -2);
    mpSeq.setOrdinate(0, 2, 10);
    mpSeq.setOrdinate(0, 3, 20);
    
    MultiPoint mp = gf.createMultiPoint(mpSeq);
    CoordinateSequence pSeq = ((Point)mp.getGeometryN(0)).getCoordinateSequence();
    assertEquals(4, pSeq.getDimension());
    for (int i = 0; i < 4; i++)
      assertEquals(mpSeq.getOrdinate(0, i), pSeq.getOrdinate(0, i));
  }
  
  /**
     * CoordinateArraySequences default their dimension to 3 unless explicitly told otherwise.
     * This test ensures that GeometryFactory.createGeometry() recreates the input dimension properly.
   * 
   * @throws ParseException
   */
  public void testCopyGeometryWithNonDefaultDimension() 
  {
    GeometryFactory gf = new GeometryFactory(CoordinateArraySequenceFactory.instance());
    CoordinateSequence mpSeq = gf.getCoordinateSequenceFactory().create(1, 2);
    mpSeq.setOrdinate(0, 0, 50);
    mpSeq.setOrdinate(0, 1, -2);
    
    Point g = gf.createPoint(mpSeq);
    CoordinateSequence pSeq = ((Point) g.getGeometryN(0)).getCoordinateSequence();
    assertEquals(2, pSeq.getDimension());
    
    Point g2 = (Point) geometryFactory.createGeometry(g);
    assertEquals(2, g2.getCoordinateSequence().getDimension());

  }

  public void testCreateGeometryPreservesEmptyCollectionMembers() {
    GeometryFactory sourceFactory = new GeometryFactory(
        PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    Point emptyPoint = sourceFactory.createPoint(
        sourceFactory.getCoordinateSequenceFactory().create(0, 3, 0));
    LineString emptyLine = sourceFactory.createLineString(
        sourceFactory.getCoordinateSequenceFactory().create(0, 3, 1));
    Polygon emptyPolygon = sourceFactory.createPolygon(
        sourceFactory.createLinearRing(
            sourceFactory.getCoordinateSequenceFactory().create(0, 4, 1)),
        new LinearRing[] {
            sourceFactory.createLinearRing(
                sourceFactory.getCoordinateSequenceFactory().create(0, 3, 0))
        });
    MultiPoint points = sourceFactory.createMultiPoint(new Point[] {
        sourceFactory.createPoint(new Coordinate(1, 2)), emptyPoint
    });
    MultiLineString lines = sourceFactory.createMultiLineString(new LineString[] {
        sourceFactory.createLineString(new Coordinate[] {
            new Coordinate(1, 2), new Coordinate(3, 4)
        }), emptyLine
    });
    MultiPolygon polygons = sourceFactory.createMultiPolygon(new Polygon[] {
        sourceFactory.createPolygon(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1),
            new Coordinate(0, 0)
        }), emptyPolygon
    });
    GeometryCollection source = sourceFactory.createGeometryCollection(new Geometry[] {
        points, lines, polygons,
        sourceFactory.createGeometryCollection(new Geometry[] {emptyPoint, emptyPolygon})
    });

    GeometryFactory targetFactory = new GeometryFactory(new PrecisionModel(), 3857,
        CoordinateArraySequenceFactory.instance());
    GeometryCollection copy = (GeometryCollection) targetFactory.createGeometry(source);
    assertFactory(copy, targetFactory);
    assertEquals(4, copy.getNumGeometries());
    assertEquals(2, copy.getGeometryN(0).getNumGeometries());
    assertEquals(2, copy.getGeometryN(1).getNumGeometries());
    assertEquals(2, copy.getGeometryN(2).getNumGeometries());
    assertEquals(2, copy.getGeometryN(3).getNumGeometries());
    Polygon copiedEmpty = (Polygon) copy.getGeometryN(2).getGeometryN(1);
    assertTrue(copiedEmpty.isEmpty());
    assertEquals(1, copiedEmpty.getNumInteriorRing());
  }

  public void testCreateGeometryPreservesCoordinateLayoutsAndStorageIndependence() {
    GeometryFactory sourceFactory = new GeometryFactory(new PrecisionModel(), 4326,
        PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    CoordinateSequence shellSequence = sequence(sourceFactory, 3, 1, new double[][] {
        {0, 0, 1}, {0, 1, 2}, {1, 1, 3}, {0, 0, 1}
    });
    CoordinateSequence holeSequence = sequence(sourceFactory, 4, 1, new double[][] {
        {0.1, 0.1, 4, 5}, {0.1, 0.2, 4, 6}, {0.2, 0.1, 4, 7}, {0.1, 0.1, 4, 5}
    });
    Polygon source = sourceFactory.createPolygon(
        sourceFactory.createLinearRing(shellSequence),
        new LinearRing[] {sourceFactory.createLinearRing(holeSequence)});
    source.setUserData("not copied");
    GeometryFactory targetFactory = new GeometryFactory(new PrecisionModel(), 3857,
        CoordinateArraySequenceFactory.instance());

    Polygon copy = (Polygon) GeometryCopier.copy(source, targetFactory);
    assertSame(targetFactory, copy.getFactory());
    assertEquals(3857, copy.getSRID());
    assertNull(copy.getUserData());
    assertEquals(3, copy.getExteriorRing().getCoordinateSequence().getDimension());
    assertEquals(1, copy.getExteriorRing().getCoordinateSequence().getMeasures());
    assertEquals(4, copy.getInteriorRingN(0).getCoordinateSequence().getDimension());
    assertEquals(1, copy.getInteriorRingN(0).getCoordinateSequence().getMeasures());

    shellSequence.setOrdinate(0, 0, 99);
    assertEquals(0.0, copy.getExteriorRing().getCoordinateN(0).x);
    copy.getExteriorRing().getCoordinateSequence().setOrdinate(0, 1, 88);
    assertEquals(0.0, shellSequence.getOrdinate(0, 1));
  }

  public void testCreateGeometryNull() {
    assertNull(geometryFactory.createGeometry(null));
    assertNull(GeometryCopier.copy(null, geometryFactory));
  }

  public void testGeometryCopierRejectsUnsupportedGeometrySubclass() {
    try {
      GeometryCopier.copy(new UnsupportedGeometry(geometryFactory), geometryFactory);
      fail("Expected unsupported geometry assertion");
    } catch (AssertionFailedException expected) {
      assertTrue(expected.getMessage().contains("Unsupported Geometry class"));
    }
  }

  private CoordinateSequence sequence(GeometryFactory factory, int dimension, int measures,
      double[][] ordinates) {
    CoordinateSequence sequence = factory.getCoordinateSequenceFactory()
        .create(ordinates.length, dimension, measures);
    for (int i = 0; i < ordinates.length; i++) {
      for (int ordinate = 0; ordinate < ordinates[i].length; ordinate++) {
        sequence.setOrdinate(i, ordinate, ordinates[i][ordinate]);
      }
    }
    return sequence;
  }

  private void assertFactory(Geometry geometry, GeometryFactory factory) {
    assertSame(factory, geometry.getFactory());
    assertEquals(factory.getSRID(), geometry.getSRID());
    if (geometry instanceof GeometryCollection) {
      for (int i = 0; i < geometry.getNumGeometries(); i++) {
        assertFactory(geometry.getGeometryN(i), factory);
      }
    }
  }

  private static class UnsupportedGeometry extends Geometry {
    UnsupportedGeometry(GeometryFactory factory) {
      super(factory);
    }

    public String getGeometryType() { return "Unsupported"; }
    protected int getTypeCode() { return -1; }
    public Coordinate getCoordinate() { return null; }
    public Coordinate[] getCoordinates() { return new Coordinate[0]; }
    public int getNumPoints() { return 0; }
    public boolean isEmpty() { return true; }
    public int getDimension() { return Dimension.FALSE; }
    public Geometry getBoundary() { return null; }
    public int getBoundaryDimension() { return Dimension.FALSE; }
    protected Geometry reverseInternal() { return this; }
    public boolean equalsExact(Geometry other, double tolerance) { return other == this; }
    public void apply(CoordinateFilter filter) { }
    public void apply(CoordinateSequenceFilter filter) { }
    public void apply(GeometryFilter filter) { }
    public void apply(GeometryComponentFilter filter) { }
    protected Geometry copyInternal() { return new UnsupportedGeometry(getFactory()); }
    public void normalize() { }
    protected Envelope computeEnvelopeInternal() { return new Envelope(); }
    protected int compareToSameClass(Object o) { return 0; }
    protected int compareToSameClass(Object o, CoordinateSequenceComparator comp) { return 0; }
  }
  
  private void checkCreateGeometryExact(String wkt) 
  {
    Geometry g = read(wkt);
    Geometry g2 = geometryFactory.createGeometry(g);
    assertTrue(g.equalsExact(g2));
    // check a copy has been made
    assertTrue(g != g2);
  }
  
}
