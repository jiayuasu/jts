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
package org.locationtech.jts.geom.util;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.Assert;

/**
 * Creates structure-preserving deep copies of geometries.
 */
public final class GeometryCopier {

  private GeometryCopier() {
  }

  /**
   * Copies a geometry using a target factory.
   * Coordinate sequences are copied using the target factory's sequence factory.
   * User data is not copied.
   *
   * @param source the geometry to copy
   * @param targetFactory the factory for the copied geometry
   * @return a deep copy, or null if the source is null
   */
  public static Geometry copy(Geometry source, GeometryFactory targetFactory) {
    if (source == null) return null;
    if (source instanceof Point) {
      Point point = (Point) source;
      return targetFactory.createPoint(copy(point.getCoordinateSequence(), targetFactory));
    }
    if (source instanceof LinearRing) {
      LinearRing ring = (LinearRing) source;
      return targetFactory.createLinearRing(copy(ring.getCoordinateSequence(), targetFactory));
    }
    if (source instanceof LineString) {
      LineString line = (LineString) source;
      return targetFactory.createLineString(copy(line.getCoordinateSequence(), targetFactory));
    }
    if (source instanceof Polygon) {
      return copyPolygon((Polygon) source, targetFactory);
    }
    if (source instanceof MultiPoint) {
      Point[] points = new Point[source.getNumGeometries()];
      for (int i = 0; i < points.length; i++) {
        points[i] = (Point) copy(source.getGeometryN(i), targetFactory);
      }
      return targetFactory.createMultiPoint(points);
    }
    if (source instanceof MultiLineString) {
      LineString[] lines = new LineString[source.getNumGeometries()];
      for (int i = 0; i < lines.length; i++) {
        lines[i] = (LineString) copy(source.getGeometryN(i), targetFactory);
      }
      return targetFactory.createMultiLineString(lines);
    }
    if (source instanceof MultiPolygon) {
      Polygon[] polygons = new Polygon[source.getNumGeometries()];
      for (int i = 0; i < polygons.length; i++) {
        polygons[i] = (Polygon) copy(source.getGeometryN(i), targetFactory);
      }
      return targetFactory.createMultiPolygon(polygons);
    }
    if (source instanceof GeometryCollection) {
      Geometry[] geometries = new Geometry[source.getNumGeometries()];
      for (int i = 0; i < geometries.length; i++) {
        geometries[i] = copy(source.getGeometryN(i), targetFactory);
      }
      return targetFactory.createGeometryCollection(geometries);
    }
    Assert.shouldNeverReachHere("Unsupported Geometry class: " + source.getClass().getName());
    return null;
  }

  private static Polygon copyPolygon(Polygon source, GeometryFactory targetFactory) {
    LinearRing shell = (LinearRing) copy(source.getExteriorRing(), targetFactory);
    LinearRing[] holes = new LinearRing[source.getNumInteriorRing()];
    for (int i = 0; i < holes.length; i++) {
      holes[i] = (LinearRing) copy(source.getInteriorRingN(i), targetFactory);
    }
    return targetFactory.createPolygon(shell, holes);
  }

  private static CoordinateSequence copy(CoordinateSequence source,
      GeometryFactory targetFactory) {
    return targetFactory.getCoordinateSequenceFactory().create(source);
  }
}
