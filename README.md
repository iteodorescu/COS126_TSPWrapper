
I created this wrapper for the Princeton University's COS 126 [Travelling Salesperson Problem assignment](https://www.cs.princeton.edu/courses/archive/fall19/cos126/assignments/tsp/).
Follow these steps to setup:

## Get Google Maps API keys
From [Google Maps API Java documentation](https://github.com/googlemaps/google-maps-services-java):
>Each Google Maps Web Service request requires an API key or client ID. API keys are freely available with a Google Account at [developers.google.com/console](developers.google.com/console). The type of API key you need is a Server key.
>To get an API key, visit developers.google.com/console and log in with a Google Account. Create a new project and enable the API(s) you want to use.

For this project you will want to get keys for:
- Directions API
- Maps Static API

>Create a new Server key.
>Important: This key should be kept secret on your server.

## Create Constants file: 
Use your created Google Maps API keys, and create a Constants.java file with the following code:.
```
public class Constants {
    public static final String DIRECTIONS_API_KEY = [YOUR GOOGLE MAPS DIRECTIONS API KEY HERE];
    public static final String STATIC_MAPS_API_KEY = [YOUR GOOGLE MAPS STATIC MAPS API KEY HERE];

    public static void main(String[] args) {
        System.out.println("This is the private API constants file.");
    }
}
```

## Download org.json
This project requires some JSON parsing, for which I use [org.json](https://github.com/stleary/JSON-java). To set it up, download a .jar file to your working directory [here](https://repo1.maven.org/maven2/org/json/json/20190722/json-20190722.jar). Then, run this in the Command Line in your working directory
For Mac:
```
$ jar xf json-20190722.jar
```
*You should substitute *json-20190722.jar* with whatever you chose to name your saved file. You could also use Maven to in
stall this, if that's easier and available to you.

