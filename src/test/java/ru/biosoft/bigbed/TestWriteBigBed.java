package ru.biosoft.bigbed;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TestWriteBigBed {
	@Test
	public void test1() throws Exception {
		BigBedWriterOptions options = new BigBedWriterOptions();
		options.compress = false;
		
		List<ChromInfo> chrs = new ArrayList<>();
		ChromInfo chr1 = new ChromInfo();
		chr1.id = 0;
		chr1.name = "chr1";
		chr1.length = 1000;
		chrs.add(chr1);
		
		
		List<BedEntry> sites = new ArrayList<>();
		sites.add(new BedEntry(0, 100, 200));
		sites.add(new BedEntry(0, 110, 120));
		sites.add(new BedEntry(0, 900, 950));
		
		BigBedWriter.write(sites, chrs, new File("test-data/test1_write.bb"), options);
		
		BigBedFile bb = BigBedFile.read("test-data/test1_write.bb");
		assertEquals(3, bb.getSiteCount());
		assertThat(bb.queryIntervals("chr1", 0, 1000, 0), is(sites));
	}
	
	@Test
	public void testCompress() throws Exception {
		BigBedWriterOptions options = new BigBedWriterOptions();
		options.compress = true;
		
		List<ChromInfo> chrs = new ArrayList<>();
		ChromInfo chr1 = new ChromInfo();
		chr1.id = 0;
		chr1.name = "chr1";
		chr1.length = 1000;
		chrs.add(chr1);
		
		
		List<BedEntry> sites = new ArrayList<>();
		sites.add(new BedEntry(0, 100, 200));
		sites.add(new BedEntry(0, 110, 120));
		sites.add(new BedEntry(0, 900, 950));
		
		BigBedWriter.write(sites, chrs, new File("test-data/test1_write_z.bb"), options);
		
		BigBedFile bb = BigBedFile.read("test-data/test1_write_z.bb");
		assertEquals(3, bb.getSiteCount());
		assertThat(bb.queryIntervals("chr1", 0, 1000, 0), is(sites));
	}
	
	@Test
	public void testTotalSummary() throws Exception {
		BigBedWriterOptions options = new BigBedWriterOptions();
		options.compress = true;
		
		List<ChromInfo> chrs = new ArrayList<>();
		ChromInfo chr1 = new ChromInfo();
		chr1.id = 0;
		chr1.name = "chr1";
		chr1.length = 1000;
		chrs.add(chr1);
		
		
		List<BedEntry> sites = new ArrayList<>();
		sites.add(new BedEntry(0, 100, 200));
		sites.add(new BedEntry(0, 110, 120));
		sites.add(new BedEntry(0, 900, 950));
		
		BigBedWriter.write(sites, chrs, new File("test-data/test1_write_z.bb"), options);
		
		BigBedFile bb = BigBedFile.read("test-data/test1_write_z.bb");
		SummaryElement summary = bb.getTotalSummary();
		assertEquals(150, summary.validCount, 0);
		assertEquals(160, summary.sum, 0);
		assertEquals(1, summary.minVal, 0);
		assertEquals(2, summary.maxVal, 0);
		assertEquals(1*1*140+2*2*10, summary.sumSq, 0);
	}
	
	@Test
	public void testWriteExtraIndex() throws Exception {
		BigBedWriterOptions options = new BigBedWriterOptions();
		options.compress = true;
		
		List<ChromInfo> chrs = new ArrayList<>();
		ChromInfo chr1 = new ChromInfo();
		chr1.id = 0;
		chr1.name = "chr1";
		chr1.length = 1000;
		chrs.add(chr1);
		
		
		List<BedEntry> sites = new ArrayList<>();
		BedEntry e = new BedEntry(0, 100, 200);
		e.data = "name1\tX".getBytes(StandardCharsets.UTF_8);
		sites.add(e);
		e = new BedEntry(0, 110, 120);
		e.data = "name2\tY".getBytes(StandardCharsets.UTF_8);
		sites.add(e);
		e = new BedEntry(0, 900, 950);
		e.data = "name3\tX".getBytes(StandardCharsets.UTF_8);
		sites.add(e);
		
		options.bedN = 3;
		options.autoSql = AutoSql.defaultBed(3, 5);
		options.extraIndexColumns = new int[] {3,4};
		
		BigBedWriter.write(sites, chrs, new File("test-data/test1_write_ei.bb"), options);
		
		BigBedFile bb = BigBedFile.read("test-data/test1_write_ei.bb");
		assertEquals(3, bb.getSiteCount());
		//assertThat(bb.queryIntervals("chr1", 0, 1000, 0), is(sites));
		List<BedEntry> expected = new ArrayList<>();
		expected.add(sites.get(1));
		assertThat(bb.queryExtraIndex(3, "name2", 0),is(expected));
		
		expected.clear();
		expected.add(sites.get(0));
		expected.add(sites.get(2));
		assertThat(bb.queryExtraIndex(4, "X", 0),is(expected));
	}
}
