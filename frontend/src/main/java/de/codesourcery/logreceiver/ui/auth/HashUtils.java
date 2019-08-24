package de.codesourcery.logreceiver.ui.auth;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class HashUtils
{
    private static final String HEX = "0123456789abcdef";
    private static final char[] HEX_CHARS = HEX.toCharArray();

    private static final ThreadLocal<SecureRandom> random = ThreadLocal.withInitial( SecureRandom::new );

    private static final int iterations = 10000;
    private static final int keyLength = 512;

    public static byte[] fromHexString(String s)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for ( int i = 0 ; i < s.length() ; i+= 2 ) {
            char hi = s.charAt(i);
            char lo = s.charAt(i+1);
            int hiValue = HEX.indexOf( hi );
            int loValue = HEX.indexOf( lo );
            out.write( (hiValue<<4)|loValue);
        }
        return out.toByteArray();
    }

    public static String hex(byte[] data)
    {
        final StringBuilder result = new StringBuilder();
        for ( byte b : data ) {
            int lo = b & 0x0f;
            int hi = (b & 0xf0) >>> 4;
            result.append( HEX_CHARS[hi] ).append( HEX_CHARS[lo] );
        }
        return result.toString();
    }

    public static boolean comparePasswords(String password,String hashedPassword)
    {
        final String salt = hashedPassword.split(":")[2];
        final byte[] saltBytes = fromHexString( salt );
        final String actualHash = hashPassword( password, saltBytes );
        return hashedPassword.equals( actualHash );
    }

    public static String hashPassword(String password) {
        final byte[] saltBytes = new byte[8];
        random.get().nextBytes( saltBytes );
        return hashPassword( password, saltBytes );
    }

    private static String hashPassword(String password,byte[] saltBytes)
    {
        try
        {
            final char[] passwordChars = password.toCharArray();
            final byte[] hashedBytes = hashPassword( passwordChars, saltBytes );
            return "PBKDF2_10000_512:"+hex( hashedBytes )+":"+hex(saltBytes);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            throw new RuntimeException( e );
        }
    }

    private static byte[] hashPassword(final char[] password, final byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        final SecretKeyFactory skf = SecretKeyFactory.getInstance( "PBKDF2WithHmacSHA512" );
        final PBEKeySpec spec = new PBEKeySpec( password, salt, iterations, keyLength );
        final SecretKey key = skf.generateSecret( spec );
        return key.getEncoded();
    }
}