package ru.biosoft.bigbed;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.biosoft.bigbed.RTreeIndexBuilder.RTree;

public class BigBedWriter {

	//chrs must fulfill chrs[i].id = i
	public static void write(List<BedEntry> sites, List<ChromInfo> chrs, File outFile, BigBedWriterOptions options) throws IOException
	{
		Comparator<BedEntry> cmp = Comparator.comparingInt(e->e.chrId);
		cmp = cmp.thenComparingInt(e->e.start);
		sites.sort(cmp);
		
		
		RandomAccessFile out = new RandomAccessFile(outFile, "rw");
		
		Utils.writeNBytes(out, (byte)0, 64);//dummy header
		//Utils.writeNBytes(out, (byte)0, BigBedFile.MAX_ZOOM_LEVELS*24);//dummy zoom levels	
		

		if(options.bedN < 3 || options.bedN > 15)
			options.bedN = 3;
		
		if(options.autoSql == null)
		{
			int nColumns = 3;
			if(sites.size() > 0) {
				BedEntry first = sites.get(0);
				if(first.data != null)
					nColumns += new String(first.data).split(options.fieldSep).length;
			}
			options.autoSql = AutoSql.defaultBed(options.bedN, nColumns);
		}
		long asOffset = out.getFilePointer();
		out.write(options.autoSql.toString().getBytes(StandardCharsets.UTF_8));
		out.writeByte(0);
		
		long totalSummaryOffset = out.getFilePointer();
		SummaryElement totalSummary = new SummaryElement();
		totalSummary.compute(sites.iterator());
		totalSummary.write(out);
		
		boolean haveExtraIndex = options.extraIndexColumns != null && options.extraIndexColumns.length > 0;
		long extensionHeaderOffset = out.getFilePointer();
		int extHeaderSize = 64;
		long extraIndexListOffset;
		if(haveExtraIndex)
		{
			extraIndexListOffset = extensionHeaderOffset + extHeaderSize;
			//out.seek(extensionHeaderOffset);
			out.writeShort(extHeaderSize);
			out.writeShort(options.extraIndexColumns.length);
			out.writeLong(extraIndexListOffset);
			Utils.writeNBytes(out, (byte)0, extHeaderSize-12);
			
			//reserve space for extra index headers
			Utils.writeNBytes(out, (byte)0, options.extraIndexColumns.length*20);
		}else
		{
			extraIndexListOffset = 0;
			out.writeShort(extHeaderSize);
			Utils.writeNBytes(out, (byte)0, extHeaderSize-2);
		}
		
		long chromTreeOffset = out.getFilePointer();
		writeChromTree(chrs, out, options.blockSize);
		
		long dataOffset = out.getFilePointer();
		out.writeLong(sites.size());
		List<Block> blocks = writeBlocks(sites, out, options);
		
		long indexOffset = out.getFilePointer();
		RTree rTree = RTreeIndexBuilder.buildRTree(blocks, options.blockSize);
		RTreeIndexWriter.writeRTree(rTree, out);
		
		//TOOD: write Zoom
		
		//write extra index
		if(haveExtraIndex)
		{
			
			long[] fileOffsets = makeExtraIndex(sites, blocks, out, options);
			out.seek(extraIndexListOffset);
			for(int i = 0; i < options.extraIndexColumns.length; i++)
			{
				int col = options.extraIndexColumns[i];
				out.writeShort(0);//type
				out.writeShort(1);//field count
				out.writeLong(fileOffsets[i]);
				out.writeInt(0);//reserved
				out.writeShort(col);
				out.writeShort(0);//reserved
			}
		}
		
		
		//Write BigBed header
		out.seek(0);
		out.writeInt(Header.MAGIC_BIGBED);
		out.writeShort(BigBedFile.VERSION);
		out.writeShort(0);//Number of zoom levels
		out.writeLong(chromTreeOffset);
		out.writeLong(dataOffset);
		out.writeLong(indexOffset);
		out.writeShort(options.autoSql.columns.size());
		out.writeShort(options.bedN);
		out.writeLong(asOffset);
		out.writeLong(totalSummaryOffset);
		out.writeInt(options.compress ? getMaxUncompressedBufLength(blocks) : 0);
		out.writeLong(extensionHeaderOffset);
		if(out.getFilePointer() != 64)
			throw new AssertionError();
		
		/* Write total summary. */
		out.seek(totalSummaryOffset);
		totalSummary.write(out);
		
	//	
		out.close();
	}
	

	
	private static int getMaxUncompressedBufLength(List<Block> blocks) {
		return blocks.stream().mapToInt(b->b.uncompressedLength).max().orElseGet(()->0);
	}


	static class ExtraIndexEntry
	{
		byte[] key;
		Block block;
	}

	private static long[] makeExtraIndex(List<BedEntry> sites, List<Block> blocks, RandomAccessFile out, BigBedWriterOptions options) throws IOException {
		int dataColumns = options.autoSql.columns.size()-3;
		int indexCount = options.extraIndexColumns.length;

		List<ExtraIndexEntry>[] ess = new List[indexCount];
		Set<String>[] keys = new Set[indexCount];
		for(int i = 0; i < indexCount; i++)
		{
			keys[i] = new HashSet<>();
			ess[i] = new ArrayList<>();
		}
		
		for(Block block : blocks)
		{
			for(Set<String> s : keys)
				s.clear();
			
			//Iterate over sites of current block and fill keys
			for(int i = block.siteOffset; i < block.siteOffset + block.siteCount; i++)
			{
				BedEntry site = sites.get(i);
				String dataStr = new String(site.data, StandardCharsets.UTF_8);
				String[] parts = dataStr.split(options.fieldSep);
				if(parts.length != dataColumns)
					throw new IllegalArgumentException("Expecting " + dataColumns + " columns in site data, but got " + parts.length + " in: " + dataStr);
				for(int j = 0; j < options.extraIndexColumns.length; j++)
				{
					int col = options.extraIndexColumns[j];
					String key = parts[j];
					keys[j].add(key);
				}
			}
			
			//make entries for current block
			for(int i = 0; i < indexCount; i++)
			{
				for(String key : keys[i])
				{
					ExtraIndexEntry e = new ExtraIndexEntry();
					e.key = key.getBytes(StandardCharsets.UTF_8);
					e.block = block;
					ess[i].add(e);	
				}
			}
		}
		
		
		long[] treeOffset = new long[indexCount];
		for(int j = 0; j < indexCount; j++)
		{
			List<ExtraIndexEntry> es = ess[j];
			Comparator<ExtraIndexEntry> cmp = new Comparator<ExtraIndexEntry>() {
				public int compare(ExtraIndexEntry e1, ExtraIndexEntry e2) {
					byte[] k1 = e1.key;
					byte[] k2 = e2.key;
					return BPlusTree.cmp(k1,k2);
				}
			};
			es.sort(cmp);
			
			int keySize = 0;
			for(int i = 0; i < es.size(); i++) {
				byte[] key = es.get(i).key;
				if(key.length > keySize)
					keySize = key.length;
			}
			int valSize = 8+8;//offset,size
			
			byte[] data = new byte[es.size()*(keySize+valSize)];
			ByteBuffer buf = ByteBuffer.wrap(data);
			buf.order(ByteOrder.BIG_ENDIAN);
			for(int i = 0; i < es.size(); i++)
			{
				ExtraIndexEntry e = es.get(i);
				buf.put(e.key);
				buf.position(buf.position() + (keySize - e.key.length));
				buf.putLong(e.block.offsetInFile);
				buf.putLong(e.block.compressedLength);
			}
			
			treeOffset[j] = out.getFilePointer();
			BPlusTree.write(out, data, keySize, valSize, options.blockSize);	
		}
		
		return treeOffset;
	}
	
	private static List<Block> writeBlocks(List<BedEntry> sites, RandomAccessFile out, BigBedWriterOptions options) throws IOException {
		if(sites.size() == 0)
			return Collections.emptyList();
		
		List<Block> blocks = new ArrayList<>();
		
		ByteArrayOutputStream blockOut = new ByteArrayOutputStream();
		DataOutputStream blockDataOut = new DataOutputStream(blockOut);
		Block curBlock = null;
		
		
		for(int i = 0; i < sites.size(); i++)
		{
			BedEntry e = sites.get(i);
			if(curBlock == null || curBlock.chrId != e.chrId || curBlock.siteCount >= options.itemsPerSlot)
			{
				//finish curBlock
				if(curBlock != null)
					finishBlock(out, options.compress, blockOut, curBlock);
				
				curBlock = new Block();
				curBlock.offsetInFile = out.getFilePointer();
				curBlock.chrId = e.chrId;
				curBlock.chrStart = e.start;
				curBlock.chrEnd = e.end;
				curBlock.siteOffset = i;
				//curBlock.compressedLength and curBlock.uncompressedLength will be filled in finishBlock
				blocks.add(curBlock);
			}else
			{
				if(curBlock.chrEnd < e.end)
					curBlock.chrEnd = e.end;
				//No need to update curBLock.chrStart since input is sorted
			}
			
			blockDataOut.writeInt(e.chrId);
			blockDataOut.writeInt(e.start);
			blockDataOut.writeInt(e.end);
			//TODO: check that e.data have no zero bytes
			if(e.data != null)
				blockDataOut.write(e.data);
			blockDataOut.write(0);
			curBlock.siteCount++;
		}
		
		//finish last block
		if(curBlock != null)
			finishBlock(out, options.compress, blockOut, curBlock);
		
		return blocks;
	}



	private static void finishBlock(RandomAccessFile out, boolean compress, ByteArrayOutputStream blockOut,
			Block curBlock) throws IOException {
		byte[] blockData = blockOut.toByteArray();
		blockOut.reset();
		curBlock.uncompressedLength = blockData.length;
		if(compress)
			blockData = Utils.compress(blockData);
		curBlock.compressedLength = blockData.length;					
		out.write(blockData);
	}


	private static void writeChromTree(List<ChromInfo> chrs, RandomAccessFile out, int blockSize) throws IOException {
		int maxChromNameSize = 0;
		for(ChromInfo chr : chrs)
			if(chr.name.length() > maxChromNameSize)
				maxChromNameSize = chr.name.length();

		int keySize = maxChromNameSize;
		int valSize = 8;
		int itemSize=  keySize + valSize;
		
		blockSize = Math.min(blockSize, chrs.size());
		
		byte[][] keys = new byte[chrs.size()][keySize];
		for(int i = 0; i < chrs.size(); i++)
		{
			ChromInfo chr = chrs.get(i);
			byte[] bytes = chr.name.getBytes(StandardCharsets.UTF_8);
			for(int j = 0; j < bytes.length; j++)
				keys[i][j] = bytes[j];
		}
		Integer[] idx = new	Integer[chrs.size()];
		for(int i = 0; i < idx.length; i++)
			idx[i] = i;
		Comparator<Integer> cmp = new Comparator<Integer>() {
			public int compare(Integer i1, Integer i2) {
				byte[] k1 = keys[i1];
				byte[] k2 = keys[i2];
				return BPlusTree.cmp(k1,k2, keySize);
			}
		};
		Arrays.sort(idx, cmp);
		
		byte[] data = new byte[chrs.size() * itemSize];
		ByteBuffer buf = ByteBuffer.wrap(data);
		//here we should write in the same byteOrder as @out
		//RandomAccessFile is BigEndian
		buf.order(ByteOrder.BIG_ENDIAN);//this is how it is done in ucsc genome browser
		for(int i = 0; i < chrs.size();i++)
		{
			int j = idx[i];
			ChromInfo chr = chrs.get(j);
			buf.put(keys[j]);
			buf.putInt(chr.id);
			buf.putInt(chr.length);
		}
		
		BPlusTree.write(out, data, keySize, valSize, blockSize);
	}
}
