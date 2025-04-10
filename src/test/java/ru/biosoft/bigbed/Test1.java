package ru.biosoft.bigbed;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class Test1 {

	@Test
	public void test1() throws Exception {
		BigBedFile bb = new BigBedFile("test-data/test.bb");
		bb.read();
		List<BedEntry> res = bb.queryIntervals("chr1", 0, 115, 0);
		
		ChromInfo chrInfo = bb.getChromInfo("chr1");
		
		List<BedEntry> expected = Arrays.asList(
				new BedEntry(chrInfo.id, 1, 10),
				new BedEntry(chrInfo.id, 100, 110),
				new BedEntry(chrInfo.id, 105, 110));
		assertThat(res, is(expected));
		
		bb.close();
	}
	
	@Test
	public void test2() throws Exception {
		BigBedFile bb = new BigBedFile("test-data/test.bb");
		bb.read();
		List<ChromInfo> chrList = new ArrayList<>();
		bb.traverseChroms(chr->chrList.add(chr));
		bb.close();
		
		assertEquals(1, chrList.size());
		ChromInfo chr = chrList.get(0);
		assertEquals("chr1", chr.name);
		assertEquals(0, chr.id);
		assertEquals(10000, chr.length);
	}
	
	@Test
	public void test3() throws Exception {
		BigBedFile bb = new BigBedFile("test-data/test.bb");
		bb.read();
		assertEquals(4, bb.getSiteCount());
		bb.close();
	}
	
	@Test
	public void test4() throws Exception {
		BigBedFile bb = new BigBedFile("test-data/test.bb");
		bb.read();
		BigBedRandomAccess bbRA = new BigBedRandomAccess(bb);
		
		List<BedEntry> result = bbRA.fetch(1, 2);
		
		ChromInfo chrInfo = bb.getChromInfo("chr1");
		List<BedEntry> expected = Arrays.asList(
				new BedEntry(chrInfo.id, 100, 110),
				new BedEntry(chrInfo.id, 105, 110));
		assertThat(result, is(expected));

		bb.close();
	}
	
	@Test
	public void test5() throws Exception
	{
		BigWigFile bw = new BigWigFile("test-data/test.bw");
		bw.read();
		List<WigEntry> res = bw.queryIntervals("chr1", 2500, 3100, 0);
		
		ChromInfo chrInfo = bw.getChromInfo("chr1");
		
		List<WigEntry> expected = Arrays.asList(
				new WigEntry(chrInfo.id, 2300, 2600, -0.75),
				new WigEntry(chrInfo.id, 2600, 2900, -0.5),
				new WigEntry(chrInfo.id, 2900, 3200, -0.25));
		assertThat(res, is(expected));
		
		bw.close();
	}
}
