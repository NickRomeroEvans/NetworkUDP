/*

 */

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ClientUDP {

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.out.println("Usage: java Client <hostname> and <port>");
			return;
		}

		Random rand = new Random();
		int session = rand.nextInt(Integer.MAX_VALUE);

		// Send Hello
		Client client = new Client(args[0], args[1], session);
		client.start();

		// magic version command sequence number session id 12 bytes total
		// 2 bytes 1 byte 1 byte 4 bytes 4 bytes

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String msg;
		try {
			// Wait while we get a handshake in order.
			while (!client.handShake) {
				// Waiting for the handshake
				Thread.sleep(2);
			}
			while ((msg = reader.readLine()) != null && !msg.equals("q")) {
				client.processInput(msg);
				TimeUnit.MICROSECONDS.sleep(100); // Give server time to process
			}
			if (msg == null) {
				System.out.println("eof");
			}

			// Send Goodbye
			client.sendGoodBye();
			// TODO Wait for goodbye or timeout
		} catch (IOException | InterruptedException e) {
			System.err.println("Caught Exception: " + e.getMessage());
		}
		System.exit(0);
	}

}
