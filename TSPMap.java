public class TSPMap {
    public static void main(String[] args) {
        if ((args.length != 1) || !(args[0].equals("s") || args[0].equals("n"))) {
            StdOut.println("This program should be called: \n$ java-introcs TSPMap n/s");
            // StdOut.println("This program should be called: \n$ java-introcs TSPMap n/s [transportation_mode]");
            return;
        }

        // if (args.length == 2) {
        //     if (StdMap.isTransportationModeSupported(args[1]))
        //         StdOut.println("This transportation mode is not supported. Supported transportation modes" + )
        // }

        boolean isSmallestHeuristic = args[0].equals("s");

        StdMap.setApiKeys(Constants.STATIC_MAPS_API_KEY, Constants.DIRECTIONS_API_KEY);
        StdMap.clear();

        int width = StdIn.readInt();
        int height = StdIn.readInt();
        StdMap.setMapScreenSize(width, height);

        Tour tour = new Tour(true);
        if (StdIn.isEmpty()) {
            StdOut.println("Empty");
            return;
        }
        while (!StdIn.isEmpty()) {
            double x = StdIn.readDouble();
            double y = StdIn.readDouble();
            String s = StdIn.readLine();

            Point p = new Point(x, y);
            StdMap.addPoint(x, y);

            if (isSmallestHeuristic) tour.insertSmallest(p);
            else tour.insertNearest(p);
        }
        String[] points = tour.toString().split("\n");

        // StdMap.setTransportationMode("transit");
        // StdMap.setTransportationMode("driving");

        double firstLng = 0;
        double firstLat = 0;
        double prevLng = 0;
        double prevLat = 0;

        for (int i = 0; i < points.length; i++) {
            String strip = points[i].trim();
            if (strip.length() == 0) continue;
            if (strip.charAt(0) != '(' || strip.charAt(strip.length()-1) != ')' || strip.indexOf(',') == -1) {
                StdOut.println("Wrong tour output");
                return;
            }
            strip = strip.substring(1, strip.length()-1);
            strip = strip.trim();
            String[] coords = strip.split(",");
            if (coords.length != 2) {
                StdOut.println("Wrong tour output");
                return;
            }
            double lng = Double.parseDouble(coords[0].trim());
            double lat = Double.parseDouble(coords[1].trim());
            StdMap.addPoint(lng, lat);
            
            if (i == 0) {
                firstLng = lng;
                firstLat = lat;
            } else {
                StdMap.addVisiblePath(prevLng, prevLat, lng, lat);
            }

            prevLng = lng;
            prevLat = lat;
        }
        StdMap.addVisiblePath(prevLng, prevLat, firstLng, firstLat);
        StdMap.openMap();

        while(true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = StdDraw.nextKeyTyped();
                
                if (key == 'i') {
                    StdMap.zoomIn();
                    StdMap.openMap();
                }
                if (key == 'o') {
                    StdMap.zoomOut();
                    StdMap.openMap();
                }
                if (key == 'm') {
                    StdMap.disableDefaultZoom();
                    StdMap.openMap();
                }
                if (key == 'd') {
                    StdMap.enableDefaultZoom();
                    StdMap.openMap();
                }
            }
        }
        
    }
}