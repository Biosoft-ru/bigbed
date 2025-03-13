package ru.biosoft.bigbed;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {
	public static int readUInt(ByteBuffer b)
	{
		int res = b.getInt();
		checkUInt(res);
		return res;
	}
	
	public static long readULong(ByteBuffer b)
	{
		long res = b.getLong();
	    checkULong(res);
		return res;
	}
	
	public static void checkUInt(int x)
	{
		if(x < 0)
			throw new ParseException("Int value to big for java signed int type: " + Integer.toUnsignedString(x));
	}
	
	public static void checkULong(long x)
	{
		if(x < 0)
			throw new ParseException("Long value to big for java signed long type: " + Long.toUnsignedString(x));
	}

	
	public static int uncompress(byte[] compressed, int compressedOffset, int compressedLength, byte[] uncompressedBuf) throws IOException {
		/*InflaterInputStream z = new InflaterInputStream(new ByteArrayInputStream(buf, bufStart, bufLength));
		int n = 0;
		int total = 0;
		while((n = z.read(uncompressedBuf, total, uncompressedBuf.length-total)) > 0)
			total += n;
		return total;
		*/
		Inflater inflater = new Inflater();
        inflater.setInput( compressed, compressedOffset, compressedLength );
        int uncompressedLen;
		try {
			uncompressedLen = inflater.inflate( uncompressedBuf );
		} catch (DataFormatException e) {
			throw new ParseException(e);
		}
        if(inflater.getRemaining() > 0)
            throw new ParseException("Extra bytes after compressed block");
        return uncompressedLen;
	}
	
	public static byte[] compress(byte[] input) {
		 byte[] output = new byte[input.length];
		 Deflater compresser = new Deflater();
		 compresser.setInput(input);
		 compresser.finish();
		 int compressedDataLength = 0;
		 while(!compresser.finished())
		 {
			 compressedDataLength += compresser.deflate(output, compressedDataLength, output.length-compressedDataLength);
			 if(compressedDataLength >= output.length)
				 output = Arrays.copyOf(output, output.length*2+1);
		 }
		 compresser.end();
		 if(output.length != compressedDataLength)
			 output = Arrays.copyOf(output, compressedDataLength);
		return output;
	}
	
	
	public static void writeNBytes(DataOutput out, byte val, int n) throws IOException
	{
		for (int k = 0; k < n; k++)
			out.writeByte(val);
	}
	
}
