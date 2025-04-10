package ru.biosoft.bigbed;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TestRandomAccess {

	@Test
	public void test1() throws Exception {
		BigBedFile bb = BigBedFile.read("test-data/extra-index/C119_DPI_ROBUST.bb");
		
		BigBedRandomAccess r = new BigBedRandomAccess(bb);
		ChromInfo chrInfo = bb.getChromInfo("chr1");
		
		List<BedEntry> res = r.fetch(69, 4);
		
		List<BedEntry> expected = Arrays.asList(
				new BedEntry(chrInfo.id, 21882, 21888),
				new BedEntry(chrInfo.id, 21896, 21907),
				new BedEntry(chrInfo.id, 21906, 21917),
				new BedEntry(chrInfo.id, 21909, 21914));
		assertThat(res, is(expected));
	}
}
