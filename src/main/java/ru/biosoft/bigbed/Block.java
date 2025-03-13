package ru.biosoft.bigbed;

//Block stores @siteCount sites in bigBed files with @offsetInFile
//The block may be compressed
//The sites are sorted by (chrId,chrStart)
public class Block {
	long offsetInFile;
	int compressedLength;
	int uncompressedLength;
	
	//all sites in this genomic range
	int chrId;
	int chrStart;
	int chrEnd;
	
	int siteCount;
	int siteOffset;//offset of the first site in the input list of BedEntry
}