import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestWiki {
    public static void main(String[] args) throws Exception {
        String query = "The Narmada River originates from Madhya Pradesh";
        String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        String url = "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch=" + encodedQuery + "&srlimit=3";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Test")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
}
