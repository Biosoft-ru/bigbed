package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class BigFile<T> implements AutoCloseable {
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
	
	
	BigFile(String source)
	{
		this.source = source;
		dataSource = new RAFDataSource();
	}
	
	void read() throws IOException, ParseException
	{
		dataSource.open(source);
		
		header = new Header();
		header.read(dataSource, getFileMagic());

		dataSource.seek(64);
		zoomLevels = new ArrayList<>(header.zoomLevels);
		for (int i = 0; i < header.zoomLevels; i++) {
			ZoomLevel zoomLevel = new ZoomLevel();
			zoomLevel.read(dataSource);
			zoomLevels.add(zoomLevel);
		}
		
		readTotalSummary();
		
		dataSource.seek(header.unzoomedDataOffset);
		
		bPlusTree = new BPlusTree(dataSource, header.chromTreeOffset);
	}
	
	protected abstract int getFileMagic();
	
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


	void initRTree() throws IOException
	{
		if(rTree == null)
		{
			rTree = new RTreeIndex();
			rTree.read(dataSource, header.unzoomedIndexOffset);
		}
	}
	
	protected abstract boolean parseBlock(int chromId, int start, int end, int maxItems, ByteBuffer block, List<T> resultList);
	
	
	public List<T> queryIntervals(String chrom, int start, int end, int maxItems) throws IOException {
		ChromInfo chrInfo = getChromInfo(chrom);
		return queryIntervals(chrInfo.id, start, end, maxItems);
	}
	

	public List<T> queryIntervals(int chromId, int start, int end, int maxItems) throws IOException {

		List<T> result = new ArrayList<>();
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
	    		
	    		parseBlock(chromId, start, end, maxItems, bb, result);
	    		
	    	}
	    	iblock += nBlocks;
	    
	    }
		
		return result;
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
}
