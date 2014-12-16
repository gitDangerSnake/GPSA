package edu.hnu.gpsa.app;

import edu.hnu.gpsa.core.Handler;

public class ConnectedComponentHandler implements Handler{

	@Override
	public Object init(int sequence) {
		return sequence;
	}

	@Override
	public Object genMsgVal(Object val, int to, Object weight) {
		return val;
	}

	@Override
	public Object compute(Object val, Object mVal) {
		int v1 = (int)val;
		int v2 = (int)mVal;
		if( v2 < v1){
			return mVal ;
		}
		return val;
	}

	@Override
	public boolean isUpdated(Object newVal, Object oldVal) {
		return newVal == oldVal;
	}

}
