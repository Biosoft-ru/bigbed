package ru.biosoft.bigbed;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TestLargeFile {
	@Test
	public void test1() throws Exception {
		BigBedFile bb = new BigBedFile("test-data/extra-index/C119_DPI_ROBUST.bb");
		bb.read();
		List<BedEntry> res = bb.queryIntervals("chr1", 0, 21950, 0);
		
		ChromInfo chrInfo = bb.getChromInfo("chr1");
		
		List<BedEntry> expected = Arrays.asList(
				new BedEntry(chrInfo.id, 21882, 21888),
				new BedEntry(chrInfo.id, 21896, 21907),
				new BedEntry(chrInfo.id, 21906, 21917),
				new BedEntry(chrInfo.id, 21909, 21914));
		assertThat(res, is(expected));
		/*
chr1    21882   21888   galGal6_1       1       -       1       0       .       .
chr1    21896   21907   galGal6_2       1       -       1       5       .       .
chr1    21906   21917   galGal6_3       1       +       1       0       .       .
chr1    21909   21914   galGal6_4       1       -       1       2       .       .

		 */
		bb.close();
	}
	
}
