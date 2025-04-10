package ru.biosoft.bigbed;

public class WigEntry {
	public WigEntry(int chrId, int start, int end, double val) {
		this.chrId = chrId;
		this.start = start;
		this.end = end;
		this.val = val;
	}
	public int chrId;
	public int start,end;//chromosome coordinates, zero based half-open
	
	public double val;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + chrId;
		result = prime * result + end;
		result = prime * result + start;
		long temp;
		temp = Double.doubleToLongBits(val);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WigEntry other = (WigEntry) obj;
		if (chrId != other.chrId)
			return false;
		if (end != other.end)
			return false;
		if (start != other.start)
			return false;
		if (Double.doubleToLongBits(val) != Double.doubleToLongBits(other.val))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "WigEntry [chrId=" + chrId + ", start=" + start + ", end=" + end + ", val=" + val + "]";
	}
	
}
