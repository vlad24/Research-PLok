package ru.spbu.math.plok.model.client;

public class Query {

	int indexStart;
	int indexEnd;
	long timeStart;
	long timeEnd;
	long time;
	
	public Query(long time, int indexStart, int indexEnd, long timeStart, long timeEnd) {
		super();
		this.time = time;
		this.indexStart = indexStart;
		this.indexEnd = indexEnd;
		this.timeStart = timeStart;
		this.timeEnd = timeEnd;
	}

	public int getI1() {
		return indexStart;
	}

	public void setIndexStart(int indexStart) {
		this.indexStart = indexStart;
	}

	public int getI2() {
		return indexEnd;
	}

	public void setIndexEnd(int indexEnd) {
		this.indexEnd = indexEnd;
	}

	public long getJ2() {
		return timeEnd;
	}

	public void setTimeEnd(long timeEnd) {
		this.timeEnd = timeEnd;
	}
	
	public long getJ1() {
		return timeStart;
	}

	public void setTimeStart(long timeStart) {
		this.timeStart = timeStart;
	}
	
	public long getTime(){
		return time;
	}
	

}
