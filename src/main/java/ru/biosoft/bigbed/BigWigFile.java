package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class BigWigFile extends BigFile<WigEntry> {
	
	public static BigWigFile read(String source) throws ParseException, IOException
	{
		BigWigFile res = new BigWigFile(source);
		res.read();
		return res;
	}
	
	BigWigFile(String source)
	{
		super(source);
	}
	

	@Override
	protected boolean parseBlock(int chromId, int qStart, int qEnd, int maxItems, ByteBuffer block, List<WigEntry> resultList) {
		BigWigSectionHeader head = new BigWigSectionHeader();
		head.read(block);
		int s = head.start-head.itemStep;
		int e = s+head.itemSpan;
		for(int i = 0; i < head.itemCount; i++)
		{
			switch(head.type)
			{
			case BED_GRAPH:
				s = Utils.readUInt(block);
				e = Utils.readUInt(block);
				break;
			case VARIABLE_STEP:
				s = Utils.readUInt(block);
				e = s + head.itemSpan;
				break;
			case FIXED_STEP:
				s += head.itemStep;
				e = s + head.itemSpan;
				break;
			default:
				throw new AssertionError();
			}
			float val = block.getFloat();
			
			if(s < e && s < qEnd && qStart < e)
			{
				resultList.add(new WigEntry(chromId, s, e, val));
				if(maxItems > 0 && resultList.size() >= maxItems)
					return false;
			}
		}
		if(maxItems > 0 && resultList.size() >= maxItems)
			return false;
		return true;
	}

	@Override
	protected int getFileMagic() {
		return Header.MAGIC_BIGWIG;
	}
	
}
