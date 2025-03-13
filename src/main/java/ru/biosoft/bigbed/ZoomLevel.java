package ru.biosoft.bigbed;

import java.io.IOException;

public class ZoomLevel {
	int reductionLevel;
	int reserved;
	long dataOffset;
	long indexOffset;

	void read(DataSource b) throws IOException
	{
		reductionLevel = b.readUInt();
	    reserved = b.readInt();
	    dataOffset = b.readULong();
	    indexOffset = b.readULong();
	}
}
