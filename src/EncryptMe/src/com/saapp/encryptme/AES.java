package com.saapp.encryptme;

import android.annotation.TargetApi;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES {
	private static final int saltByteSize = 64/8;
	private static final int ivByteSize = 128/8;
	private static final int keySize = 256/8;
	@TargetApi(9)
	public static String encrypt(String text, String seed) throws Exception{
		byte[] salt = new byte[saltByteSize];
		new Random().nextBytes(salt);
		byte[] raw = getRawKey(seed.getBytes(), salt);
		byte[] rawKey = Arrays.copyOfRange(raw, 0, keySize);
		SecretKeySpec skeySpec = new SecretKeySpec(rawKey, "AES"); //"AES"		
		
		byte[] iv = Arrays.copyOfRange(raw, keySize, keySize+ivByteSize);
		// Instantiate the cipher
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");		
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(iv));
        
        byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
        return asHex(salt) + asHex(cipher.getIV()) + asHex(encrypted);
	}

	@TargetApi(9)
	public static String decrypt(String encryptedText, String seed) throws Exception{
		byte[] salt = hexToBytes(encryptedText.substring(0, saltByteSize*2));
		byte[] raw = getRawKey(seed.getBytes(), salt);
		byte[] rawKey = Arrays.copyOfRange(raw, 0, keySize);
		SecretKeySpec skeySpec = new SecretKeySpec(rawKey, "AES"); //"AES"
		
		byte[] iv = Arrays.copyOfRange(raw, keySize, keySize+ivByteSize);
		
		// Instantiate the cipher
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(iv));
        byte[] original = cipher.doFinal(hexToBytes(encryptedText.substring((saltByteSize+ivByteSize)*2)));
        return new String(original, "UTF-8");
	}
	
	 private static byte[] getRawKey(byte[] seed, byte[] salt) throws Exception {
		 //hash with "MD5"
		 int totalSize = keySize + ivByteSize;
		 System.out.println("salt = " + asHex(salt));
		 byte[] derivedKey = new byte[totalSize];
		 MessageDigest md = MessageDigest.getInstance("MD5");//MD5 generate a 128 bit hash -> 128/8=16 byte
		 int derivedKeySize = 0;
		 byte[] lastMD5Result = null;
		 while(derivedKeySize < totalSize){
			 if(lastMD5Result != null){
				 md.update(lastMD5Result);
			 }
			 md.update(seed);
			 md.update(salt);
			 lastMD5Result = md.digest();
			 System.arraycopy(lastMD5Result, 0, derivedKey, derivedKeySize, lastMD5Result.length);
			 derivedKeySize = derivedKeySize + lastMD5Result.length;
		 }
		 return derivedKey;
     }
	 
	
	 /**
	     * Turns array of bytes into string
	     *
	     * @param buf	Array of bytes to convert to hex string
	     * @return	Generated hex string
	     */
	     public static String asHex (byte[] buf) {
	      StringBuilder strbuf = new StringBuilder(buf.length * 2);
	      int i;

	      for (i = 0; i < buf.length; i++) {
	       if (((int) buf[i] & 0xff) < 0x10) //byte is from -128 to 128, (int)buf[i] & 0xff is to convert byte to [0, 255]
		    strbuf.append("0");

	       strbuf.append(Integer.toHexString((int) buf[i] & 0xff));
	      }

	      return strbuf.toString();
	     }
	     
	     /**
	      * Turns hex string into array of bytes
	      * @param hexString 
	      * @return array of bytes
	      */
	     public static byte[] hexToBytes (String hexString){
			 byte[] buf = new byte[hexString.length()/2];
			 for(int i=0; i<hexString.length(); i=i+2){
				 buf[i/2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + (Character.digit(hexString.charAt(i+1), 16)));
			 }
			 return buf;
		 }
}