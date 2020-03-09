import java.net.*;
import java.util.*;
import java.io.*;

/**
 * This program demonstrates a client socket application that connects to a web
 * server and send a HTTP HEAD request.
 *
 * @author www.codejava.net
 */
public class HttpClient {

	public static void main(String[] args) {
		System.out.print("Search for : ");
		Scanner sc = new Scanner(System.in);
		String input = sc.next();
		while (!input.equals("close")) {
			try {

				URL url = new URL("http://" + input);
				int port = 80;

				try (Socket socket = new Socket(url.getHost(), port)) {

					OutputStream output = socket.getOutputStream();
					PrintWriter writer = new PrintWriter(output, true);

					if (url.getPath().contentEquals("")) {
						writer.println("GET " + "/" + " HTTP/1.1");
					} else {
						writer.println("GET " + url.getPath() + " HTTP/1.1");
					}
					writer.println("Host: " + url.getHost());
					writer.println("User-Agent: Simple Http Client");
					writer.println("Accept: text/html");
					writer.println("Accept-Language: en-US");
					writer.println();
					writer.flush();

					InputStream response = socket.getInputStream();

					BufferedReader reader = new BufferedReader(new InputStreamReader(response));

					String request = "";

					int line;

					while ((line = reader.read()) != -1 && reader.ready()) {
						System.out.print((char) line);
					}
					System.out.println("--------------------");
					System.out.println("Search for : ");
					sc = new Scanner(System.in);
					input = sc.next();
					
				} catch (UnknownHostException ex) {

					System.out.println("Server not found: " + ex.getMessage());

				} catch (IOException ex) {

					System.out.println("I/O error: " + ex.getMessage());
				}
			} catch (Exception e) {

			}
		}
	}
}
