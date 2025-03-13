package ru.biosoft.bigbed;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class SummaryElement {
	public long validCount;
	public double minVal;
	public double maxVal;
	public double sum;
	public double sumSq;
	
	public void write(DataOutput out) throws IOException
	{
		out.writeLong(validCount);
		out.writeDouble(minVal);
		out.writeDouble(maxVal);
		out.writeDouble(sum);
		out.writeDouble(sumSq);
	}
	
	public void read(DataSource in) throws IOException {
		validCount = in.readLong();
		minVal = in.readDouble();
		maxVal = in.readDouble();
		sum = in.readDouble();
		sumSq = in.readDouble();
	}
	
	public void compute(Iterator<BedEntry> sites)
	{
		int curChr = 0;
		List<Integer> x = new ArrayList<>();
		while(sites.hasNext())
		{
			BedEntry e = sites.next();
			if(e.chrId != curChr)
			{
				makeSummaryFromChangePoints(x);
				x.clear();
				curChr = e.chrId;
			}
			x.add((e.start+1));//+1 to make 1-based
			x.add(-((e.end+1)));//negative sign means reduce coverage at this position
		}
		
		initValues();
		makeSummaryFromChangePoints(x);//process last chrom
	}

	private void initValues() {
		this.validCount = 0;
		this.minVal = Double.MAX_VALUE;
		this.maxVal = -Double.MAX_VALUE;
		this.sum = 0;
		this.sumSq = 0;
	}

	private void makeSummaryFromChangePoints(List<Integer> points) {
		points.sort(Comparator.comparingInt(a->Math.abs(a)));
		int intervalStart = -1;
		int val = 0;
		int i = 0;
		while(i < points.size())
		{
			int p = points.get(i);
			int intervalEnd = Math.abs(p);
			if(val > 0)
				addSummary(intervalEnd-intervalStart, val);

			intervalStart = Math.abs(p);
			while(i < points.size() && Math.abs(points.get(i)) == Math.abs(p))
			{
				if(p>0)
					val++;
				else
					val--;
				i++;
			}
		}
		if(val != 0)
			throw new AssertionError();
		
	}

	private void addSummary(int length, int coverage) {
		validCount += length;
		if(coverage < minVal)
			minVal = coverage;
		if(coverage > maxVal)
			maxVal = coverage;
		sum += ((double)length) * coverage;
		sumSq += ((double)length) * coverage * coverage;
	}

}
