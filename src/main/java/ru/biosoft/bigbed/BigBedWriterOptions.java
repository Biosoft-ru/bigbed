package ru.biosoft.bigbed;

public class BigBedWriterOptions {
	public int blockSize = 256;//number of records per node in BPlusTree index or number of records in RTreeIndex node
	public int itemsPerSlot = 512;//number of BedEntry items per block in BigBedFile
	public boolean compress = true;
	
	public AutoSql autoSql;
	public int bedN;//number of standard BED columns, should be in range [3,15]
	public int[] extraIndexColumns = {};
	
	public String fieldSep="\\s+";
}
