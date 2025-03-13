package ru.biosoft.bigbed;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OffsetSize {
	long offset, size;
	public OffsetSize(long offset, long size) {
		this.offset = offset;
		this.size = size;
	}
	
	OffsetSize() {}
	
	

	public static OffsetSize parse(byte[] raw, ByteOrder order)
	{
		ByteBuffer buf = ByteBuffer.wrap(raw).order(order);
		long offset = buf.getLong();
		long size = buf.getLong();
		Utils.checkULong(offset);
		Utils.checkULong(size);
		return new OffsetSize(offset, size);
	}
}
