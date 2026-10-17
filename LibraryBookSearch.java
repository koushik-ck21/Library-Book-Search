
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class LibraryBookSearch {

    private static final BST catalogue = new BST();

    // HTML, CSS and JS files are in the project root folder.
    private static final Path FRONTEND =
            Paths.get(".").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {

        catalogue.insert(
                new Book(101, "The Alchemist", "Paulo Coelho"));
        catalogue.insert(
                new Book(105, "Wings of Fire", "A. P. J. Abdul Kalam"));
        catalogue.insert(
                new Book(103, "Atomic Habits", "James Clear"));

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", 8080), 0);

        server.createContext("/api/books", LibraryBookSearch::books);
        server.createContext("/api/search", LibraryBookSearch::search);
        server.createContext("/", LibraryBookSearch::staticFiles);

        server.setExecutor(null);
        server.start();

        System.out.println("Library Book Catalogue is running!");
        System.out.println("Open port 8080 to view the website.");
    }

    private static void books(HttpExchange ex) throws IOException {

        String method = ex.getRequestMethod();

        if (method.equals("GET")) {
            sendJson(ex, 200,
                    bookListJson(catalogue.getSortedBooks()));
            return;
        }

        if (method.equals("POST")) {
            Map<String, String> form = parseForm(ex);

            try {
                int id = Integer.parseInt(
                        form.getOrDefault("id", ""));

                String title =
                        form.getOrDefault("title", "").trim();
                String author =
                        form.getOrDefault("author", "").trim();

                if (id <= 0 || title.isEmpty() || author.isEmpty()) {
                    sendJson(ex, 400,
                            "{\"message\":\"Enter valid book details.\"}");
                } else if (!catalogue.insert(
                        new Book(id, title, author))) {
                    sendJson(ex, 409,
                            "{\"message\":\"This Book ID already exists.\"}");
                } else {
                    sendJson(ex, 201,
                            "{\"message\":\"Book added successfully!\"}");
                }

            } catch (NumberFormatException e) {
                sendJson(ex, 400,
                        "{\"message\":\"Book ID must be a number.\"}");
            }
            return;
        }

        if (method.equals("DELETE")) {
            try {
                int id = Integer.parseInt(
                        queryParams(ex.getRequestURI().getRawQuery())
                                .getOrDefault("id", ""));

                if (catalogue.delete(id)) {
                    sendJson(ex, 200,
                            "{\"message\":\"Book removed successfully!\"}");
                } else {
                    sendJson(ex, 404,
                            "{\"message\":\"Book ID not found.\"}");
                }

            } catch (NumberFormatException e) {
                sendJson(ex, 400,
                        "{\"message\":\"Enter a valid Book ID.\"}");
            }
            return;
        }

        ex.getResponseHeaders().set("Allow", "GET, POST, DELETE");
        sendJson(ex, 405,
                "{\"message\":\"Method not allowed.\"}");
    }

    private static void search(HttpExchange ex) throws IOException {

        if (!ex.getRequestMethod().equals("GET")) {
            sendJson(ex, 405,
                    "{\"message\":\"Method not allowed.\"}");
            return;
        }

        try {
            int id = Integer.parseInt(
                    queryParams(ex.getRequestURI().getRawQuery())
                            .getOrDefault("id", ""));

            Book book = catalogue.search(id);

            if (book == null) {
                sendJson(ex, 404,
                        "{\"message\":\"Book not found.\"}");
            } else {
                sendJson(ex, 200, bookJson(book));
            }

        } catch (NumberFormatException e) {
            sendJson(ex, 400,
                    "{\"message\":\"Enter a valid Book ID.\"}");
        }
    }

    private static Map<String, String> parseForm(HttpExchange ex)
            throws IOException {

        String body = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        return queryParams(body);
    }

    private static Map<String, String> queryParams(String query)
            throws IOException {

        Map<String, String> result = new HashMap<>();

        if (query == null || query.isEmpty()) {
            return result;
        }

        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);

            String key = URLDecoder.decode(pair[0], "UTF-8");
            String value = pair.length > 1
                    ? URLDecoder.decode(pair[1], "UTF-8")
                    : "";

            result.put(key, value);
        }

        return result;
    }

    private static String bookJson(Book book) {
        return "{\"id\":" + book.getBookId()
                + ",\"title\":\"" + escape(book.getTitle())
                + "\",\"author\":\"" + escape(book.getAuthor())
                + "\"}";
    }

    private static String bookListJson(List<Book> books) {

        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < books.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(bookJson(books.get(i)));
        }

        return json.append("]").toString();
    }

    private static String escape(String value) {

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void sendJson(
            HttpExchange ex, int status, String body)
            throws IOException {

        send(ex, status, body, "application/json; charset=UTF-8");
    }

    private static void staticFiles(HttpExchange ex)
            throws IOException {

        if (!ex.getRequestMethod().equals("GET")) {
            sendJson(ex, 405,
                    "{\"message\":\"Method not allowed.\"}");
            return;
        }

        String requestPath = ex.getRequestURI().getPath();

        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path file = FRONTEND.resolve(
                requestPath.substring(1)).normalize();

        if (!file.startsWith(FRONTEND)
                || !Files.isRegularFile(file)) {
            send(ex, 404, "File not found.",
                    "text/plain; charset=UTF-8");
            return;
        }

        String name = file.getFileName().toString();

        String type = name.endsWith(".html")
                ? "text/html; charset=UTF-8"
                : name.endsWith(".css")
                ? "text/css; charset=UTF-8"
                : name.endsWith(".js")
                ? "text/javascript; charset=UTF-8"
                : "application/octet-stream";

        send(ex, 200, Files.readString(file), type);
    }

    private static void send(
            HttpExchange ex, int status,
            String body, String contentType)
            throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
