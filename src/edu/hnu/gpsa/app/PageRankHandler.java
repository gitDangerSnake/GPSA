package edu.hnu.gpsa.app;

import edu.hnu.gpsa.core.Handler;

public class PageRankHandler implements Handler {

	@Override
	public Object init(int sequence) {
		return 1.0f;
	}


	@Override
	public Object compute(Object val, Object mVal) {
		return (float)((float)val + 0.85*(float)mVal);
	}

	@Override
	public boolean isUpdated(Object newVal, Object oldVal) {
		return true;
	}


	@Override
	public Object msgVal(Object val, int outdegree, Object object) {
		return (float)((float)val / outdegree);
	}

}
