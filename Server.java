import java.io.*;

public class Server {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("Need a port number");
			System.exit(0);
		}

		// new QuoteServerThread().start();
		System.out.println("Waiting on port " + args[0] + "...");
		NoBlockingUDPThread noThread = new NoBlockingUDPThread(args[0]);
		noThread.start();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String msg;
		try {
			while ((msg = reader.readLine()) != null && !msg.equals("q")) {
			}

			noThread.quit();
		} catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
		}
	}

}
