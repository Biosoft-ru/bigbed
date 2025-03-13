package ru.biosoft.bigbed;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteOrder;

public interface DataSource extends Closeable {
	void open(String source) throws IOException;
	void close() throws IOException;
	
	ByteOrder order();
	void setOrder(ByteOrder order);
	
	void seek(long pos) throws IOException;
	void skip(int n) throws IOException;
	long tell() throws IOException;

	int read() throws IOException;
	byte readByte() throws IOException;
	
	short readShort() throws IOException;
	int readUShortAsInt() throws IOException;
	
	int readInt() throws IOException;
	int readUInt() throws IOException;
	long readLong() throws IOException;
	long readULong() throws IOException;
	
	double readDouble() throws IOException;
	
	void readFully(byte[] buf, int offset, int length) throws IOException;
	
	//read up to @size bytes from file at offset @fileOffset to @data[dataOffset...] array  
	//int read(long fileOffset, byte[] data, int dataOffset, int size) throws IOException;
}
