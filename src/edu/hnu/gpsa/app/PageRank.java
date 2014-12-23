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
		Manager mgr = new Manager("/home/labserver/CG/google", fc, null, fc, 256, 2048, 4, handler,true);
		mgr.run();
	}

}
