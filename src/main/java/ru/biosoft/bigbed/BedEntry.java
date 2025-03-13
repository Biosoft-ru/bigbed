package ru.biosoft.bigbed;

public class BedEntry {
	public BedEntry(int chrId, int start, int end) {
		this.chrId = chrId;
		this.start = start;
		this.end = end;
	}
	public int chrId;
	public int start,end;//chromosome coordinates, zero based half-open
	public byte[] data;
	
	@Override
	public String toString() {
		return "BedEntry [chrId=" + chrId + ", start=" + start + ", end=" + end + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + chrId;
		result = prime * result + end;
		result = prime * result + start;
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
		BedEntry other = (BedEntry) obj;
		if (chrId != other.chrId)
			return false;
		if (end != other.end)
			return false;
		if (start != other.start)
			return false;
		return true;
	}
	
	
}
