/**
 * Computer Networks Programming Assigment 02
 * Parallel File Downloading Console App
 *
 * @author Emre Caniklioglu
 * @date   25/12/2021
 * @version 1.1.2
 *
 */

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelFileDownloader {

    private static final  int PORT = 80;

    private final String   host;
    private List<String> pathsQueue;

    public ParallelFileDownloader(String index, Integer numOfThreads) {
        this.host = index.substring(0, index.indexOf("/"));

        System.out.printf("URL of the index file: %s \n", index);
        System.out.printf("Number of parallel connections: %s \n", numOfThreads);

        this.InitializeQueue(index.substring(index.indexOf("/")));

        System.out.print("Index file is downloaded \n");
        System.out.printf("There are %s files in the index\n", this.pathsQueue.size());

        int pathCounter = 1;

        for (String URL : this.pathsQueue) {

            String pathHost = URL.substring(0, URL.indexOf("/"));
            String path = URL.substring(URL.indexOf("/"));

            List<String> headCheckResponse = Collections.emptyList();

            try {
                headCheckResponse = RequestGet.SendGetHead(pathHost, path);
            }
            catch (IOException err) {
                System.out.printf("An error occurred while sending a Get Head request to the URL: %s \n", pathHost + path);
                System.exit(-1);
            }

            if (headCheckResponse.get(0).contains("200")) {

                int size = this.ExtractSize(headCheckResponse);

                if (size != 0) {

                    if (size < numOfThreads) {
                        System.out.println("Number of threads can't be bigger than the size");
                        System.exit(-1);
                    }

                    int shareSize = size / numOfThreads;

                    List<Integer> ranges = new ArrayList<>();

                    for (int counter = 1; counter <= numOfThreads; counter++) {
                        if (counter == numOfThreads) {
                            ranges.add(size);
                            break;
                        }
                        ranges.add(shareSize * counter);
                    }

                    List<Future<List<String>>> responseFutures = new ArrayList<>();
                    GetRequestASYNC getHeadASYNC = null;

                    for (int counter = 0; counter < numOfThreads; counter++) {
                        getHeadASYNC = new GetRequestASYNC();

                        if (counter == 0) {
                            responseFutures.add(getHeadASYNC.SendRequest(pathHost, path, 0, ranges.get(0)));
                        }
                        else {
                            responseFutures.add(getHeadASYNC.SendRequest(pathHost, path, ranges.get(counter - 1) + 1, ranges.get(counter)));
                        }
                    }

                    List<String> response = new LinkedList<>();

                    for (Future<List<String>> responseFuture : responseFutures) {
                        try {
                            response.addAll(responseFuture.get());
                        }
                        catch (ExecutionException | InterruptedException err) {
                            System.out.print("An error occurred while  retrieving the future \n");
                            System.exit(-1);
                        }
                    }

                    System.out.printf("%s- %s (size = %s) is downloaded \n", pathCounter, pathHost + path, size);
                    System.out.print("File parts: ");

                    for (int counter = 0; counter < ranges.size(); counter++) {
                        if (counter == 0)
                            System.out.printf("%s - %s (%s)", 0, ranges.get(counter), ranges.get(counter));
                        else
                            System.out.printf("%s - %s (%s)", ranges.get(counter - 1), ranges.get(counter), ranges.get(counter) - ranges.get(counter - 1));

                        if  (counter != ranges.size() - 1)
                            System.out.print(", ");
                    }
                    System.out.print("\n");

                    String responseAsString = response.stream().reduce((x1, x2) -> x1 + x2 + "\n").orElse("DEFAULT RESPONSE");

                    try{
                        File file = new File(String.format("./%s", path.substring(path.lastIndexOf("/"))));
                        FileOutputStream fileOutputStream = new FileOutputStream(file);

                        fileOutputStream.write(responseAsString.getBytes(StandardCharsets.UTF_8));
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    }
                    catch (IOException err) {
                        err.printStackTrace();
                        System.out.print("An error occurred while trying to save the retrieved file to the local computer \n");
                        System.exit(-1);
                    }
                }
                else {
                    System.out.printf("%s- %s no information about the size found on the GET head request is not downloaded \n", pathCounter,  pathHost + path);
                }
            }
            else {
                System.out.printf("%s- %s is not found \n", pathCounter,  pathHost + path);
            }
            pathCounter++;
        }
        System.exit(1);
    }

    private int ExtractSize(List<String> list) {
        for (String s : list) {
            if (s.contains("Content-Length:")) {
                return Integer.parseInt(s.split(" ")[1]);
            }
        }
        return 0;
    }

    private void InitializeQueue(String path) {
        try {
            List<String> response = RequestGet.SendGetHead(this.host, path);

            boolean isOk = response.get(0).equals("HTTP/1.1 200 OK");

            if (isOk) {
                this.pathsQueue = RequestGet.SendGet(this.host, path);
            } 
            else {
                System.out.println("Index file does not exist");
                System.exit(-1);
            }

        }
        catch (IOException errs) {
            System.out.println("An error occurred while retrieving the index file or parsing its contents");
            System.exit(-1);
        }
    }

    private static class GetRequestASYNC {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        public Future<List<String>> SendRequest(String host, String path, int lowerBound, int upperBound) {
            return this.executor.submit(() -> {
                List<String> response = null;
                try {
                    response = RequestGet.SendGetRange(host, path, lowerBound, upperBound);
                }
                catch (IOException err) {
                    err.printStackTrace();
                }
                return response;
            });
        }
    }

    private static class RequestWriter {

        protected static PrintWriter GetRequestWriter(OutputStream out, String host, String path) {

            PrintWriter writer = new PrintWriter(out);

            writer.print(String.format("GET /%s HTTP/1.1\r\n", path));
            writer.print(String.format("Host: %s\r\n", host));
            writer.print("\r\n");

            return writer;
        }

        protected static PrintWriter GetHeadRequestWriter(OutputStream out, String host, String path) {
            PrintWriter writer = new PrintWriter(out);

            writer.print(String.format("HEAD /%s HTTP/1.1\r\n", path));
            writer.print(String.format("Host: %s\r\n", host));
            writer.print("User-Agent: Console Http Client\r\n");
            writer.print("Accept: text/html\r\n");
            writer.print("Accept-Language: en-US\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");

            return writer;
        }

        protected static PrintWriter GetRangeRequestWriter(
                OutputStream out,
                String host,
                String path,
                Integer lowerBound,
                Integer upperBound) {

            PrintWriter writer = new PrintWriter(out);

            writer.print(String.format("GET /%s HTTP/1.1\r\n", path));
            writer.print(String.format("Host: %s\r\n", host));
            writer.print(String.format("Range: bytes=%s-%s\r\n", lowerBound, upperBound));
            writer.print("\r\n");

            return writer;
        }
    }

    private static class RequestGet {

        protected static List<String> SendGet(String host, String path) throws IOException {
            Socket socket = CreateSocket(host);
            OutputStream out = socket.getOutputStream();

            PrintWriter writer = RequestWriter.GetRequestWriter(out, host, path);
            writer.flush();

            List<String> responseList = new LinkedList<>();
            String outputString = "";

            InputStream input = socket.getInputStream();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));

            boolean responseHeadEnded = false;

            boolean first = true;

            while((outputString = bufferedReader.readLine()) != null) {

                if (first) {
                    if (!outputString.contains("200")) {
                        System.out.println("An error occurred while retrieving the index file");
                        System.exit(-1);
                    }
                    first = false;
                }

                if (outputString.equals("")) {
                    responseHeadEnded = true;
                    continue;
                }

                if (responseHeadEnded) {
                    responseList.add(outputString);
                }
            }

            input.close();
            out.close();

            return responseList;
        }

        protected static List<String> SendGetRange(
                String host,
                String path,
                Integer lowerBound,
                Integer upperBound
        )
                throws IOException {

            Socket socket = CreateSocket(host);
            OutputStream out = socket.getOutputStream();

            PrintWriter writer = RequestWriter.GetRangeRequestWriter(out, host, path, lowerBound, upperBound);
            writer.flush();

            List<String> responseList = new LinkedList<>();
            String outputString = "";

            InputStream input = socket.getInputStream();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));

            boolean responseHeadEnded = false;

            while((outputString = bufferedReader.readLine()) != null) {

                if (outputString.equals("")) {
                    responseHeadEnded = true;
                    continue;
                }

                if (responseHeadEnded) {
                    responseList.add(outputString);
                }
            }

            input.close();
            out.close();

            return responseList;
        }

        protected static List<String> SendGetHead(String host, String path) throws IOException {

            Socket socket = CreateSocket(host);
            OutputStream out = socket.getOutputStream();

            PrintWriter writer = RequestWriter.GetHeadRequestWriter(out, host, path);
            writer.flush();

            List<String> responseList = new LinkedList<>();
            String outputString = "";

            InputStream input = socket.getInputStream();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));

            while((outputString = bufferedReader.readLine()) != null) {
                responseList.add(outputString);
            }

            input.close();
            out.close();

            return responseList;
        }

        private static Socket CreateSocket(String host) throws IOException {
            return new Socket(host, PORT);
        }
    }

    /**
     Test cases:
        - http://www.cs.bilkent.edu.tr/~cs421/fall21/project1/index1.txt
        - http://www.cs.bilkent.edu.tr/~cs421/fall21/project1/index2.txt
     */

    public static void main(String[] args) {

        String index = "";

        try {
            index = args[0];
        } catch (Exception err) {
            System.out.print("An error occurred while parsing the command line arguments please try again \n");
            System.exit(-1);
        }

        // DEFAULT VALUES FOR THE NUMBER OF THREADS
        int numOfThreads = 1;

        try {
            numOfThreads  = Integer.parseInt(args[1]);
        }
        catch (Exception err) {
            System.out.print("An error occurred while parsing the command line arguments please try again \n");
        }

        ParallelFileDownloader parallelFileDownloader = new ParallelFileDownloader(index, numOfThreads);

        new ParallelFileDownloader("www.cs.bilkent.edu.tr/~cs421/fall21/project1/index2.txt", 10);
    }
}
