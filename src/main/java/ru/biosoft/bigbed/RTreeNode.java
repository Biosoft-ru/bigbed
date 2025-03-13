package ru.biosoft.bigbed;

//In memory RTreeNode, used only during RTreeIndex building
public class RTreeNode {
	 RTreeNode next; /* Next on same level. */
	 RTreeNode children;     /* Child list. */
	 RTreeNode parent;       /* Our parent if any. */
	 int startChromIx;        /* Starting chromosome. */
	 int startBase;           /* Starting base position. */
	 int endChromIx;          /* Ending chromosome. */
	 int endBase;             /* Ending base. */
	 
	 long startFileOffset;     /* Start offset in file for leaves. */
	 long endFileOffset;       /* End file offset for leaves. */
	 
	 
		public static RTreeNode reverse(RTreeNode list) {
			RTreeNode res = null;

			while (list != null) {
				RTreeNode next = list.next;
				list.next = res;
				res = list;
				list = next;
			}

			return res;
		}

		public static RTreeNode addHead(RTreeNode list, RTreeNode node) {
			node.next = list;
			return node;
		}

		public static int count(RTreeNode list)
		{
			int res = 0;
			while(list != null)
			{
				res++;
				list = list.next;
			}
			return res;
		}
		
}
