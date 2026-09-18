/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.locationtech.jts.geom.impl;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;

/**
 * Coordinates whose layout was explicitly declared by a binary geometry header.
 * The declaration is retained by {@link #copy()}, including for empty sequences
 * or sequences whose Z or M ordinates are all NaN.
 * Ordinary padded Coordinate values do not by themselves declare a layout.
 *
 * <p>Adapted from Apache Sedona to expose sequence construction to other binary codecs.
 */
public final class DeclaredCoordinateSequence extends CoordinateArraySequence {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a declared sequence backed by the supplied array, which is not copied.
   * Coordinates must support the requested dimension and measures.
   *
   * @param coordinates the coordinates, or null for an empty sequence
   * @param dimension the total number of ordinates per coordinate
   * @param measures the number of measure ordinates
   */
  public DeclaredCoordinateSequence(Coordinate[] coordinates, int dimension, int measures) {
    super(coordinates, dimension, measures);
  }

  /**
   * Creates a declared sequence with newly allocated coordinates.
   *
   * @param size the number of coordinates
   * @param dimension the total number of ordinates per coordinate
   * @param measures the number of measure ordinates
   */
  public DeclaredCoordinateSequence(int size, int dimension, int measures) {
    super(size, dimension, measures);
  }

  private DeclaredCoordinateSequence(CoordinateSequence sequence) {
    super(sequence);
  }

  @Override
  public DeclaredCoordinateSequence copy() {
    return new DeclaredCoordinateSequence(this);
  }
}
