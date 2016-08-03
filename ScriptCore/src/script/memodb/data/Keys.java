package script.memodb.data;

import java.util.Map;

public class Keys extends Blob{
	/**
	 * Id of data the Keys stand for. 
	 */
	public String id;
	/**
	 * The count of keys for this data. 
	 */
	public short keyCount;
	/**
	 * Map of each key. 
	 * kv, id : Key
	 */
	public Map<String, Key> keyMap;
	public class Key {
		/**
		 * Length of key
		 */
		public short keyLength;
		/**
		 * Key in string
		 */
		public String key;
		/**
		 * Chunk file number 
		 */
		public int chunkFileNumber;
		/**
		 * Chunk address in Chunk file specified by chunkFileName. 
		 */
		public int chunkAddress;
		/**
		 * The count of all chunks. 
		 */
		public int chunkCount;
	}
}