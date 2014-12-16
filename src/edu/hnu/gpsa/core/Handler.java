package edu.hnu.gpsa.core;

public interface Handler {
	
	Object init(int sequence);
	Object genMsgVal(Object val,int to,Object weight);
	Object compute(Object val,Object mVal);
	boolean isUpdated(Object newVal,Object oldVal);

}
