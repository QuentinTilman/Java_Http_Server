import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Scanner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/*
 * Sources:
 * 	- http://www.java2s.com/Tutorials/Java/Socket/How_to_use_Java_Socket_class_to_create_a_HTTP_client.htm
 *  - https://stackoverflow.com/questions/6124233/how-to-handle-read-the-response-with-transfer-encodingchunked
 *  - https://stackoverflow.com/questions/8147284/how-to-use-google-translate-api-in-my-java-application
 *  - https://stackoverflow.com/questions/39326235/replace-text-in-all-text-nodes-in-a-tree-using-jsoup
 *  - 
 *  
 *  Configurations that were used during testing:
 *  - GET http://www.tinyos.net/ 80 NL
 *  - GET http://tldp.org/ 80 NL
 *  - GET http://81.247.73.165:3001/images.html 3001 FR
 *  - GET http://81.247.73.165:3001/ 3001 FR
 */
public class Client {

	private static int port;
	private static String host;
	private static String path;
	private static Socket socket;
	private static PrintWriter output;
	private static InputStream inputStream;

	public static void main(String[] args) throws Exception {
		// Read the input arguments in 'Run Configurations'
		String httpCommand = args[0];
		String uriString = args[1];
		port = Integer.parseInt(args[2]);
		String language = args[3];

		// Find host and path and create the socket, the request writer and the response reader
		URI uri = new URI(uriString);
		host = uri.getHost();
		path = uri.getPath();

		switch (httpCommand) {
		case "GET":
			processGetRequest(uriString, language);
			break;
		case "HEAD":
			processHeadRequest(uriString);
			break;
		case "PUT":
			processPutPostRequest("PUT", uriString);
			break;
		case "POST":
			processPutPostRequest("POST", uriString);

		}
	}

	private static void initialiseSocketOutputInput() throws Exception {
		socket = new Socket(host, port);
		boolean autoflush = false;
		output = new PrintWriter(socket.getOutputStream(), autoflush);
		inputStream = socket.getInputStream();
	}

	private static void processGetRequest(String uriString, String language) throws Exception {
		String httpCommand = "GET";

		initialiseSocketOutputInput();

		System.out.println("Starting GET request for: " + uriString + "\n");
		String response = getResponse(uriString, httpCommand);

		// Extract the body (html) out of the result
		String html = response.substring(response.indexOf(System.getProperty("line.separator") + System.getProperty("line.separator")) + 1);
		html.trim();

		// Translate the text inside the html (We assume every site we visit is in English)
		if (language.toLowerCase() != "en") {
			html = translateHTMLWithGoogleScript(html, language);
		}

		// Find the images with JSoup and process them
		Document doc = Jsoup.parse(html);
		Elements images = doc.select("img");
		for (Element el : images) {
			String imageUrl = el.attr("src");
			// You don't need to save remote images
			if (!(imageUrl.startsWith("http") || imageUrl.startsWith("www")))
				processImage(imageUrl);
		}
		// Find the scripts with JSoup and process them
		Elements scripts = doc.select("script");
		for (Element el : scripts) {
			String scriptUrl = el.attr("src");
			// You don't need to save remote scripts
			if (!(scriptUrl.startsWith("http") || scriptUrl.startsWith("www")))
				processScriptsAndFiles(scriptUrl);
		}
		// Find the css files with JSoup and process them
		Elements cssFiles = doc.select("link");
		for (Element el : cssFiles) {
			String cssUrl = el.attr("href");
			// You don't need to save remote files
			if (!(cssUrl.startsWith("http") || cssUrl.startsWith("www")))
				processScriptsAndFiles(cssUrl);
		}

		// Sometimes characters apear after the closing html tag, so we delete them here
		html = html.substring(0, html.lastIndexOf(">") + 1);

		// Making sure special characters are correctly parsed
		html = Jsoup.parse(html).html();

		// Print the processed response in the console
		System.out.println("\nProcessed response:");
		System.out.println(html);

		// Store the translated HTML locally (The images will load correctly because
		// they have been saved in the same directory)
		FileWriter writer = new FileWriter("index.html", false);
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		bufferedWriter.write(html);
		bufferedWriter.close();

		// Close the socket
		socket.close();

		System.out.println("\nRequest finished");
	}

	private static void processHeadRequest(String uriString) throws Exception {
		initialiseSocketOutputInput();

		System.out.println("Starting HEAD request for: " + uriString + "\n");
		String httpCommand = "HEAD";
		String response = getResponse(uriString, httpCommand);

		System.out.println(response);
		socket.close();
	}

	private static void processPutPostRequest(String httpCommand, String uriString) throws Exception {
		System.out.println("Type your message to send to the server:");
		String userInput = getUserInput();
		System.out.println(userInput);

		initialiseSocketOutputInput();
		
		if(httpCommand.equals("POST"))
			userInput = "message=" + userInput;
		userInput = userInput.strip();

		String fileToWriteTo = "/putFromClient.txt";
		output.println(httpCommand + " " + fileToWriteTo + " HTTP/1.1");
		output.println("Host: " + host + ":" + Integer.toString(port));
		output.println("Content-Type: text/plain");
		output.println("Content-Length: " + Integer.toString(userInput.length()+2));
		output.println("Connection: Closed");
		output.println();
		output.println(userInput);
		output.flush();

		System.out.println(httpCommand + " request sent.");
		
		socket.close();
	}

	private static String getUserInput() {
		String s = "";
		Scanner sc = new Scanner(System.in);
		s = sc.next();
		return s;
	}

	private static String getResponse(String uriString, String httpCommand) throws Exception {

		BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));

		// send an HTTP request to the web server
		output.println(httpCommand + " " + path + " HTTP/1.1");
		output.println("Host: " + host + ":" + Integer.toString(port));
		output.println("Connection: Keep-Alive");
		output.println();
		output.flush();

		// Read the response and make it into one String
		boolean loop = true;
		StringBuilder sb = new StringBuilder(8096);
		while (loop) {
			if (input.ready()) {
				int i = 0;
				while (i != -1) {
					i = input.read();
					sb.append((char) i);
				}
				loop = false;
			}
		}
		String result = sb.toString();
		return result;
	}

	private static String translateHTMLWithGoogleScript(String html, String language) throws Exception {
		System.out.println("Translating...\n");

		// Since the max number of characters we are able to send to the translation API is (around) 3000
		//  and tags need to be closed when being sent to translate, we find the last closing tag in the
		//  next 3000 chars of the html and make a request up until that point and repeat for the characters
		//  afterwards.
		StringBuilder sb = new StringBuilder(8096);
		int i = 0;
		while (i < html.length()) {
			String next3000chars;
			int lastIndex;
			if (i + 3000 < html.length()) {
				next3000chars = html.substring(i, i + 3000);
				lastIndex = next3000chars.lastIndexOf(">");
				String nextToTranslate = html.substring(i, i + lastIndex);
				sb.append(translate("en", language, nextToTranslate));
			} else {
				next3000chars = html.substring(i, html.length());
				lastIndex = html.length();
				sb.append(translate("en", language, next3000chars));
			}
			i += lastIndex + 1;
		}

		String result = sb.toString();
		return result;
	}
	

	private static String translate(String langFrom, String langTo, String text) throws IOException {
		// Because there are no free translation API's that use HTTP instead of HTTPS,
		// we could not use Sockets so we had to use HttpURLConnection
		String urlStr = "https://script.google.com/macros/s/AKfycbxyZWyyk0Di93JhWUyWzFsB0aZjKxUyv3M_grQHmDy7jN0GHx4/exec"
				+ "?q=" + URLEncoder.encode(text, "UTF-8") + "&target=" + langTo + "&source=" + langFrom;
		URL url = new URL(urlStr);
		StringBuilder response = new StringBuilder();
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestProperty("User-Agent", "Mozilla/5.0");
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}

	
	private static void processImage(String imageUrl) throws Exception {

		initialiseSocketOutputInput();

		// Send an HTTP request to the web server
		output.println("GET" + " /" + imageUrl + " HTTP/1.1");
		output.println("Host: " + host + ":" + Integer.toString(port));
		output.println("Connection: Closed");
		output.println();
		output.flush();

		// Create the correct directories for the images
		if (imageUrl.lastIndexOf("/") != -1) {
			String directoryPath = imageUrl.substring(0, imageUrl.lastIndexOf("/"));
			File f = new File(directoryPath);
			f.mkdirs();
		}

		final FileOutputStream fileOutputStream = new FileOutputStream(imageUrl);
		System.out.println("Saving file: " + imageUrl);

		boolean headerEnded = false;
		byte[] bytes = new byte[2048];
		int length;
		while ((length = inputStream.read(bytes)) != -1) {
			// If the end of the header had already been reached, write the bytes to the file as normal.
			if (headerEnded) {
				fileOutputStream.write(bytes, 0, length);
			}
			// This locates the end of the header by comparing the current byte as well as the next 3 bytes
			// with the HTTP header end "\r\n\r\n" (which in integer representation would be 13 10 13 10).
			// If the end of the header is reached, the flag is set to true and the remaining data in the
			// currently buffered byte array is written into the file.
			else {
				for (int i = 0; i < 2045; i++) {
					if (bytes[i] == 13 && bytes[i + 1] == 10 && bytes[i + 2] == 13 && bytes[i + 3] == 10) {
						headerEnded = true;
						fileOutputStream.write(bytes, i + 4, 2048 - i - 4);
						break;
					}
				}
			}
		}
		System.out.println("Saved file: " + imageUrl);
		inputStream.close();
		fileOutputStream.close();
	}

	private static void processScriptsAndFiles(String scriptUrl) throws Exception {
		initialiseSocketOutputInput();
		BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));

		// Send an HTTP request to the web server
		output.println("GET" + " /" + scriptUrl + " HTTP/1.1");
		output.println("Host: " + host + ":" + Integer.toString(port));
		output.println("Connection: Closed");
		output.println();
		output.flush();

		System.out.println("Saving script/file: " + scriptUrl);
		
		// Read the response and make it into one String
		boolean loop = true;
		StringBuilder sb = new StringBuilder(8096);
		while (loop) {
			if (input.ready()) {
				int i = 0;
				while (i != -1) {
					i = input.read();
					sb.append((char) i);
				}
				loop = false;
			}
		}
		String result = sb.toString();
		
		String body = result.substring(
				result.indexOf(System.getProperty("line.separator") + System.getProperty("line.separator")) + 1);
		body.trim();
		
		if (scriptUrl.lastIndexOf("/") != -1) {
			String directoryPath = scriptUrl.substring(0, scriptUrl.lastIndexOf("/"));
			File f = new File(directoryPath);
			f.mkdirs();
		}
		
		FileWriter writer = new FileWriter(scriptUrl, false);
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		bufferedWriter.write(body);
		bufferedWriter.close();
		
		System.out.println("Saved script/file: " + scriptUrl);
	}

}