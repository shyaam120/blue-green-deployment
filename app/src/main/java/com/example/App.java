package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class App {

    public static void main(String[] args) throws IOException {

        int port = 8080;

        String version = System.getenv()
                .getOrDefault("APP_VERSION", "1.0");

        HttpServer server = HttpServer.create(
                new InetSocketAddress(port), 0
        );

        server.createContext("/", exchange -> {

            String response =
                    "Blue-Green Deployment Application\n" +
                    "Version: " + version + "\n" +
                    "Server is running successfully.";

            byte[] responseBytes = response.getBytes();

            exchange.sendResponseHeaders(
                    200,
                    responseBytes.length
            );

            OutputStream output = exchange.getResponseBody();
            output.write(responseBytes);
            output.close();
        });

        server.start();

        System.out.println(
                "Application started on port " + port
        );
        System.out.println(
                "Application version: " + version
        );
    }
}
