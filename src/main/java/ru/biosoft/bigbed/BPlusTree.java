package ru.biosoft.bigbed;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
//Basically it is Map from byte[keySize] to byte[valSize] stored in external memory
public class BPlusTree {
	public static final int MAGIC_BPLUS_TREE = 0x78CA8C91;
	public static final int HEADER_SIZE = 32;
	private static final int BLOCK_HEADER_SIZE = 4;
	
	//header fields of B+ tree
	int magic;
	int blockSize;
	int keySize;
	int valSize;
	long itemCount;
	//////
	
	
	long rootOffset;
	
	DataSource dataSource;
	
	//d must be open
	public BPlusTree(DataSource d, long position) throws IOException
	{
		this.dataSource = d;
		readHeader(position);
	}
	
	
	void readHeader(long position) throws IOException
	{
		dataSource.seek(position);
		magic = dataSource.readInt();
		if(magic != MAGIC_BPLUS_TREE)
			throw new ParseException("Not a B+tree format, magic bytes 0x" + Integer.toHexString(magic));
		blockSize = dataSource.readUInt();;
		keySize = dataSource.readUInt();
		valSize = dataSource.readUInt();
		itemCount = dataSource.readULong();
		
		//Skip reserved fields
		dataSource.readInt();
		dataSource.readInt();
		
		rootOffset = position + HEADER_SIZE;
	}
	
	public boolean find(byte[] key, byte[] result) throws IOException
	{
		if(key.length > keySize)
			return false;
		byte[] keyBuf = new byte[keySize];
		System.arraycopy(key, 0, keyBuf, 0, key.length);
		Arrays.fill(keyBuf, key.length, keySize, (byte)0);
		if(result.length != valSize)
			throw new ParseException("result.length != valSize");
		return rFind(rootOffset, keyBuf, result);
	}
	
	private boolean rFind(long blockOffset, byte[] key, byte[] result) throws IOException
	{
		dataSource.seek(blockOffset);
		byte isLeaf = dataSource.readByte();
		byte reserved = dataSource.readByte();
		int childCount = dataSource.readUShortAsInt();
		byte[] keyBuf = new byte[keySize];
		if(isLeaf == 1)
		{
			for (int i = 0; i < childCount; i++) {
				dataSource.readFully(keyBuf, 0, keySize);
				dataSource.readFully(result, 0, valSize);
				if (Arrays.equals(key, keyBuf))
					return true;
			}
			return false;
		}
		else
		{
			dataSource.skip(keySize);//discard key
			long fileOffset = dataSource.readULong();
			for(int i = 1; i < childCount; i++)
			{
				dataSource.readFully(keyBuf, 0, keySize);
				if(cmp(key, keyBuf, keySize) < 0)
					break;
				fileOffset = dataSource.readULong();
			}
			return rFind(fileOffset, key, result);
		}
	}

	public void findMulti(byte[] key, Consumer<byte[]> resConsumer) throws IOException
	{
		if(key.length > keySize)
			return;
		byte[] keyBuf = new byte[keySize];
		System.arraycopy(key, 0, keyBuf, 0, key.length);
		rFindMulti(rootOffset, keyBuf, resConsumer);
	}
	
	private void rFindMulti(long blockOffset, byte[] key, Consumer<byte[]> resConsumer) throws IOException
	{
		dataSource.seek(blockOffset);
		byte isLeaf = dataSource.readByte();
		byte reserved = dataSource.readByte();
		int childCount = dataSource.readUShortAsInt();
		byte[] keyBuf = new byte[keySize];
		
		if(isLeaf == 1)
		{
			for (int i = 0; i < childCount; i++) {
				dataSource.readFully(keyBuf, 0, keySize);
				
				if (Arrays.equals(key, keyBuf))
				{
					byte[] valBuf = new byte[valSize];
					dataSource.readFully(valBuf, 0, valSize);
					resConsumer.accept(valBuf);
				}else
				{
					dataSource.skip(valSize);
				}
			}
		}
		else
		{
			dataSource.readFully(keyBuf, 0, keySize);
			long lastFileOffset = dataSource.readULong();
			long fileOffset = lastFileOffset;
			int lastCmp = cmp(key, keyBuf, keySize);
			for(int i = 1; i < childCount; i++)
			{
				dataSource.readFully(keyBuf, 0, keySize);
				fileOffset = dataSource.readULong();
				int cmp = cmp(key, keyBuf, keySize);
				if(lastCmp >= 0 && cmp <= 0)
				{
					long pos = dataSource.tell();
					rFindMulti(lastFileOffset, key, resConsumer);
					dataSource.seek(pos);
				}
				if(cmp < 0)
					return;
				lastCmp = cmp;
				lastFileOffset = fileOffset;
			}
			rFindMulti(fileOffset, key, resConsumer);
		}
	}

	
	public void traverse(DataSource d, BiConsumer<byte[], byte[]> kvAction) throws IOException
	{
		traverse(rootOffset, d, kvAction, new byte[keySize], new byte[valSize]);
	}
	
	private void traverse(long rootOffset, DataSource d, BiConsumer<byte[], byte[]> kvAction, byte[] keySpace, byte[] valSpace) throws IOException
	{
		d.seek(rootOffset);
		byte isLeaf = d.readByte();
		byte reserved = d.readByte();
		int childCount = d.readUShortAsInt();
		if(isLeaf == 1)
		{
			for (int i = 0; i < childCount; i++) {
				d.readFully(keySpace, 0, keySize);
				d.readFully(valSpace, 0, valSize);
				kvAction.accept(keySpace, valSpace);
			}
		}
		else
		{
			long[] offsets = new long[childCount];
			for(int i = 0; i < childCount; i++)
			{
				d.readFully(keySpace, 0, keySize);
				long nodeOffset = d.readULong();
				offsets[i] = nodeOffset;
			}
			for(int i = 0; i < childCount; i++)
				traverse(offsets[i], d, kvAction, keySpace, valSpace);
		}
	}

	public static int cmp(byte[] a, byte[] b, int n) {
		for(int i = 0; i < n; i++)
		{
			int ai = a[i] & 0xff;
			int bi = b[i] & 0xff;
			if(ai != bi)
				return (ai < bi) ? -1 : 1;
		}
		return 0;
	}
	
	public static int cmp(byte[] a, byte[] b) {
		int n = Math.min(a.length, b.length);
		for(int i = 0; i < n; i++)
		{
			int ai = a[i] & 0xff;
			int bi = b[i] & 0xff;
			if(ai != bi)
				return (ai < bi) ? -1 : 1;
		}
		return Integer.compare(a.length, b.length);
	}
	
	//items in data should be sorted by key
	//TODO: support data.length > 2^31
	//This is merely translation to java of kent/src/lib/bbiWite.c:bptFileBulkIndexToOpenFile
	public static void write(RandomAccessFile out, byte[] data, int keySize, int valSize, int blockSize) throws IOException
	{
		int itemSize = keySize + valSize;
		int itemCount = data.length / itemSize;
		int reserved = 0;
		out.writeInt(MAGIC_BPLUS_TREE);
		out.writeInt(blockSize);
		out.writeInt(keySize);
		out.writeInt(valSize);
		out.writeLong(itemCount);
		out.writeInt(reserved);
		out.writeInt(reserved);
		long indexOffset = out.getFilePointer();
		
		int levels = countLevels(blockSize, itemCount);
		int i;
		for (i = levels - 1; i > 0; --i) {
			long endLevelOffset = writeIndexLevel(blockSize, data, itemSize, itemCount, indexOffset, i, keySize,
					valSize, out);
			indexOffset = out.getFilePointer();
			if (endLevelOffset != indexOffset)
				throw new AssertionError();
		}

		/* Write leaf nodes */
		writeLeafLevel(blockSize, data, itemSize, itemCount,
		        keySize, valSize, out);


	}

	
	private static void writeLeafLevel(int blockSize, byte[] data, int itemSize, int itemCount, int keySize,
			int valSize, RandomAccessFile out) throws IOException {
		int countOne;// 16bit
		int countLeft = itemCount;
		for (int i = 0; i < itemCount; i += countOne) {
			/* Write block header */
			if (countLeft > blockSize)
				countOne = blockSize;
			else
				countOne = countLeft;
			out.writeByte(1);// isLeaf
			out.writeByte(0);// reserved
			out.writeShort(countOne);

			/* Write out position in genome and in file for each item. */
			for (int j = 0; j < countOne; ++j) {
				if (i + j >= itemCount)
					throw new AssertionError();
				out.write(data, (i + j) * itemSize, itemSize);
			}

			/* Pad out any unused bits of last block with zeroes. */
			Utils.writeNBytes(out, (byte)0, (blockSize-countOne)*itemSize);

			countLeft -= countOne;
		}

	}


	private static long writeIndexLevel(int blockSize, byte[] data, int itemSize, int itemCount, long indexOffset,
			int level, int keySize, int valSize, RandomAccessFile out) throws IOException {
		
		/* Calculate number of nodes to write at this level. */
		int slotSizePer = xToY(blockSize, level);   // Number of items per slot in node
		int nodeSizePer = slotSizePer * blockSize;  // Number of items per node
		int nodeCount = (itemCount + nodeSizePer - 1)/nodeSizePer;


		/* Calculate sizes and offsets. */ 
		int bytesInIndexBlock = (BLOCK_HEADER_SIZE + blockSize * (keySize+8));
		int bytesInLeafBlock = (BLOCK_HEADER_SIZE + blockSize * (keySize+valSize));
		int bytesInNextLevelBlock = (level == 1 ? bytesInLeafBlock : bytesInIndexBlock);
		int levelSize = nodeCount * bytesInIndexBlock;
		long endLevel = indexOffset + levelSize;
		long nextChild = endLevel;
		
		
		byte isLeaf = 0;
		byte reserved = 0;

		for (int i = 0; i < itemCount; i += nodeSizePer) {
			/* Calculate size of this block */
			int countOne = (itemCount - i + slotSizePer - 1) / slotSizePer;
			if (countOne > blockSize)
				countOne = blockSize;

			/* Write block header. */
			out.writeByte(isLeaf);
			out.writeByte(reserved);
			out.writeShort((int) countOne);

			/* Write out the slots that are used one by one, and do sanity check. */
			int slotsUsed = 0;
			long endIx = i + nodeSizePer;
			if (endIx > itemCount)
				endIx = itemCount;
			for (long j = i; j < endIx; j += slotSizePer) {
				out.write(data, (int) (j * itemSize), keySize);
				out.writeLong(nextChild);
				nextChild += bytesInNextLevelBlock;
				++slotsUsed;
			}
			if (slotsUsed != countOne)
				throw new AssertionError();

			/* Write out empty slots as all zero. */
			int slotSize = keySize + 8;
			Utils.writeNBytes(out, (byte) 0, (blockSize - countOne) * slotSize);
		}
		return endLevel;
	}


	private static int countLevels(int maxBlockSize, int itemCount) {
		int levels = 1;
		while (itemCount > maxBlockSize) {
			itemCount = (itemCount + maxBlockSize - 1) / maxBlockSize;
			levels += 1;
		}
		return levels;

	}
	
	/* Return x to the Y power, with y usually small. */
	private static int xToY(int x, int y)
	{
		int i, val = 1;
		for (i = 0; i < y; ++i)
			val *= x;
		return val;
	}

}
