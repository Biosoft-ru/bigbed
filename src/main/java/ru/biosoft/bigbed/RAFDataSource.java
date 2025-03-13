package ru.biosoft.bigbed;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

public class RAFDataSource implements DataSource {

	private RandomAccessFile file;
	
	@Override
	public void open(String source) throws IOException {
		file = new RandomAccessFile(source, "r");
	}

	@Override
	public void close() throws IOException {
		file.close();
	}

	@Override
	public void seek(long pos) throws IOException {
		file.seek(pos);
	}

	@Override
	public void skip(int n) throws IOException {
		int skipped = file.skipBytes(n);
		if(skipped != n)
			throw new EOFException();
	}
	
	@Override
	public long tell() throws IOException {
		return file.getFilePointer();
	}

	ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;//RandomAccessFile is in BIG_ENDIAN
	
	public ByteOrder order() { return byteOrder; }
	public void setOrder(ByteOrder order) {this.byteOrder = order;}

	@Override
	public int read() throws IOException {
		return file.read();
	}
	
	@Override
	public byte readByte() throws IOException {
		return file.readByte();
	}
	
	@Override
	public int readInt() throws IOException {
		int res = file.readInt();
		if(byteOrder != ByteOrder.BIG_ENDIAN)
			res = Integer.reverseBytes(res);
		return res;
	}

	@Override
	public int readUInt() throws IOException {
		int res = readInt();
		if(res < 0)
			throw new ParseException("Int value is too big for java signed int type: " + Integer.toUnsignedString(res));
		return res;
	}
	
	@Override
	public short readShort() throws IOException {
		short res = file.readShort();
		if(byteOrder != ByteOrder.BIG_ENDIAN)
			res = Short.reverseBytes(res);
		return res;
	}

	@Override
	public int readUShortAsInt() throws IOException {
		return Short.toUnsignedInt(readShort());
	}

	@Override
	public long readLong() throws IOException {
		long res = file.readLong();
		if(byteOrder != ByteOrder.BIG_ENDIAN)
			res = Long.reverseBytes(res);
		return res;
	}
	
	@Override
	public long readULong() throws IOException {
		long res = readLong();
		if(res < 0)
			throw new ParseException("Long value is too big for java signed long type: " + Long.toUnsignedString(res));
		return res;
	}
	
	@Override
	public double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}


	@Override
	public void readFully(byte[] buf, int offset, int length) throws IOException {
		file.readFully(buf, offset, length);
	}



}
