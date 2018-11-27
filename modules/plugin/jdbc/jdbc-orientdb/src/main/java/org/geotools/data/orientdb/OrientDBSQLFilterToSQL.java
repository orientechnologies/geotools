/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.data.orientdb;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LinearRing;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.DistanceBufferOperator;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;

import java.io.IOException;
import org.opengis.filter.spatial.Crosses;

public class OrientDBSQLFilterToSQL extends FilterToSQL {

    public OrientDBSQLFilterToSQL() {
        super();
    }

    @Override
    protected FilterCapabilities createFilterCapabilities() {
        FilterCapabilities caps = super.createFilterCapabilities();
        caps.addType(BBOX.class);
        caps.addType(Contains.class);
        //caps.addType(Crosses.class);
        caps.addType(Disjoint.class);
        caps.addType(Equals.class);
        caps.addType(Intersects.class);
        caps.addType(Overlaps.class);
        caps.addType(Touches.class);
        caps.addType(Within.class);
        caps.addType(Beyond.class);

        return caps;
    }

    private static void clampLongitude(Coordinate coordinate) {
        if (coordinate.x > 180.) {
            coordinate.x = 180.;
        }
        if (coordinate.x < -180.) {
            coordinate.x = -180.;
        }
    }

    private static void clampLattitude(Coordinate coordinate) {
        if (coordinate.y > 90.) {
            coordinate.y = 90.;
        }
        if (coordinate.y < -90.) {
            coordinate.y = -90.;
        }
    }

    private static void clampCoordinates(Geometry g) {
        Coordinate[] coordinates = g.getCoordinates();
        for (Coordinate coordinate : coordinates) {
            clampLongitude(coordinate);
            clampLattitude(coordinate);
        }
    }

    @Override
    protected void visitLiteralGeometry(Literal expression) throws IOException {
        Geometry g = (Geometry) evaluateLiteral(expression, Geometry.class);
        clampCoordinates(g);
        if (g instanceof LinearRing) {
            //WKT does not support linear rings
            g = g.getFactory().createLineString(((LinearRing) g).getCoordinateSequence());
        }
        out.write("ST_GeomFromText('" + g.toText() + "', " + currentSRID + ")");                
    }

    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter,
            PropertyName property, Literal geometry, boolean swapped, Object extraData) {

        return visitBinarySpatialOperatorEnhanced(filter, (Expression) property, (Expression) geometry,
                swapped, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter, Expression e1,
            Expression e2, Object extraData) {
        return visitBinarySpatialOperatorEnhanced(filter, e1, e2, false, extraData);
    }

    /**
     *
     * @param filter
     * @param e1
     * @param e2
     * @param swapped
     * @param extraData
     * @return
     */
    protected Object visitBinarySpatialOperatorEnhanced(BinarySpatialOperator filter, Expression e1,
            Expression e2, boolean swapped, Object extraData) {

        try {

            if (filter instanceof DistanceBufferOperator) {
                out.write("ST_Distance(");
                e1.accept(this, extraData);
                out.write(", ");
                e2.accept(this, extraData);
                out.write(")");

                if (filter instanceof DWithin) {
                    out.write("<");
                } else if (filter instanceof Beyond) {
                    out.write(">");
                } else {
                    throw new RuntimeException("Unknown distance operator");
                }
                out.write(Double.toString(((DistanceBufferOperator) filter).getDistance()));
            } else if (filter instanceof BBOX) {
                out.write("ST_Intersects(");
                e1.accept(this, extraData);
                out.write(",");
                e2.accept(this, extraData);
                out.write(") = true");
            } else {
                boolean equalsTrueNecessary = true;
              
                if (filter instanceof Contains) {
                    out.write("ST_Contains(");
//                } else if (filter instanceof Crosses) {
//                    out.write("ST_Crosses(");
                } else if (filter instanceof Disjoint) {
                    out.write("ST_Disjoint(");
                } else if (filter instanceof Equals) {
                    out.write("ST_Equals(");
                } else if (filter instanceof Intersects) {
                    out.write("ST_Intersects(");
                } else if (filter instanceof Overlaps) {
                    out.write("ST_Overlaps(");
//                } else if (filter instanceof Touches) {                  
//                    out.write("ST_Touches(");
                } else if (filter instanceof Within) {
                    out.write("ST_Within(");
                } else {
                    throw new RuntimeException("Unknown operator: " + filter);
                }

                if (swapped) {
                    e2.accept(this, extraData);
                    out.write(", ");
                    e1.accept(this, extraData);
                } else {
                    e1.accept(this, extraData);
                    out.write(", ");
                    e2.accept(this, extraData);
                }

                out.write(")");
                if (equalsTrueNecessary){
                  out.write(" = true");
                }
                                
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return extraData;
    }
}
