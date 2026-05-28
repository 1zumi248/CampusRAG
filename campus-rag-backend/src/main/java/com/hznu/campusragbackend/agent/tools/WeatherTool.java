package com.hznu.campusragbackend.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class WeatherTool {

    private static final String AMAP_URL = "https://restapi.amap.com/v3/weather/weatherInfo?key=%s&city=%s&extensions=base";
    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;

    public WeatherTool(@Value("${amap.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool("查询指定城市的实时天气信息，返回温度、天气状况、湿度、风力等数据。当用户询问天气时调用此工具")
    public String getWeather(@P("城市名称，例如杭州、北京、上海") String city) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("高德API Key未配置");
            return "天气服务暂不可用（API Key 未配置）";
        }

        try {
            String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = String.format(AMAP_URL, apiKey, encoded);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "天气查询失败，服务返回状态码: " + response.statusCode();
            }

            log.info("高德天气查询成功 city={}, response length={}", city, response.body().length());
            return formatWeather(city, response.body());
        } catch (Exception e) {
            log.error("天气查询异常 city={}", city, e);
            return "天气查询失败: " + e.getMessage();
        }
    }

    private String formatWeather(String city, String json) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(json);
            var lives = root.path("lives");
            if (!lives.isArray() || lives.isEmpty()) {
                return city + "：暂无天气数据";
            }
            var current = lives.get(0);
            return String.format(
                    "%s天气：%s，温度 %s°C，湿度 %s%%，%s风 %s级，数据更新时间 %s",
                    current.path("city").asText(city),
                    current.path("weather").asText("未知"),
                    current.path("temperature").asText("-"),
                    current.path("humidity").asText("-"),
                    current.path("winddirection").asText(""),
                    current.path("windpower").asText("-"),
                    current.path("reporttime").asText("未知")
            );
        } catch (Exception e) {
            log.warn("天气数据格式化失败", e);
            return city + "天气数据获取成功，但格式化失败: " + e.getMessage();
        }
    }
}
