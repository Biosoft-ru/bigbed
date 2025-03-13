package ru.biosoft.bigbed;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

public class TestExtraIndex {
	@Test
	public void test1() throws ParseException, IOException {
		BigBedFile bb = new BigBedFile("test-data/extra-index/test.bb");
		bb.read();
		AutoSql autoSql = bb.getAutoSql();
		assertEquals("name", autoSql.columns.get(3).name);
		ExtraIndex index = bb.getExtraIndices().get(0);
		assertEquals("name", index.name);
		List<BedEntry> res = bb.queryExtraIndex("name", "geneXXX", 0);
		assertEquals(res.size(), 1);
		BedEntry e = res.get(0);
		assertEquals(e.chrId, bb.getChromInfo("chr2").id);
		assertEquals(1, e.start);
		assertEquals(19, e.end);
		assertEquals("geneXXX", new String(e.data));
		bb.close();
	}
	
	//@Test
	//Input file too big for git
	public void test2() throws ParseException, IOException {
		BigBedFile bb = new BigBedFile("test-data/extra-index/C119_DPI_ROBUST.bb");
		bb.read();
		
		List<BedEntry> res = bb.queryExtraIndex("name", "galGal6_106544", 0);
		assertEquals(res.size(), 1);
		BedEntry e = res.get(0);
		assertEquals(bb.getChromInfo("NC_040902.1").id, e.chrId);
		assertEquals(1840, e.start);
		assertEquals(1851, e.end);
		assertEquals("galGal6_106544\t1\t+\t1\t5\t.\t.", new String(e.data));
		
		res = bb.queryExtraIndex("geneSymbol", "DHRS4", 0);
		assertEquals(res.size(), 3);
		res.sort(Comparator.comparingInt(a->a.start));
		e = res.get(0);
		assertEquals(bb.getChromInfo("chr1").id, e.chrId);
		assertEquals(71603, e.start);
		assertEquals(71621, e.end);
		assertEquals("galGal6_33\t1\t-\t1\t12\tpromoter\tDHRS4", new String(e.data));
		
		e = res.get(1);
		assertEquals(bb.getChromInfo("chr1").id, e.chrId);
		assertEquals(71627, e.start);
		assertEquals(71636, e.end);
		assertEquals("galGal6_34\t1\t-\t1\t5\tpromoter\tDHRS4", new String(e.data));

		e = res.get(2);
		assertEquals(bb.getChromInfo("chr1").id, e.chrId);
		assertEquals(71655, e.start);
		assertEquals(71658, e.end);
		assertEquals("galGal6_35\t1\t-\t1\t2\tpromoter\tDHRS4", new String(e.data));

		
		bb.close();
	}
}
