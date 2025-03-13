package ru.biosoft.bigbed;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ru.biosoft.bigbed.AutoSql.Column;

public class TestAutoSql {
	 @Test
	 public void test1() {
	   	String str =
	      "table test	\"test is test\"\n"
	    + "(\n"
	    + "string  chrom;          \"Chr\"\n"
	    + "uint    chromStart;     \"start\"\n"
	    + "uint    chromEnd;       \"end\"\n"
	    + "string  name;           \"Gene name\"\n"
	    + ")\n";
	
		AutoSql res = AutoSql.parse(str);
		assertEquals("test", res.name);
		assertEquals("test is test", res.description);
		assertEquals(4, res.columns.size());
		assertEquals(new Column("string", "chrom", "Chr"), res.columns.get(0));
		assertEquals(new Column("uint", "chromStart", "start"), res.columns.get(1));
		assertEquals(new Column("uint", "chromEnd", "end"), res.columns.get(2));
		assertEquals(new Column("string", "name", "Gene name"), res.columns.get(3));
	 }
		
	 @Test
	 public void test2() {
	    String str = "table cage_peaks_galGal6_C119_DPI_ROBUST\n"
	    + "\"GTRD TSS track galGal6 C119_DPI_ROBUST\"\n"
	    + "	(\n"
	    + "	string  chrom;          \"Reference sequence chromosome or scaffold\"\n"
	    + "	uint    chromStart;     \"Start position of feature on chromosome\"\n"
	    + "	uint    chromEnd;       \"End position of feature on chromosome\"\n"
	    + "	string  name;           \"Stable id of TSS\"\n"
	    + "	uint    score;          \"Score (unused)\"\n"
	    + "	char[1] strand;         \"+ or - for strand\"\n"
	    + "	uint    version;        \"Version of TSS\"\n"
	    + "	uint    summit;         \"Offset from chromStart of the main TSS position\"\n"
	    + "	string  type;           \"TSS type (promoter/enhancer)\"\n"
	    + "	string  geneSymbol;     \"Gene Symbol\"\n"
	    + "	)";
		AutoSql res = AutoSql.parse(str);
		assertEquals("cage_peaks_galGal6_C119_DPI_ROBUST", res.name);
		assertEquals("GTRD TSS track galGal6 C119_DPI_ROBUST", res.description);
		assertEquals(10, res.columns.size());
		assertEquals(new Column("string", "chrom", "Reference sequence chromosome or scaffold"), res.columns.get(0));
		assertEquals(new Column("uint", "chromStart", "Start position of feature on chromosome"), res.columns.get(1));
		assertEquals(new Column("uint", "chromEnd", "End position of feature on chromosome"), res.columns.get(2));
		assertEquals(new Column("string", "name", "Stable id of TSS"), res.columns.get(3));
		assertEquals(new Column("uint", "score", "Score (unused)"), res.columns.get(4));
		assertEquals(new Column("char[1]", "strand", "+ or - for strand"), res.columns.get(5));
		assertEquals(new Column("uint", "version", "Version of TSS"), res.columns.get(6));
		assertEquals(new Column("uint", "summit", "Offset from chromStart of the main TSS position"),
				res.columns.get(7));
		assertEquals(new Column("string", "type", "TSS type (promoter/enhancer)"), res.columns.get(8));
		assertEquals(new Column("string", "geneSymbol", "Gene Symbol"), res.columns.get(9));
	}
		
	@Test
	public void test3() {
		String str = "table \"PEAKS034859_AHR_P35869_MACS2_113\"\n"
				+ "	\"PEAKS034859: peaks obtained from ChIP-seq experiment (EXP031790; Target: AHR (P35869)) by MACS2\"\n"
				+ "	(\n"
				+ "	string  chrom;		\"Reference sequence chromosome or scaffold\"\n"
				+ "	uint    chromStart;	\"Start position of peak\"\n"
				+ "	uint    chromEnd;	\"End position of peak\"\n"
				+ "	string  name;		\"Name of gene\"\n"
				+ "	string uniprot_id;	\"Uniprot ID\"\n"
				+ "	uint	abs_summit;	\"Absolute peak summit position\"\n"
				+ "	double	pileup;	\"Pileup height at peak summit\"\n"
				+ "	double	p_value;	\"-log10(p-value) for the peak summit\"\n"
				+ "	double	fold_enrichment;	\"Fold enrichment for this peak summit against random Poisson distribution with local lambda\"\n"
				+ "	double	q_value;	\"-log10(q-value) for the peak summit\"\n"
				+ "	uint	ID;	\"Peak ID\"\n"
				+ "	)";
		AutoSql res = AutoSql.parse(str);
		assertEquals("PEAKS034859_AHR_P35869_MACS2_113", res.name);
		assertEquals("PEAKS034859: peaks obtained from ChIP-seq experiment (EXP031790; Target: AHR (P35869)) by MACS2", res.description);
		assertEquals(11, res.columns.size());
		assertEquals(new Column("string", "chrom", "Reference sequence chromosome or scaffold"), res.columns.get(0));
		assertEquals(new Column("uint", "chromStart", "Start position of peak"), res.columns.get(1));
		assertEquals(new Column("uint", "chromEnd", "End position of peak"), res.columns.get(2));
		assertEquals(new Column("string", "name", "Name of gene"), res.columns.get(3));
		assertEquals(new Column("string", "uniprot_id", "Uniprot ID"), res.columns.get(4));
		assertEquals(new Column("uint", "abs_summit", "Absolute peak summit position"), res.columns.get(5));
		assertEquals(new Column("double", "pileup", "Pileup height at peak summit"), res.columns.get(6));
		assertEquals(new Column("double", "p_value", "-log10(p-value) for the peak summit"), res.columns.get(7));
		assertEquals(new Column("double", "fold_enrichment", "Fold enrichment for this peak summit against random Poisson distribution with local lambda"), res.columns.get(8));
		assertEquals(new Column("double", "q_value", "-log10(q-value) for the peak summit"), res.columns.get(9));
		assertEquals(new Column("uint", "ID", "Peak ID"), res.columns.get(10));
	}
		
	@Test
	public void test4() {
	    String str = "table test\n"
	    		+ "\"test is test\"\n"
	    		+ "(\n"
	    		+ "string chrom; \"Chr\"\n"
	    		+ "uint chromStart; \"start\"\n"
	    		+ "uint chromEnd; \"end\"\n"
	    		+ "string name; \"Gene name\"\n"
	    		+ ")\n";
		AutoSql res = AutoSql.parse(str);
		assertEquals(str, res.toString());
	}
}
