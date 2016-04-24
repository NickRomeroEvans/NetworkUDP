import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;


/**
 * Utility class for building and parsing messages.
 * 
 *  Takes raw components and generates a ByteBuffer.
 *  Provides method to take a processed ByteBuffer and turn it into a Message object
 */
public class MessageUtils {
	
	private MessageUtils() {}
	
	private static final byte[] MAGIC_VERSION = {(byte) 0xC4, 0x61, 1}; 
	
	private static Charset MESSAGE_CHARSET = Charset.forName("UTF-8");

	public static final byte HELLO = 0;
	public static final byte DATA = 1;
	public static final byte ALIVE = 2;
	public static final byte GOODBYE = 3;
	
	public static class Message {
		private boolean isValid = false;
		private byte version = 0;
		private byte command = -1;
		private int sequence = 0;
		private int sessionId = 0;
		private String data = null;
		

		public boolean isValid() {
			return isValid;
		}

		public byte getVersion() {
			return version;
		}

		public byte getCommand() {
			return command;
		}

		public int getSequence() {
			return sequence;
		}

		public int getSessionId() {
			return sessionId;
		}

		public String getData() {
			return data;
		}

		private Message() {}
		
		/**
		 * Internal Factory Method to build Message objects from a buffer.  An invalid buffer will have "isValid" set to false
		 * 
		 * @param buffer
		 * @return
		 */
		static Message parseMessageBuffer(ByteBuffer buffer) {
			Message result = new Message();
			try {
				result.isValid = (MAGIC_VERSION[0] == buffer.get() && MAGIC_VERSION[1] == buffer.get() && MAGIC_VERSION[2] == buffer.get());
				// Advance in case we short circuited the validity check
				buffer.position(3);
				result.command = buffer.get();
				result.sequence = buffer.getInt();
				result.sessionId = buffer.getInt();
				if (result.isValid && result.command == DATA && buffer.hasRemaining()) {
					result.data = MESSAGE_CHARSET.decode(buffer).toString();
				}
			} catch (BufferUnderflowException e) {
				result.isValid = false;
			}
			return result;
		}
	}
	
	public static Message parseMessageBuffer(byte[] buffer, int length) {
        ByteBuffer bbuf = ByteBuffer.wrap(buffer, 0, length);  // <-- note we may not have data in the whole buffer
        return  parseMessageBuffer(bbuf);
	}
	
	/**
	 * Given a ByteBuffer composed of our message elements, turn it into a useful parsed object
	 * 
	 * @param buffer
	 * @return
	 */
	public static Message parseMessageBuffer(ByteBuffer buffer) {
		return Message.parseMessageBuffer(buffer);
	}
	
	/**
	 * Take components and compose a ByteBuffer that can shipped through a datagram
	 * 
	 * @param command
	 * @param sequence
	 * @param sessionId
	 * @return
	 */
	public static ByteBuffer buildMessage(byte command, int sequence, int sessionId) {
		return buildMessage(command, sequence, sessionId, null);
	}
	
	/**
	 * Build a message with an embedded data String.  Note that data string will only be
	 * included if the command is a "DATA" command
	 * 
	 * @param command
	 * @param sequence
	 * @param sessionId
	 * @param data
	 * @return
	 */
	public static ByteBuffer buildMessage(byte command, int sequence, int sessionId, String data) {
    	ByteBuffer message = ByteBuffer.allocate(1024);
    	message.put(MAGIC_VERSION);
        message.put(command); ; // command equals command
        message.putInt(sequence);
		message.putInt(sessionId);
		
		if (command == DATA && data != null) {
			message.put(MESSAGE_CHARSET.encode(data));
		}
		
		return message;
	}
	
}
