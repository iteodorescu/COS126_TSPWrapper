import java.util.*;
import java.net.*;
import java.io.*;
import java.lang.Exception;
import java.util.regex.Pattern;
import org.json.*;

public final class StdMap {
    // helper classes
    private final class Location {
        public final double lng;
        public final double lat;

        // other constants
        private static final double MIN_LAT = -180.0;
        private static final double MAX_LAT = 180.0;
        private static final double MIN_LNG = -85.05115;
        private static final double MAX_LNG = 85;
        
        // constructors
        public Location(double ln, double la) {
            if (la < MIN_LAT || ln < MIN_LNG || la > MAX_LAT || ln > MAX_LNG) {
                
                throw new IllegalArgumentException("Coordinates exceed world bounds");
            }
            lng = process(ln);
            lat = process(la);
        }

        public boolean equals(Object that) {
            if (that instanceof Location)
                return ((Location)that).lng == lng && ((Location)that).lat == lat;
            return false;
        }

        public int hashCode() {
            return (int)Math.round(((lng - MIN_LNG) * (MAX_LAT-MIN_LAT) + lat-MIN_LAT)* 100000);
        }

        // ensure accuracy of max 5 decimals
        private double process(double value) {
            return (double)Math.round(value * 100000d) / 100000d;
        }

        public String toString() {
            return lng + "," + lat;
        }
    }


    private final class Path{
        // identifiers
        public final Location start;
        public final Location end;
        private final String pathId;

        // path stats
        private double distance; // in meters
        private double time; //in sec
        private final boolean isPossible;
        private final Location boundaryNE;
        private final Location boundarySW;


        // constructors (also perform call to API)
        public Path(Location start, Location end) {
            if (!StdMap.apiKeysSet) throw new IllegalStateException("API calls can't be made until all API keys are set");
            this.start = start;
            this.end = end;
            // start request
            String req = Request.request(getReqURL());
            JSONObject res = new JSONObject(req);
            String status = res.getString("status");

            switch(status) {
                case "OK": {
                    assert(res.has("routes"));
                    JSONObject route = res.getJSONArray("routes").getJSONObject(0);
                    JSONArray legs = route.getJSONArray("legs");
                    JSONObject bounds = route.getJSONObject("bounds");

                    // get path id
                    this.pathId = route.getJSONObject("overview_polyline").getString("points");
                    this.isPossible = true;

                    // get distance
                    this.distance = 0d;
                    this.time = 0d;
                    for (int i = 0; i < legs.length(); i++) {
                        JSONObject leg = legs.getJSONObject(i);
                        this.distance += leg.getJSONObject("distance").getInt("value");
                        this.time += leg.getJSONObject("duration").getInt("value");
                    }

                    // get bounds
                    JSONObject ne = bounds.getJSONObject("northeast");
                    JSONObject sw = bounds.getJSONObject("southwest");
                    this.boundaryNE = std.new Location(ne.getDouble("lat"), ne.getDouble("lng"));
                    this.boundarySW = std.new Location(sw.getDouble("lat"), sw.getDouble("lng"));
                    
                    return;
                }
                case "MAX_WAYPOINTS_EXCEEDED":
                case "MAX_ROUTE_LENGTH_EXCEEDED":
                case "ZERO_RESULTS": {
                    break;
                }
                case "INVALID_REQUEST":
                case "NOT_FOUND": throw new IllegalArgumentException("Invalid coordinates for the path - couldn't geocode");
                case "REQUEST_DENIED": throw new IllegalArgumentException("Directions API key is not correct");
                case "OVER_QUERY_LIMIT":
                case "OVER_DAILY_LIMIT": throw new IllegalArgumentException("Directions API key is obsolete, " +
                        "or the daily limit has been exceded.");
                default: {
                    System.err.println("Unknown error when sending an API request.");
                    break;
                }
            }
            // default  - no path between the two
            this.distance = -1;
            this.pathId = null;
            this.isPossible = false;
            this.boundaryNE = null;
            this.boundarySW = null;
        }

        public Path(double startLng, double startLat, double endLng, double endLat) {
            this(std.new Location(startLng, startLat), std.new Location(endLng, endLat));
        }

        public boolean equals(Object that) {
            if (that instanceof Path)
                return (((Path)that).start.equals(start) && ((Path)that).end.equals(end)) ||
                        (((Path)that).start.equals(end) && ((Path)that).end.equals(start));
            return false;
        }

        public int hashCode() {
            double hs = this.start.hashCode()/100000d;
            double he = this.end.hashCode()/100000d;
            return (int)Math.round(hs*he);
        }
        

        // gets the length of the available path in meters or or -1 if the path is not possible
        public double getDistance() {
            if (!this.isPossible) return -1;
            assert(this.distance >= 0);
            return distance;
        }

        // get path request url
        private String getReqURL() {
            assert(StdMap.DIRECTIONS_API_KEY != null);
            StringBuilder url = new StringBuilder(StdMap.DIRECTIONS_URL);

            url.append("?mode=");
            url.append(StdMap.mode);

            url.append("&origin=");
            url.append(start.toString());

            url.append("&destination=");
            url.append(end.toString());

            url.append("&key=");
            url.append(StdMap.DIRECTIONS_API_KEY);
            return url.toString();
        }

        public double getTime() {
            if (!this.isPossible) return -1;
            assert(this.time >= 0);
            return time;
        }

        // returns whether there is any path available or null if the path is not possible
        public boolean isPossible() {
            return isPossible;
        }

        
        // returns W, E, S, N boundaries in this order or null if the path is not possible
        // Boundaries:
        // West (lowest lng/x value), East (highest lng/x value)
        // South (lowest lat/y value), North (highest lat/y value)
        public double[] getBoundaries() {

            if (!isPossible) return null;
            assert(boundaryNE != null && boundarySW != null);
            double[] ret = new double[4];
            ret[0] = boundarySW.lng;
            ret[1] = boundaryNE.lng;
            ret[2] = boundarySW.lat;
            ret[3] = boundaryNE.lat;
            return ret;
        }
        // returns the path id if the path is possible, otherwise return null
        public String getPathId() {
            if (!isPossible) return null;
            return pathId;
        }
    }

    private static final class Request {
        public static String request(String targetURL) {
            HttpURLConnection connection = null;
    
            try {
                //Create connection
                URL url = new URL(targetURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(true);
    
                //Get Response
                InputStream is = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                return response.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    // constants
    private static final String STATIC_MAP_URL = "https://maps.googleapis.com/maps/api/staticmap";
    private static final String DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final int DEFAULT_MAP_WIDTH = 500;
    private static final int  DEFAULT_MAP_HEIGHT = 500;
    private static final int DEFAULT_INFOBOX_WIDTH = 360;
    private static final String DEFAULT_MODE = "walking";
    private static final String DEFAULT_PATH_COLOR = "0x000000";
    private static final String DEFAULT_POINT_COLOR = "0xFF0000";
    private static final int MAX_URL_CHARS = 8000; // it's actually 8192 but just in case, leaving space for the key
    private static final String[] TRANSPORTATION_MODE = new String[]{"driving", "walking", "bicycling", "transit"};

    // needs to be setup
    private static Hashtable<Location, Hashtable<Location, Path>> graph;
    private static Set<Path> visiblePaths;
    private static String STATIC_MAP_API_KEY;
    private static String DIRECTIONS_API_KEY;

    // things that could be set
    private static int canvasWidth;
    private static int canvasHeight;
    private static String mode;
    private static String pathColor;
    private static String pointColor;
    private static Location center;
    private static boolean showPoints;
    private static int zoom;
    private static boolean defaultZoom;
    private static boolean infoboxOn;
    private static int infoboxWidth;
    private static LinkedList<String> messages;
    private static int maxMessages;

    // touched only by me
    private static boolean apiKeysSet;
    private static StdMap std = new StdMap();

    // singleton pattern: client can't instantiate
    private StdMap() {
    }

    // static initializer
    static {
        init();
    }

    private static void init() {
        StdMap.graph = null;
        StdMap.visiblePaths = null;
        StdMap.STATIC_MAP_API_KEY = null;
        StdMap.DIRECTIONS_API_KEY = null;
        StdMap.canvasWidth = StdMap.DEFAULT_MAP_WIDTH;
        StdMap.canvasHeight = StdMap.DEFAULT_MAP_HEIGHT;
        StdMap.mode = StdMap.DEFAULT_MODE;
        StdMap.pathColor = StdMap.DEFAULT_PATH_COLOR;
        StdMap.pointColor = StdMap.DEFAULT_POINT_COLOR;
        StdMap.center = null;
        StdMap.zoom = 5;
        StdMap.showPoints = true;
        StdMap.apiKeysSet = false;
        StdMap.defaultZoom = true;
        StdMap.infoboxOn = false;
        StdMap.infoboxWidth = StdMap.DEFAULT_INFOBOX_WIDTH;
        StdMap.messages = new LinkedList<String>();
        StdMap.maxMessages = (StdMap.canvasHeight - 10) / 20;
    }

    public static String addMessage(String m) {
        StdMap.messages.add(m);
        String popped = null;
        while (StdMap.messages.size() > StdMap.maxMessages) popped = StdMap.messages.pop();
        return popped;
    }

    // adds point to the graph to be displayed
    // add records *possible* paths between points
    private static void addPoint(Location l) {
        if (StdMap.graph == null) StdMap.graph = new Hashtable<Location, Hashtable<Location, Path>>();
        if (StdMap.graph.containsKey(l)) return;
        Hashtable<Location, Path> s = new Hashtable<Location, Path>();
        for (Location it : StdMap.graph.keySet()) {
            Hashtable<Location, Path> sl = StdMap.graph.get(it);
            // paths might be different
            s.put(it, std.new Path(l, it));
            sl.put(l, std.new Path(it, l));
        }
        StdMap.graph.put(l, s);
    }

    // adds point to the graph to be displayed
    // add records *possible* paths between points
    public static void addPoint(double lng, double lat) {
        StdMap.addPoint(std.new Location(lng, lat));
    }

    // add to URL helper function
    private static boolean addToUrl(StringBuilder url, String s) {
        int len = url.length();
        assert(len < StdMap.MAX_URL_CHARS);
        if (len + s.length() < StdMap.MAX_URL_CHARS) {
            url.append(s);
            return true;
        }
        return false;
    }


    // makes specified path visible on the map to be created
    private static void addVisiblePath(Path p) {
        if (!p.isPossible())
            throw new IllegalArgumentException("Impossible path can't be drawn to map");
        if (!StdMap.graph.containsKey(p.start) && !StdMap.graph.containsKey(p.end))
            throw new IllegalArgumentException("Path contains endpoints that are not on the map");

        if (StdMap.visiblePaths == null) StdMap.visiblePaths = new HashSet<Path>();
        StdMap.visiblePaths.add(p);
    }


    public static void addVisiblePath(double startlng, double startlat, double endlng, double endlat) {
        StdMap.addVisiblePath(std.new Path(startlng, startlat, endlng, endlat));
    }

    // clears the map of all points in the graph and all visible paths
    public static void clear() {
        setPoints((Location[])null);
    }

    // clears all visible paths
    public static void clearPaths() {
        setVisiblePaths((Path[])null);
    }


    private static String createMapUrl() { // include API key in parameters
        assert(StdMap.graph != null);
        StringBuilder url = new StringBuilder(STATIC_MAP_URL);

        // size
        url.append("?size=");
        url.append(StdMap.canvasWidth);
        url.append("x");
        url.append(StdMap.canvasHeight);

        // add points
        if (StdMap.showPoints) {
            boolean isFirst = true;
            for (Location l : StdMap.graph.keySet()) {
                
                String opts = "&markers=size:" + (isFirst? "mid" : "tiny") + "%7Ccolor:"+ StdMap.pointColor + "%7C";
                boolean flag = addToUrl(url, opts + l.toString());
                if (!flag) break;

                if (isFirst) isFirst = false;
            }
        }

        if (StdMap.visiblePaths != null) {
            for (Path p : StdMap.visiblePaths) {
                assert(p.getPathId() != null);
                // in the url below the 80 after the color is the alpha value (transparency)
                addToUrl(url, "&path=weight:3%7Ccolor:" + StdMap.pathColor + "80%7Cenc:" + p.getPathId());
            }
        }

        url.append("&maptype=roadmap");
        if (!StdMap.defaultZoom) url.append("&zoom=" + StdMap.zoom);
        url.append("&key=");
        url.append(StdMap.STATIC_MAP_API_KEY);

        // System.out.println(url);
        return url.toString();
    }

    public static void disableDefaultZoom() {
        StdMap.defaultZoom = false;
    }

    private static void drawInfobox() {
        // get timing and distance info
        // display info
        StdDraw.setFont();
        StdDraw.setPenColor(StdDraw.BLACK);
        StdDraw.setPenRadius();
        int i = 0;
        for (String m : StdMap.messages) {
            StdDraw.textLeft(StdMap.canvasWidth + 5, StdMap.canvasHeight - 20 - 20*i, m);
            i++;
        }
    }

    public static void enableDefaultZoom() {
        StdMap.defaultZoom = true;
    }

    private static double getMapDistance(Location a, Location b) {
        if (graph.containsKey(a) && graph.get(a).containsKey(b)) {
            return graph.get(a).get(b).getDistance();
        }
        Path p = std.new Path(a, b);
        return p.getDistance();
    }

    public static double getMapDistance(double startLng, double startLat, double endLng, double endLat) {
        return StdMap.getMapDistance(std.new Location(startLng, startLat), std.new Location(endLng, endLat));
    }

    
    // this was useless :(
    // returns W, E, S, N boundaries in this order
    private static double[] getBoundaries() {
        assert(StdMap.graph != null);
        double[] boundaries = new double[4];
        boolean[] isSet = new boolean[4]; // default is false for all values

        if (StdMap.visiblePaths == null) {
            System.err.println("Paths not set");
        }
        else {
            for (Path p : StdMap.visiblePaths) {
                assert (p.isPossible());
                getBoundariesInsertHelper(boundaries, isSet, p.getBoundaries());
            }
        }

        // if it's a hamiltonian path this shouldn't be needed
        for (Location l : StdMap.graph.keySet()) {
            getBoundariesInsertHelper(boundaries, isSet, new double[]{l.lng, l.lng, l.lat, l.lat});
        }
        assert(isSet[0] && isSet[1] && isSet[2] && isSet[3]);
        if (StdMap.center != null) getBoundariesInsertHelper(boundaries, isSet, 
                                    new double[]{center.lng, center.lng, center.lat, center.lat});
        return boundaries;
    }

    // helper to compare current boundaries (boundaries[i] set iff isSet[i])
    // against a new rectangle of boundaries (temp) to be inserted
    private static void getBoundariesInsertHelper(double[] boundaries, boolean[] isSet, double[] temp) {
        // West (lowest on lng/x axis)
        if (!isSet[0] || boundaries[0] > temp[0]) {
            boundaries[0] = temp[0];
            isSet[0] = true;
        }
        // East (highest on lng/x axis)
        if (!isSet[1] || boundaries[1] < temp[1]) {
            boundaries[1] = temp[1];
            isSet[1] = true;
        }
        // South (lowest on lat/y axis)
        if (!isSet[2] || boundaries[2] > temp[2]) {
            boundaries[2] = temp[2];
            isSet[2] = true;
        }
        // North (highest on lat/y axis)
        if (!isSet[3] || boundaries[3] < temp[3]) {
            boundaries[3] = temp[3];
            isSet[3] = true;
        }
    }

    public static String getTotalDistance() {
        int dist = 0; // in meters
        for (Path p : visiblePaths) {
            dist += p.getDistance();
        }
        String output = String.format("%.1f miles", metersToMiles(dist));
        return output;
    }

    public static String getTotalTime() {
        int time = 0; // in secs
        for (Path p : visiblePaths) {
            time += p.getTime();
        }

        double mins = secsToMins(time);
        int hours = (int)Math.floor(mins/60);
        mins -= hours*60;

        String output = "";

        if (hours > 0) {
            if (hours > 1) output += (hours + " hours");
            else output += (hours + " hour");

            if (mins > 0) output += " ";
        }

        if (mins > 0) {
            if (mins == 1) output += "1 minute";
            else output += String.format("%.1f minutes", mins);
        }

        return output;
    }

    public static String getTransportationMode() {
        return StdMap.mode;
    }

    // finds the point in the center of the map boundary (or returns the one provided by the user)
    private static Location findCenter() {
        assert(StdMap.graph != null);
        if (StdMap.center != null) return StdMap.center; // was setup by user
        double[] boundaries = getBoundaries();
        return std.new Location((boundaries[0]+boundaries[1])/2.0, (boundaries[2]+boundaries[3])/2.0);
    }

    public static void hideInfobox() {
        StdMap.infoboxOn = false;
    }

    public static boolean isTransportationModeSupported(String mode) {
        String m = mode.toLowerCase();
        for (int i = 0; i < StdMap.TRANSPORTATION_MODE.length; i++)  {
            if (m.equals(StdMap.TRANSPORTATION_MODE[i])) return true;
        }
        return false;
    }

    private static double metersToMiles(int distance) {
        return Math.round(distance/1609.344*10)/10d;
    }

    // opens map using StdDraw
    // takes in Google Maps Static Maps API key as argument
    public static void openMap() {
        if (StdMap.graph == null) {
            System.err.println("Locations not set");
            return;
        }
        if (StdMap.visiblePaths == null) System.err.println("Paths not set");

        String url = createMapUrl();
        if (url == null) return;
        if(StdMap.infoboxOn) {
            StdDraw.setCanvasSize(StdMap.canvasWidth + StdMap.infoboxWidth, StdMap.canvasHeight);
            StdDraw.setXscale(0, StdMap.canvasWidth + StdMap.infoboxWidth);
        }
        else {
            StdDraw.setCanvasSize(StdMap.canvasWidth, StdMap.canvasHeight);
            StdDraw.setXscale(0, StdMap.canvasWidth);
        }
        StdDraw.setYscale(0, StdMap.canvasHeight);
        if (StdMap.infoboxOn) StdMap.drawInfobox();
        StdDraw.enableDoubleBuffering();
        StdDraw.picture(StdMap.canvasWidth/2d, StdMap.canvasHeight/2d, url, StdMap.canvasWidth, StdMap.canvasHeight);
        StdDraw.show();
        // uncomment for timing
        //System.exit(0);
    }

    // remove location from map
    private static void removePoint(Location l) {
        if(StdMap.graph == null || !StdMap.graph.containsKey(l)) return;
        for (Location it : StdMap.graph.keySet()) {
            if (it.equals(l)) continue;
            Hashtable<Location, Path> sl = StdMap.graph.get(it);
            sl.remove(l);
        }
        StdMap.graph.remove(l);
        Set<Path> save = new HashSet<Path>(StdMap.visiblePaths);
        for (Path p : save) {
            if (p.start.equals(l) || p.end.equals(l)) {
                StdMap.visiblePaths.remove(p);
            }
        }
        if (StdMap.visiblePaths.isEmpty()) StdMap.visiblePaths = null;
        if (StdMap.graph.isEmpty()) StdMap.graph = null;
    }

    public static void removePoint(double lng, double lat) {
        removePoint(std.new Location(lng, lat));
    }

    // remove path from map
    private static void removeVisiblePath(Path p) {
        if (StdMap.visiblePaths == null) return;
        StdMap.visiblePaths.remove(p);
        if (StdMap.visiblePaths.isEmpty()) StdMap.visiblePaths = null;
    }

    public static void removeVisiblePath(double startlng, double startlat, double endlng, double endlat) {
        StdMap.removeVisiblePath(std.new Path(startlng, startlat, endlng, endlat));
    }

    private static double secsToMins(int sec) {
        return Math.round(sec/60d * 10)/10d;
    }

    // set API keys is correct
    public static void setApiKeys(String staticMapKey, String directionsKey) {
        if (StdMap.apiKeysSet) throw new IllegalStateException("API keys can only be set once");
        if (!validateApiKeys(staticMapKey, directionsKey)) throw new IllegalArgumentException("API keys are incorrect");
        StdMap.STATIC_MAP_API_KEY = staticMapKey;
        StdMap.DIRECTIONS_API_KEY = directionsKey;
        StdMap.apiKeysSet = true;
    }

    
    // set map center
    private static void setMapCenter(Location l) {
        StdMap.center = l;
    }

    public static void setMapCenter(double lng, double lat) {
        setMapCenter(std.new Location(lng, lat));
    }

    public static void setZoom(int zoom) {
        if (zoom < 0 || zoom > 20) throw new IllegalArgumentException("Zoom must be a value between 0 and 20.");
        
        StdMap.disableDefaultZoom();
        
        StdMap.zoom = zoom;
    }

    // set map dimensions (on screen)
    // calling with nonpositive arguments will just leave the default options
    public static void setMapScreenSize(int width, int height) {
        StdMap.canvasWidth = width > 0? width : DEFAULT_MAP_WIDTH;
        StdMap.canvasHeight = height > 0? height : DEFAULT_MAP_HEIGHT;

        StdMap.maxMessages = (StdMap.canvasHeight - 10) / 20;
        while (StdMap.messages.size() > StdMap.maxMessages) StdMap.messages.pop();
    }

    public static void setTransportationMode(String mode) {
        if (!StdMap.isTransportationModeSupported(mode))
            throw new IllegalArgumentException("Transportation mode can be set to either" + StdMap.supportedTransportationModes()
                                                + " (where available)");
        StdMap.mode = new String(mode);
        if (graph == null) return;
        Set<Location> points = graph.keySet();
        StdMap.clear();
        StdMap.setPoints(points.toArray(new Location[0]));
    }

    public static void setPathColor(String s) {
        if (s.charAt(0) == '#') s = "0x" + s.substring(1);
        if (!Pattern.matches("^0(x|X)([0-9a-fA-F]){6}$", s))
            throw new IllegalArgumentException("Path color must be a hex number of the form 0x123ABC or #123abc");
        StdMap.pathColor = s;
    }

    public static void setPointColor(String s) {
        if (s.charAt(0) == '#') s = "0x" + s.substring(1);
        if (!Pattern.matches("^0(x|X)([0-9a-fA-F]){6}$", s))
            throw new IllegalArgumentException("Point color must be a hex number of the form 0x123ABC or #123abc");
        StdMap.pointColor = s;
    }

    // sets the points of the graph to be displayed, and erases all set visible paths
    // if there are any existing points, they will be removed
    // records *possible* paths between points
    // Note: calling with a null argument will erase all the points from the graph
    private static void setPoints(Location[] l) {
        StdMap.visiblePaths = null;
        if (l == null) {
            StdMap.graph = null;
            return;
        }
        StdMap.graph = new Hashtable<Location, Hashtable<Location, Path>>();

        for (int i = 0; i < l.length; i++) {
            // don't allow duplicates
            if (StdMap.graph.containsKey(l[i])) continue;
            StdMap.graph.put(l[i], new Hashtable<Location, Path>());
        }

        for (Location start : StdMap.graph.keySet()) {
            Hashtable<Location, Path> s = StdMap.graph.get(start);
            for (Location end : StdMap.graph.keySet()) {
                Path p = std.new Path(start, end);
                s.put(end, p);
                Hashtable<Location, Path> e = StdMap.graph.get(end);
                e.put(start, p);
            }
        }
    }

    public static void setPoints(double[][] points) {
        Location[] l = new Location[points.length];
        for (int i = 0; i < points.length; i++) {
            l[i] = std.new Location(points[i][0], points[i][1]);
        }
        StdMap.setPoints(l);
    }

    public static void setShowPoints(boolean toggle) {
        StdMap.showPoints = toggle;
    }

    // sets the visible paths of the graph to be displayed, and erases all previously set visible paths
    // Note: calling with a null argument will erase all the paths from the graph
    private static void setVisiblePaths(Path[] ps) {
        if (ps == null) StdMap.visiblePaths = null;
        else {
            StdMap.visiblePaths = new HashSet<Path>();
            for (int i = 0 ; i < ps.length; i++) {
                StdMap.visiblePaths.add(ps[i]);
            }
        }
    }

    public static void setVisiblePaths(double[][] points) {
        Path[] ps = new Path[points.length];
        for (int i = 0; i < points.length; i++) {
            ps[i] = std.new Path(points[i][0], points[i][1], points[i][2], points[i][3]);
        }
        StdMap.setVisiblePaths(ps);
    }

    public static void showInfobox() {
        StdMap.infoboxOn = true;
    }
    
    public static void showInfobox(int width) {
        StdMap.infoboxWidth = width;
        StdMap.infoboxOn = true;
    }

    public static String supportedTransportationModes() {
        StringBuilder ls = new StringBuilder();
        boolean isFirst = true;
        for (int i = 0; i < StdMap.TRANSPORTATION_MODE.length; i++)  {
            if (isFirst) isFirst = false;
            else ls.append(", ");
            ls.append(StdMap.TRANSPORTATION_MODE[i]);
        }
        return ls.toString();
    }

    // unset map center
    public static void unsetMapCenter() {
        setMapCenter(null);
    }

    // check if API keys are correct
    public static boolean validateApiKeys(String staticMapKey, String directionsKey) {
        if (staticMapKey == null || staticMapKey.length() == 0) {
            System.err.println("Static Maps API key is invalid");
            return false;
        }
        if (directionsKey == null || directionsKey.length() == 0) {
            System.err.println("Directions API key is invalid");
            return false;
        }

        try {
            Request.request("https://maps.googleapis.com/maps/api/directions/json?" +
                "mode=walking&origin=40.35025,-74.65219&destination=40.34187,%20-74.65904" +
                "&key=" + directionsKey);
        } catch (Exception e) {
            System.err.println("Directions API key is invalid, API call failed.");
            return false;
        }
        try {
            Request.request("https://maps.googleapis.com/maps/api/staticmap?" + 
                "center=Princeton,NJ&zoom=13&size=500x500&key=" + staticMapKey);
        } catch (Exception e) {
            System.err.println("Static Maps API key is invalid");
            return false;
        }
        return true;
    }

    public static int zoomIn() {
        if (StdMap.zoom < 20) StdMap.zoom++;
        return StdMap.zoom;
    }

    public static int zoomOut() {
        if (StdMap.zoom > 0) StdMap.zoom--;
        return StdMap.zoom;
    }

    // testing
    public static void main(String[] args) {
        StdMap.setApiKeys(Constants.STATIC_MAPS_API_KEY, Constants.DIRECTIONS_API_KEY);
        // // Princeton test
        // StdMap.clear();
        // StdMap.addPoint(40.35025, -74.65219); // CS Dept
        // StdMap.addPoint(40.34187, -74.65904); // Wawa
        // StdMap.addPoint(40.34720, -74.66155); // Ustore
        // StdMap.addVisiblePath(40.35025, -74.65219, 40.34187, -74.65904); // CS - W
        // StdMap.addVisiblePath(40.34187, -74.65904, 40.34720, -74.66155); // W U

        // // NYC test
        // StdMap.clear();
        // StdMap.addPoint(40.758896, -73.985130);
        // StdMap.addPoint(40.750580, -73.993584);
        // StdMap.addPoint(40.752655, -73.977295);
        // StdMap.addPoint(40.785091, -73.968285);
        // StdMap.addPoint(40.778965, -73.962311);
        // StdMap.addPoint(40.76155, -73.97711);
        // StdMap.addVisiblePath(40.758896, -73.985130, 40.750580, -73.993584);
        // StdMap.addVisiblePath(40.752655, -73.977295, 40.785091, -73.968285);
        // StdMap.addVisiblePath(40.778965, -73.962311, 40.785091, -73.968285);

        // equals test 1
        Location l1 = std.new Location(40.758896, -73.985130);
        Location l2 = std.new Location(40.758896, -73.985130);
        System.out.println("Are equal? " + (l1.equals(l2)? "yes" : "no"));

        // equals test 2
        StdMap.clear();
        // StdMap.setMapCenter(40.35025, -74.65219); // CS Dept
        StdMap.addPoint(40.750580, -73.993584);
        StdMap.addPoint(40.758896, -73.985130);
        StdMap.addPoint(40.758896, -73.985130);
        StdMap.addPoint(40.758896, -73.985130);
        StdMap.addPoint(40.758896, -73.985130);
        StdMap.addPoint(40.750580, -73.993584);
        StdMap.addPoint(40.750580, -73.993584);
        StdMap.addPoint(40.750580, -73.993584);
        StdMap.addVisiblePath(40.758896, -73.985130, 40.750580, -73.993584);
        StdMap.addVisiblePath(40.758896, -73.985130, 40.750580, -73.993584);
        StdMap.addVisiblePath(40.758896, -73.985130, 40.750580, -73.993584);
        StdMap.setShowPoints(false);
        System.out.println("Graph size " + graph.size());
        System.out.println("Path size " + visiblePaths.size());

        openMap();
    }
}

