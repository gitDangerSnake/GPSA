package edu.hnu.gpsa.app;

import edu.hnu.gpsa.core.Handler;

public class BFSHandler implements Handler{

	@Override
	public Object init(int sequence) {
		if(sequence == 0) return 0;
		else return Integer.MAX_VALUE;
	}

	@Override
	public Object compute(Object val, Object mVal) {
		int oldVal = (int)val;
		int newVal = (int)mVal;
		if(oldVal < newVal) return newVal+1;
		else return oldVal;
	}

	@Override
	public boolean isUpdated(Object newVal, Object oldVal) {
		return (int)newVal != (int)oldVal;
	}

}
