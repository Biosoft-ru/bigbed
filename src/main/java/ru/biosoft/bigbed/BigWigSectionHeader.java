package ru.biosoft.bigbed;

import java.nio.ByteBuffer;

public class BigWigSectionHeader {

	int chromId;
	int start, end;
	int itemStep;
	int itemSpan;
	
	enum Type {
		BED_GRAPH,
		VARIABLE_STEP,
		FIXED_STEP;
		public static Type decode(int val)
		{
			switch(val)
			{
			case 1: return BED_GRAPH;
			case 2: return VARIABLE_STEP;
			case 3: return FIXED_STEP;
			default:
				throw new AssertionError(val);
			}
		}
	}
	Type type;
	
	byte reserved;
	int itemCount;//2byte 
	
	public void read(ByteBuffer b)
	{
		chromId = Utils.readUInt(b);
		start = Utils.readUInt(b);
		end = Utils.readUInt(b);
		itemStep = Utils.readUInt(b);
		itemSpan = Utils.readUInt(b);
		type = Type.decode(b.get());
		reserved = b.get();
		itemCount = Short.toUnsignedInt(b.getShort());
	}
}
