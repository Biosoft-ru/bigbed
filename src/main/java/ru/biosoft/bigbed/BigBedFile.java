package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class BigBedFile extends BigFile<BedEntry> {
	
	//extension
	int extensionSize;
	int extraIndexCount;
	long extraIndexListOffset;
	
	//TODO: support siteCount > Integer.MAX_VALUE
	int siteCount;
	
	public static BigBedFile read(String source) throws ParseException, IOException
	{
		BigBedFile res = new BigBedFile(source);
		res.read();
		return res;
	}
	
	BigBedFile(String source)
	{
		super(source);
	}
	
	void read() throws IOException, ParseException
	{
		super.read();
				
		readAutoSql();
		readExtraIndices();
		
		dataSource.seek(header.unzoomedDataOffset);
		siteCount = (int) dataSource.readULong();
	}

	public int getSiteCount()
	{
		return siteCount;
	}
	
	@Override
	protected boolean parseBlock(int chromId, int start, int end, int maxItems, ByteBuffer block, List<BedEntry> result) {
		while(block.hasRemaining()) {
			int resChrId = Utils.readUInt(block);
			int resStart = Utils.readUInt(block);
			int resEnd = Utils.readUInt(block);

			block.mark();
			int restLen = 0;
			while(block.hasRemaining() && block.get() != 0)
				restLen++;
			
			 if (resChrId == chromId
		      && ( (resStart < end && resEnd > start)
		                // Make sure to include zero-length insertion elements at start or end:
		           || (resStart == resEnd && (resStart == end || resEnd == start))) )
		     {
		         BedEntry entry = new BedEntry(resChrId, resStart, resEnd);
		         block.reset();
		         entry.data = new byte[restLen];
		         block.get(entry.data);
		         block.get();//skip zero terminator
		         result.add(entry);
		         if(maxItems > 0 && result.size() >= maxItems)
		        	 return false;
		      }

		}
		if(maxItems > 0 && result.size() >= maxItems)
			return false;
		return true;
		
	}
	

	private AutoSql autoSql;
	public AutoSql getAutoSql() {
		return autoSql;
	}
	private void readAutoSql() throws IOException {
		if(header.autoSqlOffset == 0L)
			return;
		dataSource.seek(header.autoSqlOffset);
		StringBuilder sb = new StringBuilder();
		int c;
		//read C-style string
		while((c=dataSource.read()) > 0)
			sb.append((char)c);
		autoSql = AutoSql.parse(sb.toString());
	}
	
	private List<ExtraIndex> extraIndices;
	public List<ExtraIndex> getExtraIndices() {
		return extraIndices;
	}
	
	private void readExtraIndices() throws IOException
	{
		if(header.extensionOffset == 0L)
			return;
		extraIndices = new ArrayList<>();
		dataSource.seek(header.extensionOffset);
		dataSource.readUShortAsInt();//extension size
		int extraIndexCount = dataSource.readUShortAsInt();
		long extraIndexListOffset = dataSource.readULong();

		dataSource.seek(extraIndexListOffset);
		for (int i=0; i < extraIndexCount; i++) {
			int type = dataSource.readUShortAsInt();
			int fieldCount = dataSource.readUShortAsInt();
			
			ExtraIndex extraIndex = new ExtraIndex();
			extraIndex.fileOffset = dataSource.readULong();
			
			dataSource.readInt();//skip 4 reserved bytes
			if (type != 0) {
				throw new ParseException("Don't understand extra index type " + type);
			}
			if (fieldCount != 1) {
				throw new ParseException("Indexes on multiple fields are not supported");
			}
			extraIndex.colIdx = dataSource.readUShortAsInt();
			extraIndex.name = autoSql == null ? null : autoSql.columns.get(extraIndex.colIdx).name;
			extraIndices.add(extraIndex);
			dataSource.readShort();//skip 2 reserved bytes	
		}
		
	}
	
	public List<BedEntry> queryExtraIndex(String indexName, String query, int maxItems) throws IOException
	{
		if(autoSql == null)
			throw new ParseException("Can not query extra index without autosql");
		int colIdx = -1;
		for(int i = 0; i < autoSql.columns.size(); i++)
		{
			if(autoSql.columns.get(i).name.equals(indexName))
			{
				colIdx = i;
				break;
			}
		}
		if(colIdx == -1)
			throw new ParseException("Index " + indexName + " not found");
		return queryExtraIndex(colIdx, query, maxItems);		
	}
	
	public List<BedEntry> queryExtraIndex(int colIdx, String query, int maxItems) throws IOException
	{
		ExtraIndex extraIndex = null;
		for(ExtraIndex e : extraIndices)
			if(e.colIdx == colIdx)
			{
				extraIndex = e;
				break;
			}
		if(extraIndex == null)
			throw new ParseException("Column " + colIdx + " is not indexed");
		return queryExtraIndex(extraIndex, query, maxItems);
	}
	
	public List<BedEntry> queryExtraIndex(ExtraIndex extraIndex, String query, int maxItems) throws IOException
	{
		List<BedEntry> result = new ArrayList<BedEntry>();
		
		BPlusTree bpTree = new BPlusTree(dataSource, extraIndex.fileOffset);
		
		List<OffsetSize> blocks = new ArrayList<>();
		bpTree.findMulti(query.getBytes(), rawVal->{
			OffsetSize block = OffsetSize.parse(rawVal, dataSource.order());
			blocks.add(block);
		});
		
		if(blocks.size() == 0)
			return Collections.emptyList();
		
		Comparator<OffsetSize> cmp = Comparator.<OffsetSize>comparingLong(b->b.offset).thenComparingLong(b->b.size);
		blocks.sort(cmp);
		List<OffsetSize> uBlocks = new ArrayList<>();
		OffsetSize prev = blocks.get(0);
		uBlocks.add(prev);
		for(int i = 1; i < blocks.size(); i++)
		{
			OffsetSize cur = blocks.get(i);
			if(cmp.compare(prev, cur) != 0)
				uBlocks.add(cur);
			prev = cur;
		}
		
		byte[] uncompressedBuf = null;
		if(header.uncompressBufSize > 0)
			uncompressedBuf = new byte[header.uncompressBufSize];
		
		for(OffsetSize block : uBlocks)
		{
			dataSource.seek(block.offset);
			int bufLength = (int)block.size;
			byte[] buf = new byte[bufLength];
			dataSource.readFully(buf, 0, bufLength);
    		if(uncompressedBuf != null)
    		{
    			bufLength = Utils.uncompress(buf, 0, bufLength, uncompressedBuf);
    			buf = uncompressedBuf;
    		}
    		
    		ByteBuffer bb = ByteBuffer.wrap(buf, 0, bufLength);
    		bb.order(dataSource.order());
    		
    		while(bb.hasRemaining()) {
    			int resChrId = Utils.readUInt(bb);
    			int resStart = Utils.readUInt(bb);
    			int resEnd = Utils.readUInt(bb);

    			BedEntry entry = new BedEntry(resChrId, resStart, resEnd);
    			
    			bb.mark();
    			int restLen = 0;
    			while(bb.hasRemaining() && bb.get() != 0)
    				restLen++;
    			bb.reset();
		        entry.data = new byte[restLen];
		        bb.get(entry.data);
		        bb.get();//skip zero terminator
    			
		        
		        String data = new String(entry.data);
		        String[] fields = data.split("\t");
		        if(query.equals(fields[extraIndex.colIdx - 3]))
    		    {
    				result.add(entry);
    		        if(maxItems > 0 && result.size() >= maxItems)
    		        	return result;
    		    }

    		}
    		
		}
		
		return result;			
	}

	@Override
	protected int getFileMagic() {
		return Header.MAGIC_BIGBED;
	}
}
