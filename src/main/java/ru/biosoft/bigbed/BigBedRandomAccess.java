package ru.biosoft.bigbed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import ru.biosoft.bigbed.RTreeIndex.LeafNode;

//Query sites from BigBed file by their index (1,2,3,...)
//Useful for pagination
//BigBeds where not designed for this type of query and original implementation doesn't have this functionality
public class BigBedRandomAccess {
	private BigBedFile bbFile;
	private TreeMap<Long, OffsetSize> idxToOffset;
	private long siteCount;

	public BigBedRandomAccess(BigBedFile bbFile) throws IOException {
		this.bbFile = bbFile;
		initIdxToOffset();
	}

	public List<BedEntry> fetch(long from, int size) throws IOException {
		if (from < 0 || from >= siteCount || size < 0 || from + size > siteCount)
			throw new IllegalArgumentException();
		if (size == 0)
			return Collections.emptyList();

		List<BedEntry> result = new ArrayList<>();

		Long keyFrom = idxToOffset.floorKey(from);
		Long keyTo = idxToOffset.floorKey(from + size - 1);
		SortedMap<Long, OffsetSize> blocks = idxToOffset.subMap(keyFrom, true, keyTo, true);

		int uncompressedBufSize = bbFile.header.uncompressBufSize;
		byte[] uncompressed = null;
		if (uncompressedBufSize > 0)
			uncompressed = new byte[uncompressedBufSize];
		DataSource input = bbFile.dataSource;

		long skip = -1;
		for (Map.Entry<Long, OffsetSize> entry : blocks.entrySet()) {
			if (skip == -1)
				skip = from - entry.getKey();
			OffsetSize block = entry.getValue();

			input.seek(block.offset);
			int blockSize = (int) block.size;
			byte[] blockData = new byte[blockSize];

			input.readFully(blockData, 0, blockSize);

			if (uncompressedBufSize > 0) {
				blockSize = Utils.uncompress(blockData, 0, (int) block.size, uncompressed);
				blockData = uncompressed;
			}

			ByteBuffer buf = ByteBuffer.wrap(blockData, 0, blockSize);
			buf.order(input.order());
			while (buf.hasRemaining()) {
				int chrId = buf.getInt();
				int start = buf.getInt();
				int end = buf.getInt();

				buf.mark();
				int restLen = 0;
				while (buf.hasRemaining() && buf.get() != 0)
					restLen++;

				if (skip == 0) {

					BedEntry bed = new BedEntry(chrId, start, end);
					buf.reset();
					bed.data = new byte[restLen];
					buf.get(bed.data);
					buf.get();// skip zero terminator
					result.add(bed);
					if (result.size() >= size)
						return result;
				} else {
					skip--;
				}
			}
		}

		return result;
	}

	private void initIdxToOffset() throws IOException {
		idxToOffset = new TreeMap<>();

		bbFile.initRTree();
		List<LeafNode> leafs = new ArrayList<>();
		bbFile.rTree.traverseAllLeafs(bbFile.dataSource, leafs::add);

		int itemsPerSlot = -1;// do not rely on rTreeHeader.getItemsPerSlot()

		RTreeIndex.LeafNode prev = null;
		for (LeafNode node : leafs) {
			if (prev != null) {
				if (prev.startChromIdx != node.startChromIdx) {
					// node and prev on distinct chromosomes
					// last block on chromosome maybe not full (<=itemsPerSlot)
					siteCount += countInCompressedBlock(bbFile.dataSource, prev.offset, prev.size,
							bbFile.header.uncompressBufSize);
				} else {
					// leaf and prev on same chromosomes
					// assume that prev block is full in this case
					if (itemsPerSlot == -1) {
						itemsPerSlot = countInCompressedBlock(bbFile.dataSource, prev.offset, prev.size,
								bbFile.header.uncompressBufSize);
					}
					siteCount += itemsPerSlot;
				}
			}
			idxToOffset.put(siteCount, new OffsetSize(node.offset, node.size));
			prev = node;
		}

		if (prev != null)// last block
		{
			siteCount += itemsPerSlot = countInCompressedBlock(bbFile.dataSource, prev.offset, prev.size,
					bbFile.header.uncompressBufSize);
		}
	}

	private int countInCompressedBlock(DataSource input, long offset, long size, int uncompressedBufSize)
			throws IOException {
		input.seek(offset);
		byte[] buf = new byte[(int) size];
		input.readFully(buf, 0, buf.length);
		if (bbFile.header.uncompressBufSize > 0)
			return countInCompressedBlock(buf, uncompressedBufSize, input.order());
		else
			return countInUncompressedBlock(buf, 0, buf.length, input.order());
	}

	private int countInCompressedBlock(byte[] compressed, int uncompressedBufSize, ByteOrder order) throws IOException {
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		byte[] uncompressed = new byte[uncompressedBufSize];
		int uncompressedLen;
		try {
			uncompressedLen = inflater.inflate(uncompressed);
		} catch (DataFormatException e) {
			throw new ParseException(e);
		}
		if (inflater.getRemaining() > 0)
			throw new ParseException("extra bytes after compressed block");
		return countInUncompressedBlock(uncompressed, 0, uncompressedLen, order);
	}

	private int countInUncompressedBlock(byte[] uncompressed, int uncompressedOffset, int uncompressedLen,
			ByteOrder order) {
		int res = 0;
		ByteBuffer buf2 = ByteBuffer.wrap(uncompressed, uncompressedOffset, uncompressedLen);
		buf2.order(order);
		while (buf2.hasRemaining()) {
			int chrId = buf2.getInt();
			int start = buf2.getInt();
			int end = buf2.getInt();
			while (buf2.get() > 0)
				;
			res++;
		}
		return res;

	}
}
