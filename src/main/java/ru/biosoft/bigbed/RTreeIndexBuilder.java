package ru.biosoft.bigbed;

import java.util.List;

public class RTreeIndexBuilder {

	static class RTree
	{
		RTreeNode root;
		int levelCount;
		int blockSize;//number of childs per node
		int leafCount;
	}
	
	public static RTree buildRTree(List<Block> blocks, int blockSize)
	{
		if(blocks.size() == 0)
			return dummyRTree();
		RTreeNode list = null;
		for(int i = 0; i < blocks.size(); i++)
		{
			Block range = blocks.get(i);
			RTreeNode node = new RTreeNode();
			node.startChromIx = range.chrId;
			node.endChromIx = range.chrEnd;
			node.startBase = range.chrStart;
			node.endBase = range.chrEnd;
			node.startFileOffset = range.offsetInFile;
			node.endFileOffset = range.offsetInFile + range.compressedLength;
			//node.endFileOffset = i+1<blocks.length?blocks[i+1].offsetInFile:endFileOffset;
			
			node.next = list;
			list=node;
		}
		list = RTreeNode.reverse(list);

		
		RTreeNode root = list;
		int levelCount = 1;
		while(root.next != null || levelCount < 2)
		{
			list = null;
			int slotsUsed = blockSize;
			RTreeNode parent = null;
			RTreeNode next = null;
			for(RTreeNode node = root; node != null; node = next)
			{
				next = node.next;
				if(slotsUsed >= blockSize)
				{
					slotsUsed = 1;
					parent = new RTreeNode();
					parent.children = node;
					node.parent = parent;
					node.next = null;
					list = RTreeNode.addHead(list,parent);
				}
				else
				{
					++slotsUsed;
					parent.children = RTreeNode.addHead(parent.children, node);
					if (node.startChromIx < parent.startChromIx)
	                {
						parent.startChromIx = node.startChromIx;
						parent.startBase = node.startBase;
	                }
					else if (node.startChromIx == parent.startChromIx)
	                {
						if(node.startBase < parent.startBase)
							parent.startBase = node.startBase;
	                }
					if(node.endChromIx > parent.endChromIx)
	                {
						parent.endChromIx = node.endChromIx;
						parent.endBase = node.endBase;
	                }
					else if (node.endChromIx == parent.endChromIx)
	                {
						if(node.endBase > parent.endBase)
							parent.endBase = node.endBase;
	                }

				}
			}
			list = RTreeNode.reverse(list);
			for(RTreeNode node = list; node != null; node=node.next)
				node.children = RTreeNode.reverse(node.children);
			root = list;
			levelCount++;
		}
		
		RTree tree = new RTree();
		tree.root = root;
		tree.levelCount = levelCount;
		tree.blockSize = blockSize;
		tree.leafCount = blocks.size();
		return tree;
	}
	
	private static RTree dummyRTree() {
		RTree res = new RTree();
		res.levelCount = 0;
		res.root = null;
		return res;
	}
	
}
