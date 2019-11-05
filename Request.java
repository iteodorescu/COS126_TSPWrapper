import java.net.*;
import java.io.*;

public class Request {
    // adapted from https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
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

    // testing
    public static void main(String[] args) {
        System.out.println("=============Start running==============");
        System.out.println(request("https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=40.737102,-73.990318&destination=40.755823,-73.986397" +
                "&key=AIzaSyB8Opr-eXvOJ9EVDqPDeIY1xuUgnTnPIro"));
        System.out.println("==============Done running==============");
    }
}
