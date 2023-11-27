package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public final int THREADS_COUNT = 64;
    final List<String> allowedMethods = List.of(GET, POST);
    private final ExecutorService threadPool;


    public Server() {
        this.threadPool = Executors.newFixedThreadPool(THREADS_COUNT);
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public void start(int port) {

        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.execute(() -> {
                    try (
                            final var in = new BufferedInputStream(socket.getInputStream());
                            final var out = new BufferedOutputStream(socket.getOutputStream());
                    ) {

                        Request request = createRequest(in);

                        if (request == null) {
                            badRequest(out);
                            socket.close();
                            return;
                        }

                        out.write((
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Length: 0\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());
                        out.flush();

                        System.out.println(request.getQueryParams());
                        System.out.println(request.getQueryParam("value"));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private Request createRequest(BufferedInputStream in) throws IOException {
        final var limit = 4096;

        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // ищем request line
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            return null;
        }

        // читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            return null;
        }

        final var method = requestLine[0];
        if (!allowedMethods.contains(method)) {
            return null;
        }
        System.out.println(method);

        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            return null;
        }
        System.out.println(path);

        // ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            return null;
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        System.out.println(headers);

        // для GET тела нет
        if (!method.equals(GET)) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            String body = null;
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);

                body = new String(bodyBytes);
                System.out.println(body);
            }
            return new Request(requestLine, headers, body);
        }
        return new Request(requestLine, headers);
    }


}