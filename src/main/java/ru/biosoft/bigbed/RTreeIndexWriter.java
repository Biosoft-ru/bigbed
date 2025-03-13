package ru.biosoft.bigbed;

import java.io.IOException;
import java.io.RandomAccessFile;

import ru.biosoft.bigbed.RTreeIndexBuilder.RTree;

public class RTreeIndexWriter {

	private static final int nodeHeaderSize = 4;
	private static final int indexSlotSize = 24;
	private static final int leafSlotSize = 32;


	public static void writeRTree(RTree rTree, RandomAccessFile out) throws IOException {
		long indexOffset = out.getFilePointer();
		out.writeInt(RTreeIndex.MAGIC);
		out.writeInt(rTree.blockSize);
		out.writeLong(rTree.leafCount);
		if(rTree.root != null)
		{
			out.writeInt(rTree.root.startChromIx);
			out.writeInt(rTree.root.startBase);
			out.writeInt(rTree.root.endChromIx);
			out.writeInt(rTree.root.endBase);
			
		}else
		{
			out.writeInt(0);
			out.writeInt(0);
			out.writeInt(0);
			out.writeInt(0);
		}
		out.writeLong(indexOffset);//this is called fileSize in RTreeIndex?
		out.writeInt(1);//items per slot
		out.writeInt(0);//reserved
		
		int[] levelSizes = new int[rTree.levelCount];
		for (int i=0; i<rTree.levelCount; ++i)
		    levelSizes[i] = 0;
		calcLevelSizes(rTree.root, levelSizes, 0, rTree.levelCount-1);
		
		long[] levelOffsets = new long[rTree.levelCount];
		long offset = out.getFilePointer();
		int iNodeSize = indexNodeSize(rTree.blockSize);
		int lNodeSize = leafNodeSize(rTree.blockSize);
		for (int i=0; i<rTree.levelCount; ++i)
		{
		    levelOffsets[i] = offset;
		    offset += levelSizes[i] * iNodeSize;
		}

		
		/* Write out index levels. */
		int finalLevel = rTree.levelCount-3;
		for (int i=0; i<=finalLevel; ++i)
		{
		    long childNodeSize = (i==finalLevel ? lNodeSize : iNodeSize);
		    writeIndexLevel(rTree.blockSize, childNodeSize, rTree.root, levelOffsets[i+1], i, out);
		}

		int leafLevel = rTree.levelCount - 2;
		writeLeaves(rTree.blockSize, leafNodeSize(rTree.blockSize), rTree.root, leafLevel, out);


	}

	private static void writeLeaves(int itemsPerSlot, int lNodeSize, RTreeNode root, int leafLevel,
			RandomAccessFile out) throws IOException {
		rWriteLeaves(itemsPerSlot, lNodeSize, root, 0, leafLevel, out);
	}
	
	private static void rWriteLeaves(int itemsPerSlot, int lNodeSize, RTreeNode root, int curLevel, int leafLevel,
			RandomAccessFile out) throws IOException {
		if (curLevel == leafLevel) {
			/* We've reached the right level, write out a node header. */
			byte reserved = 0;
			byte isLeaf = 1;
			int countOne = RTreeNode.count(root.children);
			out.writeByte(isLeaf);
			out.writeByte(reserved);
			out.writeShort(countOne);

			/* Write out elements of this node. */
			for (RTreeNode el = root.children; el != null; el = el.next) {
				out.writeInt(el.startChromIx);
				out.writeInt(el.startBase);
				out.writeInt(el.endChromIx);
				out.writeInt(el.endBase);
				out.writeLong(el.startFileOffset);
				long size = el.endFileOffset - el.startFileOffset;
				out.writeLong(size);
			}

			/* Write out zeroes for empty slots in node. */
			Utils.writeNBytes(out, (byte) 0, indexSlotSize * (itemsPerSlot - countOne));
		} else {
			/* Otherwise recurse on children. */
			for (RTreeNode el = root.children; el != null; el = el.next)
				rWriteLeaves(itemsPerSlot, lNodeSize, el, curLevel + 1, leafLevel, out);
		}

	}

	private static void writeIndexLevel(int blockSize, long childNodeSize, RTreeNode root, long offsetOfFirstChild,
			int level, RandomAccessFile out) throws IOException {
		rWriteIndexLevel(blockSize, childNodeSize, root, 0, level, offsetOfFirstChild, out);
	}

	/*
	 * Recursively write an index level, skipping levels below destLevel, writing
	 * out destLevel.
	 */
	private static long rWriteIndexLevel(int blockSize, long childNodeSize, RTreeNode root, int curLevel, int destLevel,
			long offsetOfFirstChild, RandomAccessFile out) throws IOException {

		long offset = offsetOfFirstChild;
		if (curLevel == destLevel) {
			/* We've reached the right level, write out a node header */
			byte reserved = 0;
			byte isLeaf = 0;
			int countOne = RTreeNode.count(root.children);
			out.writeByte(isLeaf);
			out.writeByte(reserved);
			out.writeShort(countOne);

			/* Write out elements of this node. */
			for (RTreeNode el = root.children; el != null; el = el.next) {
				out.writeInt(el.startChromIx);
				out.writeInt(el.startBase);
				out.writeInt(el.endChromIx);
				out.writeInt(el.endBase);
				out.writeLong(offset);
				offset += childNodeSize;
			}

			/* Write out zeroes for empty slots in node. */
			Utils.writeNBytes(out, (byte) 0, indexSlotSize * (blockSize - countOne));
		} else {
			/* Otherwise recurse on children. */
			for (RTreeNode el = root.children; el != null; el = el.next)
				offset = rWriteIndexLevel(blockSize, childNodeSize, el, curLevel + 1, destLevel, offset, out);
		}
		return offset;
	}

	private static int indexNodeSize(int blockSize)
	/* Return size of an index node. */
	{
		return nodeHeaderSize + indexSlotSize * blockSize;
	}

	private static int leafNodeSize(int blockSize)
	/* Return size of a leaf node. */
	{
		return nodeHeaderSize + leafSlotSize * blockSize;
	}


	private static void calcLevelSizes(RTreeNode root, int[] levelSizes, int level, int maxLevel) {
		for(RTreeNode node = root; node != null; node = node.next)
		{
			levelSizes[level]++;
			if(level < maxLevel)
				calcLevelSizes(node.children, levelSizes, level+1, maxLevel);
		}
	}
}
