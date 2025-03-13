bedToBigBed -type=bed3+1 -as=test.as -extraIndex=name test.bed chrom.sizes  test.bb
bigBedNamedItems -field=name test.bb geneXXX out.bed

