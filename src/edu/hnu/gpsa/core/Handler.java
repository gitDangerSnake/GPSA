package edu.hnu.gpsa.core;

public interface Handler {
	
	Object init(int sequence);
	Object compute(Object val,Object mVal);
	boolean isUpdated(Object newVal,Object oldVal);
	Object msgVal(Object val, int outdegree, Object weight);

}
