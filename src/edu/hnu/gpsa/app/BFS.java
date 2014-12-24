package edu.hnu.gpsa.app;

import java.io.IOException;

import edu.hnu.gpsa.core.Handler;
import edu.hnu.gpsa.core.Manager;
import edu.hnu.gpsa.datablock.IntConverter;

public class BFS {

	public static void main(String[] args) throws IOException {
		IntConverter ic = new IntConverter();
		Handler handler = new BFSHandler();
		Manager mgr = new Manager("google", ic, null, ic, 2, 16, 15, handler,false);
		mgr.run();
	}
}
