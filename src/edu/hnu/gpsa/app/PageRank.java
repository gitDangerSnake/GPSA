package edu.hnu.gpsa.app;

import java.io.IOException;

import edu.hnu.gpsa.core.Handler;
import edu.hnu.gpsa.core.Manager;
import edu.hnu.gpsa.datablock.FloatConverter;

public class PageRank {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		FloatConverter fc = new FloatConverter();
		Handler handler = new PageRankHandler();
//		Manager mgr = new Manager("/home/labserver/gpsa_test/pr/journal/journal", fc, null, fc, 256, 4096, 5, handler,true);
//		Manager mgr = new Manager("/home/labserver/gpsa_test/pr/google/google", fc, null, fc, 256, 4096, 5, handler,true);
		Manager mgr = new Manager("/home/labserver/gpsa_test/pr/verify/verify", fc, null, fc, 2, 16, 5, handler,true);
		mgr.run();
	}

}
