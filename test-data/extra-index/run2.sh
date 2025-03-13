#!/bin/bash -e

bedToBigBed -type=bed6+4 -as=C119_DPI_ROBUST.as -extraIndex=name,geneSymbol C119_DPI_ROBUST.bed galGal6.ucsc.chrom.sizes C119_DPI_ROBUST.bb 
bigBedNamedItems -field=name C119_DPI_ROBUST.bb galGal6_106544 out2_name.bed
bigBedNamedItems -field=geneSymbol C119_DPI_ROBUST.bb DHRS4 out2_gene.bed

