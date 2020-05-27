package org.geotools.mif;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import static org.geotools.mif.util.MIFUtil.*;
import org.geotools.mif.util.QueueBufferedReader;

import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.util.GeometricShapeFactory;

/**
 * Read MapInfo MIF Data section (graphical objects)
 */
public class MIFDataReader implements Iterator<Geometry>, AutoCloseable {

    private static final String[] OPTIONAL_PLINE = { "PEN", "SMOOTH" };
    private static final String[] OPTIONAL_REGION = { "PEN", "BRUSH", "CENTER" };
    private static final String[] OPTIONAL_RECT = { "PEN", "BRUSH" };

    private QueueBufferedReader mif;
    private GeometryFactory gf;

    private Geometry next;
    private boolean end;

    public MIFDataReader(File mif) throws IOException {
        BufferedReader r = Files.newBufferedReader(mif.toPath(), StandardCharsets.US_ASCII);
        skipToDataSection(r);
        this.mif = new QueueBufferedReader(r);
        this.gf = JTSFactoryFinder.getGeometryFactory();
        this.end = false;
    }

    private void skipToDataSection(BufferedReader r) throws IOException {
        try {
            String line;
            do {
                line = r.readLine();
                if (line == null) {
                    throw new IOException("Could not find DATA section");
                }
            } while (!startsWithIgnoreCase(line, "DATA"));
        } catch (IOException e) {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException e1) {
                    // Ignore the close exception throw the original exception
                }
            }
            throw e;
        }
    }

    @Override
    public boolean hasNext() {
        if (next == null) {
            next = readGeometry();
        }
        return !end;
    }

    @Override
    public Geometry next() {
        Geometry geometry;
        if (next != null) {
            geometry = next;
            next = null;
        } else {
            geometry = readGeometry();
        }
        return geometry;
    }

    @Override
    public void close() {
        end = true;
        try {
            mif.close();
        } catch (IOException ignore) {
            // Do nothing
        }
        mif = null;
        gf = null;
        next = null;
    }

    private Geometry readGeometry() {
        try {
            String line;
            do {
                line = mif.poll();
                if (line == null) {
                    end = true;
                    return null;
                }
                line = line.trim();
            } while (line.isEmpty());

            if (startsWithIgnoreCase(line, "NONE")) {
                return gf.createPoint((CoordinateSequence) null);
            } else if (startsWithIgnoreCase(line, "POINT")) {
                return parsePoint(line);
            } else if (startsWithIgnoreCase(line, "LINE")) {
                return parseLine(line);
            } else if (startsWithIgnoreCase(line, "PLINE")) {
                return parsePLine(line);
            } else if (startsWithIgnoreCase(line, "REGION")) {
                return parseRegion(line);
            } else if (startsWithIgnoreCase(line, "ARC")) {
                return parseArc(line);
            } else if (startsWithIgnoreCase(line, "TEXT")) {
                return parseText(line);
            } else if (startsWithIgnoreCase(line, "RECT")
                    || startsWithIgnoreCase(line, "ROUNDRECT")
                    || startsWithIgnoreCase(line, "ELLIPSE")) {
                return parseRect(line);
            } else if (startsWithIgnoreCase(line, "MULTIPOINT")) {
                return parseMultiPoint(line);
            } else if (startsWithIgnoreCase(line, "COLLECTION")) {
                return parseGeometryCollection(line);
            } else {
                // Ignore geometry
                return null;
            }
        } catch (IOException e) {
            close();
            throw new RuntimeException(e);
        }
    }

    private Point parsePoint(String line) throws IOException {
        String[] a = line.split("\\s+");
        double x = Double.parseDouble(a[1]);
        double y = Double.parseDouble(a[2]);

        skipOptional("SYMBOL");
        return gf.createPoint(toCoordinateSequence(x, y));
    }

    private LineString parseLine(String line) throws IOException {
        String[] a = line.split("\\s+");
        double x1 = Double.parseDouble(a[1]);
        double y1 = Double.parseDouble(a[2]);
        double x2 = Double.parseDouble(a[3]);
        double y2 = Double.parseDouble(a[4]);

        skipOptional("PEN");
        return gf.createLineString(toCoordinateSequence(x1, y1, x2, y2));
    }

    private Geometry parsePLine(String line) throws IOException {
        String[] a = line.split("\\s+");

        int numLines = 1;
        if (a.length >= 2 && startsWithIgnoreCase(a[1], "MULTIPLE")) {
            numLines = Integer.parseInt(a[2]);
        }

        LineString[] lineStrings = new LineString[numLines];
        for (int i = 0; i < numLines; i++) {
            int numpts = Integer.parseInt(mif.poll().trim());
            lineStrings[i] = gf.createLineString(readCoordinatesN(numpts));
        }
        skipOptional(OPTIONAL_PLINE);
        return numLines == 1 ? lineStrings[0] : gf.createMultiLineString(lineStrings);
    }

    private Geometry parseRegion(String line) throws NumberFormatException, IOException {
        String[] a = line.split("\\s+");

        int numPolygons = Integer.parseInt(a[1]);
        Polygon[] polygons = new Polygon[numPolygons];
        for (int i = 0; i < numPolygons; i++) {
            int numpts = Integer.parseInt(mif.poll().trim());
            polygons[i] = gf.createPolygon(readCoordinatesN(numpts));
        }

        skipOptional(OPTIONAL_REGION);
        return numPolygons == 1 ? polygons[0] : gf.createMultiPolygon(polygons);
    }

    private LineString parseArc(String line) throws IOException {
        String[] arr = line.split("\\s+");
        double x1 = Double.parseDouble(arr[1]);
        double y1 = Double.parseDouble(arr[2]);
        double x2 = Double.parseDouble(arr[3]);
        double y2 = Double.parseDouble(arr[4]);
        Envelope e = new Envelope(x1, x2, y1, y2);

        arr = mif.poll().split("\\s+");
        double a = Double.parseDouble(arr[0]);
        double b = Double.parseDouble(arr[1]);

        skipOptional("PEN");

        GeometricShapeFactory gsf = new GeometricShapeFactory(gf);
        gsf.setEnvelope(e);
        return gsf.createArc(a, b);
    }

    private Point parseText(String line) throws IOException {
        // Skip TEXT line
        line = mif.poll();

        String[] a = line.split("\\s+");
        double x1 = Double.parseDouble(a[1]);
        double y1 = Double.parseDouble(a[2]);
        // double x2 = Double.parseDouble(a[3]);
        // double y2 = Double.parseDouble(a[4]);

        skipOptional(new String[] { "FONT", "SPACING", "JUSTIFY", "ANGLE", "LABEL" });

        return gf.createPoint(toCoordinateSequence(x1, y1));
    }

    private Polygon parseRect(String line) throws IOException {
        String[] a = line.split("\\s+");
        double x1 = Double.parseDouble(a[1]);
        double y1 = Double.parseDouble(a[2]);
        double x2 = Double.parseDouble(a[3]);
        double y2 = Double.parseDouble(a[4]);

        if (startsWithIgnoreCase(a[0], "ROUNDRECT")) {
            mif.poll(); // Skip the degree of rounding (a)
        }

        skipOptional(OPTIONAL_RECT);
        return JTS.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    private Geometry parseMultiPoint(String line) throws IOException {
        String[] a = line.split("\\s+");
        int numPoints = Integer.parseInt(a[1]);

        Point[] points = new Point[numPoints];
        for (int i = 0; i < numPoints; i++) {
            a = mif.poll().split("\\s+");
            points[i] = gf.createPoint(readCoordinatesN(1));
        }

        skipOptional("SYMBOL");

        return numPoints == 1 ? points[0] : gf.createMultiPoint(points);
    }

    private GeometryCollection parseGeometryCollection(String line) throws IOException {
        int n = Integer.parseInt(line.substring("COLLECTION".length()));
        Geometry[] geometries = new Geometry[n];
        for (int i = 0; i < n; i++) {
            geometries[i] = readGeometry();
        }
        return gf.createGeometryCollection(geometries);
    }

    private CoordinateSequence readCoordinatesN(int n) throws IOException {
        CoordinateSequence csq = gf.getCoordinateSequenceFactory().create(n, 2);
        for (int i = 0; i < n; i++) {
            String[] a = mif.poll().split("\\s+");
            double x = Double.parseDouble(a[0]);
            double y = Double.parseDouble(a[1]);
            csq.setOrdinate(i, 0, x);
            csq.setOrdinate(i, 1, y);
        }
        return csq;
    }

    private void skipOptional(String s) throws IOException {
        String line = mif.peek();
        if (!startsWithIgnoreCase(line, s)) {
            mif.poll();
        }
    }

    private void skipOptional(String[] a) throws IOException {
        String line = mif.peek();
        if (line == null) {
            return;
        }
        line = line.trim();
        for (String s : a) {
            if (startsWithIgnoreCase(line, s)) {
                mif.poll();
                line = mif.peek();
                if (line == null) {
                    return;
                }
                line = line.trim();
            }
        }
    }

    private CoordinateSequence toCoordinateSequence(double x, double y) {
        CoordinateSequence csq = gf.getCoordinateSequenceFactory().create(1, 2);
        csq.setOrdinate(0, 0, x);
        csq.setOrdinate(0, 1, y);
        return csq;
    }

    private CoordinateSequence toCoordinateSequence(double x1, double y1, double x2, double y2) {
        CoordinateSequence csq = gf.getCoordinateSequenceFactory().create(2, 2);
        csq.setOrdinate(0, 0, x1);
        csq.setOrdinate(0, 1, y1);
        csq.setOrdinate(1, 0, x2);
        csq.setOrdinate(1, 1, y2);
        return csq;
    }

}