package com.invoicemind.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

/** Tiny HTTP helper that never throws on 4xx/5xx so tests can assert on error responses. */
public final class TestApi {
  public record Resp(int status, byte[] bytes, HttpHeaders headers, ObjectMapper mapper) {
    public String text() { return new String(bytes, StandardCharsets.UTF_8); }

    public JsonNode json() {
      try {
        return mapper.readTree(bytes);
      } catch (IOException e) {
        throw new IllegalStateException("Not JSON: " + text(), e);
      }
    }

    public String cookie(String name) {
      var cookies = headers.get(HttpHeaders.SET_COOKIE);
      if (cookies == null) return null;
      return cookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
          .map(c -> c.substring(name.length() + 1, c.indexOf(';') < 0 ? c.length() : c.indexOf(';'))).orElse(null);
    }
  }

  /** An authenticated test identity. */
  public record Session(String token, UUID userId, UUID organizationId, String email, String password) {}

  public static final String PASSWORD = "Passw0rd!long";

  private final RestClient client;
  private final ObjectMapper mapper;
  private final String baseUrl;

  public TestApi(String baseUrl, ObjectMapper mapper) {
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.client = RestClient.builder().baseUrl(baseUrl)
        .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
        .build();
  }

  public Resp get(String path, String token) { return call(HttpMethod.GET, path, token, null, null); }

  public Resp post(String path, String token, Object body) { return call(HttpMethod.POST, path, token, body, null); }

  public Resp put(String path, String token, Object body) { return call(HttpMethod.PUT, path, token, body, null); }

  public Resp patch(String path, String token, Object body) { return call(HttpMethod.PATCH, path, token, body, null); }

  public Resp delete(String path, String token) { return call(HttpMethod.DELETE, path, token, null, null); }

  public Resp call(HttpMethod method, String path, String token, Object body, Map<String, String> headers) {
    // URI.create keeps the caller's percent-encoding as is (a String template would encode '%' again)
    var spec = client.method(method).uri(java.net.URI.create(baseUrl + path));
    if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (headers != null) for (var e : headers.entrySet()) spec = spec.header(e.getKey(), e.getValue());
    if (body != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
    return spec.exchange((req, res) -> new Resp(res.getStatusCode().value(), res.getBody().readAllBytes(),
        res.getHeaders(), mapper), true);
  }

  public Resp upload(String token, byte[] content, String filename) { return upload(token, content, filename, Map.of()); }

  public Resp upload(String token, byte[] content, String filename, Map<String, String> headers) {
    MultipartBodyBuilder b = new MultipartBodyBuilder();
    b.part("file", new ByteArrayResource(content) {
      @Override
      public String getFilename() { return filename; }
    }).contentType(MediaType.APPLICATION_OCTET_STREAM);
    var spec = client.post().uri("/api/v1/invoices").contentType(MediaType.MULTIPART_FORM_DATA).body(b.build());
    if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    for (var e : headers.entrySet()) spec = spec.header(e.getKey(), e.getValue());
    return spec.exchange((req, res) -> new Resp(res.getStatusCode().value(), res.getBody().readAllBytes(),
        res.getHeaders(), mapper), true);
  }

  /** A minimal but structurally valid PDF carrying the fake-ai scenario marker and unique content. */
  public static byte[] pdf(String scenarioId) {
    String doc = "%PDF-1.4\n%SCENARIO:" + scenarioId + "\n1 0 obj<</Type/Catalog>>endobj\n% " + UUID.randomUUID()
        + "\ntrailer<</Root 1 0 R>>\n%%EOF\n";
    return doc.getBytes(StandardCharsets.ISO_8859_1);
  }

  // --------------------------------------------------------------- identities
  public Session registerOrg(String orgName) {
    String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    Resp r = post("/api/v1/auth/register", null,
        Map.of("organizationName", orgName, "fullName", "Admin " + orgName, "email", email, "password", PASSWORD));
    if (r.status() != 201) throw new IllegalStateException("register failed: " + r.status() + " " + r.text());
    return session(r.json(), email, PASSWORD);
  }

  public Session createUser(Session admin, String role) {
    String email = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    Resp r = post("/api/v1/users", admin.token(),
        Map.of("email", email, "fullName", role + " user", "role", role, "password", PASSWORD));
    if (r.status() != 201) throw new IllegalStateException("createUser failed: " + r.status() + " " + r.text());
    return login(email, PASSWORD);
  }

  public Session login(String email, String password) {
    Resp r = post("/api/v1/auth/login", null, Map.of("email", email, "password", password));
    if (r.status() != 200) throw new IllegalStateException("login failed: " + r.status() + " " + r.text());
    return session(r.json(), email, password);
  }

  private static Session session(JsonNode auth, String email, String password) {
    return new Session(auth.get("accessToken").asText(), UUID.fromString(auth.at("/user/id").asText()),
        UUID.fromString(auth.at("/user/organizationId").asText()), email, password);
  }
}
