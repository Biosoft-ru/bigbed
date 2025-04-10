package ru.biosoft.bigbed;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Consumer;

public class RTreeIndex {
	public static int MAGIC = 0x2468ACE0;
	public static int HEADER_SIZE = 48;

	// Fields in file
	int magic;
	int blockSize;
	long itemCount;
	int startChromIdx;
	int startBase;
	int endChromIdx;
	int endBase;
	long fileSize;
	int itemsPerSlot;
	/////

	long rootOffset;

	public void read(DataSource d, long position) throws IOException {
		d.seek(position);
		magic = d.readInt();
		if (magic != MAGIC)
			throw new ParseException("Not Rtree format, magic bytes 0x" + Integer.toHexString(magic));
		blockSize = d.readUInt();
		itemCount = d.readULong();
		startChromIdx = d.readUInt();
		startBase = d.readUInt();
		endChromIdx = d.readUInt();
		endBase = d.readUInt();
		fileSize = d.readULong();
		itemsPerSlot = d.readUInt();

		// Skip reserved
		d.readInt();

		rootOffset = position + HEADER_SIZE;
	}
	
	public void findOverlappingBlocks(int chrId, int start, int end, DataSource d, Consumer<OffsetSize> result) throws IOException {
		findOverlappingBlocks(rootOffset, chrId, start, end, d, result);
	}

	public void findOverlappingBlocks(long fileOffset, int chrId, int start, int end, DataSource d, Consumer<OffsetSize> result) throws IOException {
		d.seek(fileOffset);
		byte isLeaf = d.readByte();
		byte reserved = d.readByte();
		int childCount = d.readUShortAsInt();
		
		if (isLeaf == 1) {
			for (int i = 0; i < childCount; ++i) {
				int startChromIx = d.readUInt();
				int startBase = d.readUInt();
				int endChromIx = d.readUInt();
				int endBase = d.readUInt();
				long offset = d.readULong();
				long size = d.readULong();
				if (cirTreeOverlaps(chrId, start, end, startChromIx, startBase, endChromIx, endBase)) {
					OffsetSize os = new OffsetSize(offset, size);
					result.accept(os);
				}
			}
		}
		else
		{
			/* Read node into arrays. */
			int[] startChromIx = new int[childCount];
			int[] startBase = new int[childCount];
			int[] endChromIx = new int[childCount];
			int[] endBase = new int[childCount];
			long[] offset = new long[childCount];
			for (int i = 0; i < childCount; ++i) {
				startChromIx[i] = d.readUInt();
				startBase[i] = d.readUInt();
				endChromIx[i] = d.readUInt();
				endBase[i] = d.readUInt();
				offset[i] = d.readULong();
			}

			/* Recurse into child nodes that we overlap. */
			for (int i = 0; i < childCount; ++i) {
				if (cirTreeOverlaps(chrId, start, end, startChromIx[i], startBase[i], endChromIx[i], endBase[i]))
					findOverlappingBlocks(offset[i], chrId, start, end, d, result);
			}

		}
	}
	
	static class LeafNode
	{
		int startChromIdx,startBase,endChromIdx,endBase;
		long offset, size;
	}
	
	public void traverseAllLeafs(DataSource d, Consumer<LeafNode> result) throws IOException
	{
		traverseAllLeafs(rootOffset, d, result);
	}
	private void traverseAllLeafs(long fileOffset, DataSource d, Consumer<LeafNode> result) throws IOException
	{
		d.seek(fileOffset);
		byte isLeaf = d.readByte();
		byte reserved = d.readByte();
		int childCount = d.readUShortAsInt();
		
		if (isLeaf == 1) {
			for (int i = 0; i < childCount; ++i) {
				LeafNode node = new LeafNode();
				node.startChromIdx = d.readUInt();
				node.startBase = d.readUInt();
				node.endChromIdx = d.readUInt();
				node.endBase = d.readUInt();
				node.offset = d.readULong();
				node.size = d.readULong();
				result.accept(node);
			}
		}
		else
		{
			long[] offset = new long[childCount];
			for (int i = 0; i < childCount; ++i) {
				int startChromIdx = d.readUInt();
				int startBase = d.readUInt();
				int endChromIdx = d.readUInt();
				int endBase = d.readUInt();
				long childOffset = d.readULong();
				offset[i] = childOffset;
			}
			for (int i = 0; i < childCount; ++i) {
				traverseAllLeafs(offset[i], d, result);
			}
		}
	}
	
	
	static boolean cirTreeOverlaps(int qChrom, int qStart, int qEnd, int rStartChrom, int rStartBase, int rEndChrom,
			int rEndBase) {
		return cmpTwoBits32(qChrom, qStart, rEndChrom, rEndBase) > 0
				&& cmpTwoBits32(qChrom, qEnd, rStartChrom, rStartBase) < 0;
	}

	static int cmpTwoBits32(int aHi, int aLo, int bHi, int bLo)
	{
		if (aHi < bHi)
			return 1;
		else if (aHi > bHi)
			return -1;
		else {
			if (aLo < bLo)
				return 1;
			else if (aLo > bLo)
				return -1;
			else
				return 0;
		}
	}

}
