package com.shine.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shine.weather.exception.CityNotFoundException;
import com.shine.weather.model.Weather;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private final WebClient webClient;

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${geo.api.key}")
    private String geoApiKey;

    public WeatherService(WebClient.Builder webClientBuilder,
                          @Value("${weather.api.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }


    //base-url?city=Delhi&appId=YOUR_KEY&units=metric
    public Weather getWeatherByCity(String city){

        Mono<JsonNode> geoResponseNode = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geo/1.0/direct")
                        .queryParam("q", city)
                        .queryParam("limit", 1)
                        .queryParam("appid", geoApiKey)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class);

        JsonNode geoJson = geoResponseNode.block();

        if (geoJson == null || !geoJson.isArray() || geoJson.isEmpty()) {
            throw new CityNotFoundException("City not found: " + city);
        }

        JsonNode geoNode = geoJson.get(0);
        Double lat = geoNode.get("lat").asDouble();
        Double lon = geoNode.get("lon").asDouble();
        String resolvedCity = geoNode.get("name").asText();
        String country = geoNode.get("country").asText();

        //Mono<JsonNode>: Asynchronous JsonNode
        Mono<JsonNode> weatherResponseMono = webClient.get()//initiates an HTTP GET request
                .uri(uriBuilder -> uriBuilder //builds the URL with query parameters
                        .path("/data/2.5/weather")
                        .queryParam("lat",lat)
                        .queryParam("lon",lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .build())
                .retrieve()//This tells WebClient: "Execute the request and give me the response body."
                .bodyToMono(JsonNode.class); //This maps the HTTP response body to a Mono<JsonNode>.

        //This converts the async Mono into a regular JsonNode by blocking until the response arrives.
        JsonNode weatherJson = weatherResponseMono.block();

        if (weatherJson == null || !weatherJson.has("main")) {
            throw new CityNotFoundException("Weather data not found for city: " + city);
        }

        Double temp = weatherJson.get("main").get("temp").asDouble();
        Integer humidity = weatherJson.get("main").get("humidity").asInt();
        Double windSpeed = weatherJson.get("wind").get("speed").asDouble();
        Double feelsLike = weatherJson.get("main").get("feels_like").asDouble();
        String description = weatherJson.get("weather").get(0).get("description").asText();
        String icon = weatherJson.get("weather").get(0).get("icon").asText();

        return new Weather(resolvedCity,country,temp,description,humidity,windSpeed,feelsLike,icon);
    }

}
