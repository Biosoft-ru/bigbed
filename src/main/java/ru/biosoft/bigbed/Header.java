package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteOrder;

public class Header {
	///////Fields stored in file
	int magic;
	int version;//16bit unsigned
	int zoomLevels;//16bit unsigned
	long chromTreeOffset;
	long unzoomedDataOffset;
	long unzoomedIndexOffset;
	int fieldCount;//16bit unsigned
	int definedFieldCount;//16bit unsigned
	long autoSqlOffset;
	long totalSummaryOffset;
	int uncompressBufSize;
	long extensionOffset;
	//////////////
	
	static int MAGIC_BIGBED = 0x8789F2EB;
	static int MAGIC_BIGWIG = 0x888FFC26;
	
	void read(DataSource b, int expectedMagic) throws IOException
	{
		magic = b.readInt();
		if(magic != expectedMagic)
			if(Integer.reverseBytes(magic) == expectedMagic)
				b.setOrder(b.order() == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
			else
				throw new ParseException("Not a BigBed file, magic bytes 0x" + Integer.toHexString(magic));
		version = b.readUShortAsInt();
		zoomLevels = b.readUShortAsInt();
		chromTreeOffset = b.readULong();
		unzoomedDataOffset = b.readULong();
		unzoomedIndexOffset = b.readULong();
		fieldCount = b.readUShortAsInt();
		definedFieldCount = b.readUShortAsInt();
		autoSqlOffset = b.readULong();
		totalSummaryOffset = b.readULong();
		uncompressBufSize = b.readUInt();
		extensionOffset = b.readULong();
	}
}
