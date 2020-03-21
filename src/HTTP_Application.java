import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

import jdk.jshell.spi.ExecutionControl.NotImplementedException;

//optimized (handles request is 6ms instead of 4000ms)
public class HTTP_Application implements Runnable {
	private Socket clientConnection;
	static final String ROOT = "D:\\www\\";
	static final String DEFAULT_PAGE = "index.html";
	static final String ERROR_FILE_404 = "/errors/404.html";
	static final String ERROR_FILE_400 = "/errors/400.html";
	static final String ERROR_FILE_501 = "/errors/501.html";
	static final String FILES_LIST = "files.html";
	static final String POST_FILE = "posts.html";
	static final String FILES_PAGE = "file.html";
	static final String POST_PAGE = "post.html";
	private final Date firstRequestTime;
	static final int keep_Alive = 1000;
	private int requestInOneConnection = 0;

	public HTTP_Application(Socket r) throws IOException {
		this.setRequest(r);
		this.firstRequestTime = new Date();
	}

	public Socket getRequest() {
		return clientConnection;
	}

	public void setRequest(Socket request) {
		this.clientConnection = request;
	}

	@Override
	public void run() {
		System.out.print("\n--------------------------------------\n");
		System.out.print("Client " + clientConnection.getInetAddress() + " made new request.");
		System.out.print("\n--------------------------------------\n");
		try {
			clientConnection.setKeepAlive(true);
			while ((firstRequestTime.getTime() + keep_Alive >= (new Date()).getTime())) {
				handleRequest();
			}
			clientConnection.close();

		} catch (IOException e) {
			try {
				e.printStackTrace();
				System.out.println("\n connection for " + clientConnection.getInetAddress() + " has been closed");
				clientConnection.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	/**
	 * Is used to handle a client request. This can be seen as a main method to handle a full Request.
	 * It will read a client request and filter through different tokens to determine the requested method and to handle it.
	 * 
	 * @throws IOException
	 */
	public void handleRequest() throws IOException {
		long start = new Date().getTime();

		BufferedReader in = null;
		DataOutputStream out = null;
		try {
			in = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
			out = new DataOutputStream(clientConnection.getOutputStream());
			String header = "";
			while (in.ready()) {
				int theCharNum = in.read();
				char theChar = (char) theCharNum;
				header += theChar;
			}
			HashMap<String, String> headerTokens = getRequestHeader(header);
			if (headerTokens.size() == 0) {

			} else {
				requestInOneConnection += 1;
				// System.out.print("\n requests:" + requestInOneConnection);
				System.out.print("\n--------------------------------------\n");

				if (!checkIfValidRequest(headerTokens)) {
					getBadRequestHeader(in, out);
				}

				String method = headerTokens.get("method");
				if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("PUT")
						&& !method.equals("POST")) {
					getNotImplementedHeader(out);
					out.close();
				} else {// TE VERBETEREN MOET NIET GEBEUREN BEHAVLE BIJ GET
					String ifnonematch = "";
					String notmodifiedsince = "";
					try {
						ifnonematch = headerTokens.get(headerTokens.get("If-None-Match"));
						notmodifiedsince = headerTokens.get("If-Modified-Since");
					} catch (IndexOutOfBoundsException e) {

					}
					String path = headerTokens.get("path");
					if (method.equals("GET")) {
						if (path.equals("/")) {
							path = DEFAULT_PAGE;
						}
						File file = new File(ROOT + path);
						String ETag = getEtag(file);

						SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
						dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
						String lastModified = dateFormat.format(new Date(file.lastModified()));

						if (lastModified.equals(notmodifiedsince) || ETag.equals(ifnonematch)) {
							getNotModifiedHeader(out);
						} else {
							getHeader(out);
							getFile(out, path, ETag, file);
						}

						long end = new Date().getTime();
						long total = end - start;
						System.out.println("total time:" + total);
					} else if (method.equals("PUT")) {
						putMethod(headerTokens);
						getHeader(out);
						out.writeBytes("\r\n");
						out.flush();

					} else if (method.equals("HEAD")) {
						getHeader(out);
						out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
						out.writeBytes("\r\n");
						out.flush();
					} else if (method.equals("POST")) {
						postMethod(headerTokens);
						getHeader(out);
						File file = new File(ROOT + POST_PAGE);
						file.setLastModified(new Date().getTime());
						String ETag = getEtag(file);
						getFile(out, POST_PAGE, ETag, file);
					}
				}
			}

		} catch (FileNotFoundException | NoSuchFileException e) {
			getFileNotFoundHeader(in, out);
		} catch (Exception e) {
			e.printStackTrace();
			getNotImplementedHeader(out);
		}
	}

	/**
	 * 
	 * @param header String that represents the full HTTP request of a client.
	 * @return returns a hashmap of important headers that can be found in a HTTP request
	 */
	private HashMap<String, String> getRequestHeader(String header) {
		HashMap<String, String> headerTokens = new HashMap<String, String>();

		if (header.equals("")) {
		} else {
			long start = new Date().getTime();
			System.out.print(header);
			String method = header.split(" ")[0];
			headerTokens.put("method", method);
			headerTokens.put("path", header.split(" ")[1]);
			headerTokens.put("Host", getHostHeader(header));
			headerTokens.put("ETag", getEtagHeader(header));
			headerTokens.put("If-Modified-Since", getIfModifiedSinceHeader(header));
			headerTokens.put("If-None-Match", getIfNoneMatchHeader(header));
			if (method.equals("POST") || method.equals("PUT")) {
				String boundary = getBoundaryHeader(header);
				headerTokens.put("boundary", boundary);
				headerTokens.put("Body", getBodyRequest(header, boundary));
			}
			long end = new Date().getTime();
			long total = end - start;
		}
		return headerTokens;
	}

	/**
	 * Method to filter though a HTTP Request to find the value of the Host header.
	 * @param requestHeaders String that represents the full HTTP request of a client.
	 * @return The value for the Host-Header inside a HTTP Request
	 */
	private String getHostHeader(String requestHeaders) {
		Pattern r = Pattern.compile("Host: (.*)");
		Matcher m = r.matcher(requestHeaders);
		if (m.find()) {
			return m.group(1);
		}
		return "";
	}
	
	/**
	 * Method to filter though a HTTP Request to find the value of the ETag header.
	 * @param requestHeaders String that represents the full HTTP request of a client.
	 * @return The value for the ETag-Header inside a HTTP Request
	 */
	private String getEtagHeader(String requestHeaders) {
		Pattern r = Pattern.compile("ETag: (.*)");
		Matcher m = r.matcher(requestHeaders);
		if (m.find()) {
			return m.group(1);
		}
		return "";

	}
	
	/**
	 * Method to filter though a HTTP Request to find the value of the If-Modified-Since header.
	 * @param requestHeaders String that represents the full HTTP request of a client.
	 * @return The value for the If-Modified-Since-Header inside a HTTP Request
	 */
	private String getIfModifiedSinceHeader(String requestHeaders) {
		Pattern r = Pattern.compile("If-Modified-Since: (.*)");
		Matcher m = r.matcher(requestHeaders);
		if (m.find()) {
			return m.group(1);
		}
		return "";
	}
	
	/** 
	 * Method to filter though a HTTP Request to find the value of the If-None-Match header.
	 * @param requestHeaders String that represents the full HTTP request of a client.
	 * @return The value for the If-None-Match-Header inside a HTTP Request
	 */
	private String getIfNoneMatchHeader(String requestHeaders) {
		Pattern r = Pattern.compile("If-None-Match: (.*)");
		Matcher m = r.matcher(requestHeaders);
		if (m.find()) {
			return m.group(1);
		}
		return "";
	}
	
	/**
	 * Method to filter though a HTTP Request to find the value of boundary inside the Content-Type header.
	 * @param requestHeaders String that represents the full HTTP request of a client.
	 * @return The value value of boundary inside the Content-Type header.
	 */
	private String getBoundaryHeader(String requestHeaders) {
		Pattern r = Pattern.compile("boundary=(.*)");
		Matcher m = r.matcher(requestHeaders);
		if (m.find()) {
			return m.group(1);
		}
		return "";
	}

	/**
	 * 
	 * @param out 	The DataOutPutStream of a Socket made for a HTTP Request
	 * @param path 	The Requested path found in a HTTP GET Request
	 * @param ETag 	The ETag header found in a HTTP Request. This will be used to check if the requested file changed since last request.
	 * 				The hashcode made of the content of a file
	 * @param file 	The requested File.
	 * @throws IOException Will be thrown if the socket is closed.
	 */
	private void getFile(DataOutputStream out, String path, String ETag, File file) throws IOException {

		long contentLength = file.length();
		Date modifiedSince = new Date(file.lastModified());

		SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
		out.writeBytes("ETag:" + ETag + "\r\n");
		out.writeBytes("Last-Modified:" + dateFormat.format(modifiedSince) + "\r\n");
		out.writeBytes("\r\n");
		Files.copy(Paths.get(ROOT + path), out);
		out.flush();
	}

	/**
	 * Method to calculate the ETag of a local file.
	 * @param file local file.
	 * @return The ETag hashvalue of a local File
	 * @throws IOException if the file doesn't exist.
	 */
	private String getEtag(File file) throws IOException {
		int fileLength = (int) file.length();
		byte[] fileData = readFileData(file, fileLength);
		return calculateEtag(fileData);
	}

	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) {
				fileIn.close();
			}
		}
		return fileData;
	}

	private String calculateEtag(byte[] fileData) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(fileData);
			return bytesToHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

	private void getHeader(DataOutputStream out) throws IOException {
		out.writeBytes("HTTP/1.1 200 OK\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Connection: Keep-Alive\r\n");
		out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
	}

	private void postMethod(HashMap<String, String> headerTokens) throws IOException {
		HashMap<String, String> values = getBodyTokensPOST(headerTokens.get("Body"), headerTokens.get("bondary"));

		String contentToAdd = " <tr>\r\n" + "    <td>" + new Date() + "</td>\r\n" + "    <td>"
				+ clientConnection.getInetAddress() + "</td>\r\n" + "	<td>" + values.get("message") + "</td>\r\n"
				+ "  </tr>";
		Files.writeString(Paths.get(ROOT + POST_FILE), contentToAdd.replace("+", " ") + "\n",
				StandardOpenOption.APPEND);
		long contentLength = Files.size(Paths.get(ROOT + POST_FILE));

	}

	private String getBodyRequest(String requestHeaders, String boundary) {
		Pattern r = Pattern.compile("Content-Length: (.*)");
		Matcher m = r.matcher(requestHeaders);
		String startInt = null;
		if (m.find()) {
			startInt = m.group(1);
		}
		int bodyStart = Integer.parseInt(startInt);
		return requestHeaders.subSequence((requestHeaders.length() - bodyStart), requestHeaders.length()).toString();
	}

	private HashMap<String, String> getBodyTokensPOST(String body, String boundary) {
		HashMap<String, String> formValues = new HashMap<String, String>();
		CharSequence bodySeq = null;
		if (boundary != null) {
			bodySeq = body.subSequence(body.indexOf(boundary) + boundary.length(), body.lastIndexOf(boundary));
			
		} else {
			Pattern r = Pattern.compile("message=(.*)");
			Matcher rm = r.matcher(body);
			if (rm.find()) {
				formValues.put("message", rm.group(1));
			}
		}
		return formValues;
	}
//	Pattern b = Pattern.compile(boundary);
	//  Matcher bm = b.matcher(body);
	//	while(bm.find()) {
	//		formValues.putAll(getBodyTokens(body.toString(), boundary));
	//	}
	
	private HashMap<String, String> getBodyTokensPUT(String body, String boundary) {
		HashMap<String, String> formValues = new HashMap<String, String>();
		CharSequence bodySeq = null;
		if (boundary != null) {
			bodySeq = body.subSequence(body.indexOf(boundary) + boundary.length(), body.lastIndexOf(boundary));
			Pattern fn = Pattern.compile("filename=(.*)");
			Matcher fnm = fn.matcher(bodySeq);
			if (fnm.find()) {
				formValues.put("filename", fnm.group(1).replace('"', ' ').strip());
			}
			Pattern ct = Pattern.compile("Content-Type:(.*)");
			Matcher ctm = ct.matcher(bodySeq);
			if (ctm.find()) {
				formValues.put("Content-Type", ctm.group(1));
			}
			formValues.put("Body", body);
		} 
		return formValues;
	}

	

	private void putMethod(HashMap<String, String> headerTokens) throws Exception {
		File file = new File(ROOT + FILES_PAGE);
		file.setLastModified(new Date().getTime());
		HashMap<String, String> tokens = getBodyTokensPUT(headerTokens.get("Body"), headerTokens.get("boundary"));

		if (!checkIfValidFile(tokens.get("filename"))) {
			throw new Exception();
		} else {
			String contentType = tokens.get("Content-Type");
			String fileContent = tokens.get("Body");
			String filename = tokens.get("filename");
			boolean exist = Files.exists(Path.of(ROOT + "/files/" + filename), LinkOption.NOFOLLOW_LINKS);
			FileWriter myWriter = new FileWriter(ROOT + "/files/" + filename);
			myWriter.write(fileContent);
			myWriter.close();
			if (!exist) {
				updateFiles(filename);
			}
		}

	}

	private void updateFiles(String filename) throws IOException {
		String contentToAdd = " <tr>\r\n" + "    <td>" + new Date() + "</td>\r\n" + "    <td>"
				+ clientConnection.getInetAddress() + "</td>\r\n" + "	<td> <a href=\"/files/" + filename + "\"> "
				+ filename + "</a> </td>\r\n" + "  </tr>";

		Files.writeString(Paths.get(ROOT + FILES_LIST), contentToAdd, StandardOpenOption.APPEND);
	}

	private void getNotModifiedHeader(DataOutputStream out) throws IOException {
		System.out.print("not modified");
		out.writeBytes("HTTP/1.1 304 Not Modified\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: Keep-Alive\r\n");
		out.writeBytes("\r\n");
		out.flush();
	}

	private void getFileNotFoundHeader(BufferedReader in, DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_404));
		out.writeBytes("HTTP/1.1 404 \r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		out.writeBytes(Files.readString(Paths.get(ROOT + ERROR_FILE_404)));// AANPASSEN
		out.flush();
	}

	private void getBadRequestHeader(BufferedReader in, DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_400));
		out.writeBytes("HTTP/1.1 400 Bad Request \r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		out.writeBytes(Files.readString(Paths.get(ROOT + ERROR_FILE_400)));// AANPASSEN
		out.flush();
	}

	private void getNotImplementedHeader(DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_501));
		String contentMimeType = "text/html";
		out.writeBytes("HTTP/1.1 501 Not Implemented\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		Files.copy(Paths.get(ROOT + ERROR_FILE_501), out);
		out.flush();
	}

	private String getMimeType(String path) {
		if (path.endsWith(".html"))
			return "text/html";
		else if (path.endsWith(".css"))
			return "text/css";
		else if (path.endsWith(".png"))
			return "image/png";
		else if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
			return "image/jpeg";
		else if (path.endsWith(".js"))
			return "text/javascript";
		else if (path.endsWith(".ico"))
			return "image/x-icon";
		else
			return null;
	}

	private boolean checkIfValidFile(String filename) {
		if (filename.endsWith("txt") || filename.endsWith("html") || filename.endsWith("rtf"))
			return true;
		return false;
	}

	private boolean checkIfValidRequest(HashMap<String, String> headerTokens) {
		if (headerTokens.get("Host").equals("")) {
			return false;
		}
		return true;
	}
}