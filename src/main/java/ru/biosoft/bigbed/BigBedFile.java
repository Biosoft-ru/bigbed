package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class BigBedFile implements AutoCloseable {
	static final int VERSION = 4;
	
	String source;
	DataSource dataSource;
	Header header;
	
	public static final int MAX_ZOOM_LEVELS = 10;
	public static final int ZOOM_INCREMENT = 4;
	List<ZoomLevel> zoomLevels; 
	
	SummaryElement totalSummary;
	
	BPlusTree bPlusTree; 
	RTreeIndex  rTree;
	
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
		this.source = source;
		dataSource = new RAFDataSource();
	}
	
	void read() throws IOException, ParseException
	{
		dataSource.open(source);
		
		header = new Header();
		header.read(dataSource);

		dataSource.seek(64);
		zoomLevels = new ArrayList<>(header.zoomLevels);
		for (int i = 0; i < header.zoomLevels; i++) {
			ZoomLevel zoomLevel = new ZoomLevel();
			zoomLevel.read(dataSource);
			zoomLevels.add(zoomLevel);
		}
		
		readAutoSql();
		readTotalSummary();
		readExtraIndices();
		
		dataSource.seek(header.unzoomedDataOffset);
		siteCount = (int) dataSource.readULong();
		
		bPlusTree = new BPlusTree(dataSource, header.chromTreeOffset);
	}
	
	private void readTotalSummary() throws IOException {
		if(header.totalSummaryOffset == 0)
			return;
		dataSource.seek(header.totalSummaryOffset);
		totalSummary = new SummaryElement();
		totalSummary.read(dataSource);
	}
	
	public SummaryElement getTotalSummary()
	{
		return totalSummary;
	}


	public int getSiteCount()
	{
		return siteCount;
	}
	
	void initRTree() throws IOException
	{
		if(rTree == null)
		{
			rTree = new RTreeIndex();
			rTree.read(dataSource, header.unzoomedIndexOffset);
		}
	}
	
	public List<BedEntry> queryIntervals(int chromId, int start, int end, int maxItems) throws IOException {

		List<BedEntry> result = new ArrayList<>();
		initRTree();
		
		
		
		
		int paddedStart = (start > 0) ? start-1 : start;
		int paddedEnd = end+1;
		
		List<OffsetSize> blockList = new ArrayList<>();
		rTree.findOverlappingBlocks(chromId, paddedStart, paddedEnd, dataSource, blockList::add);
		
		
		byte[] uncompressedBuf = null;
		if(header.uncompressBufSize > 0)
			uncompressedBuf = new byte[header.uncompressBufSize];
		
		for (int iblock=0; iblock < blockList.size(); )
	    {
			/* Find contiguous blocks and read them into mergedBuf. */
			int nBlocks = findContiguousBlocks(blockList, iblock);
			OffsetSize firstBlock = blockList.get(iblock);
			long mergedOffset = firstBlock.offset;
			OffsetSize lastBlock = blockList.get(iblock+nBlocks-1);
	        long mergedSize = lastBlock.offset + lastBlock.size - mergedOffset;
	        if(mergedSize > Integer.MAX_VALUE)
	        	throw new ParseException("Merged size too big: " + mergedSize);
	    	dataSource.seek(mergedOffset);
	    	byte[] mergedBuf = new byte[(int)mergedSize];
	    	dataSource.readFully(mergedBuf, 0, (int)mergedSize);

	    	for(int i = 0; i < nBlocks; i++)
	    	{
	    		byte[] buf;
	    		int bufStart, bufLength;

	    		OffsetSize curBlock = blockList.get(iblock+i);
	    		
	    		buf = mergedBuf;
    			bufStart = (int)(curBlock.offset-firstBlock.offset);
    			bufLength = (int)curBlock.size;
    			
	    		if(uncompressedBuf != null)
	    		{
	    			bufLength = Utils.uncompress(buf, bufStart, bufLength, uncompressedBuf);
	    			buf = uncompressedBuf;
	    			bufStart = 0;
	    		}
	    		
	    		ByteBuffer bb = ByteBuffer.wrap(buf, bufStart, bufLength);
	    		bb.order(dataSource.order());
	    		
	    		while(bb.hasRemaining()) {
	    			int resChrId = Utils.readUInt(bb);
	    			int resStart = Utils.readUInt(bb);
	    			int resEnd = Utils.readUInt(bb);

	    			bb.mark();
	    			int restLen = 0;
	    			while(bb.hasRemaining() && bb.get() != 0)
	    				restLen++;
	    			
	    			 if (resChrId == chromId
	    		      && ( (resStart < end && resEnd > start)
	    		                // Make sure to include zero-length insertion elements at start or end:
	    		           || (resStart == resEnd && (resStart == end || resEnd == start))) )
	    		     {
	    		         BedEntry entry = new BedEntry(resChrId, resStart, resEnd);
	    		         bb.reset();
	    		         entry.data = new byte[restLen];
	    		         bb.get(entry.data);
	    		         bb.get();//skip zero terminator
	    		         result.add(entry);
	    		         if(maxItems > 0 && result.size() >= maxItems)
	    		        	 return result;
	    		      }

	    		}
	    		
	    	}
	    	iblock += nBlocks;
	    
	    }
		
		return result;
	}
	
	public List<BedEntry> queryIntervals(String chrom, int start, int end, int maxItems) throws IOException {
		ChromInfo chrInfo = getChromInfo(chrom);
		return queryIntervals(chrInfo.id, start, end, maxItems);
	}
	

	//Return number of contiguous blocks starting at iblock
	private int findContiguousBlocks(List<OffsetSize> blockList, int iblock) {
		for(int i = iblock + 1; i < blockList.size(); i++)
			if(blockList.get(i).offset != blockList.get(i-1).offset + blockList.get(i-1).size)
				return i-iblock;
		return blockList.size() - iblock;
	}

	
	public ChromInfo getChromInfo(String chrom) throws IOException {
		byte[] key = chrom.getBytes();
		byte[] value = new byte[bPlusTree.valSize];
		bPlusTree.find(key, value);
		ChromInfo res = parseChromInfo(chrom, value);
		return res;
	}

	public ChromInfo parseChromInfo(String chrom, byte[] value) {
		ByteBuffer b = ByteBuffer.wrap(value);
		b.order(dataSource.order());
		int chromId = Utils.readUInt(b);
		int chromSize = Utils.readUInt(b);
		ChromInfo res =new ChromInfo();
		res.id = chromId;
		res.name = chrom;
		res.length = chromSize;
		return res;
	}
	
	public void traverseChroms(Consumer<ChromInfo> action) throws IOException {
		bPlusTree.traverse(dataSource, (k,v)->{
			int nameLength = 0;
			while(nameLength < k.length && k[nameLength] != 0)
				nameLength++;
			String chrom = new String(k, 0, nameLength);
			ChromInfo chrInfo = parseChromInfo(chrom, v);
			action.accept(chrInfo);
		});
	}
	


	public void close() throws IOException
	{
		dataSource.close();
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
}
