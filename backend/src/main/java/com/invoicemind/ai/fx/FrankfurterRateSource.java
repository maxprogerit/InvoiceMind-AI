package com.invoicemind.ai.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.config.AppProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ECB reference rates through the free Frankfurter API (https://frankfurter.dev, no key needed).
 * The request carries nothing but two dates; the response is EUR-based and daily on business days.
 */
@Component
public class FrankfurterRateSource implements RateSource {
  private final RestClient http;
  private final ObjectMapper mapper;

  public FrankfurterRateSource(AppProperties props, ObjectMapper mapper) {
    var fx = props.fx();
    this.mapper = mapper;
    var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(fx.timeoutMs())).build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofMillis(fx.timeoutMs()));
    this.http = RestClient.builder().baseUrl(fx.baseUrl()).requestFactory(factory).build();
  }

  @Override
  public Map<LocalDate, Map<String, BigDecimal>> fetch(LocalDate from, LocalDate to) throws IOException {
    byte[] body;
    try {
      body = http.get().uri("/{from}..{to}", from, to).retrieve().body(byte[].class);
    } catch (RestClientException e) {
      throw new IOException("Rate source unavailable: " + e.getMessage(), e);
    }
    JsonNode rates = mapper.readTree(body).path("rates");
    if (!rates.isObject()) throw new IOException("Unexpected response from the rate source");

    Map<LocalDate, Map<String, BigDecimal>> out = new LinkedHashMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> days = rates.fields(); days.hasNext(); ) {
      var day = days.next();
      Map<String, BigDecimal> perCurrency = new LinkedHashMap<>();
      day.getValue().fields().forEachRemaining(e -> {
        if (e.getValue().isNumber() && e.getValue().decimalValue().signum() > 0) {
          perCurrency.put(e.getKey(), e.getValue().decimalValue());
        }
      });
      out.put(LocalDate.parse(day.getKey()), perCurrency);
    }
    return out;
  }
}
