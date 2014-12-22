package edu.hnu.gpsa.app;

import java.io.IOException;

import edu.hnu.gpsa.core.Handler;
import edu.hnu.gpsa.core.Manager;
import edu.hnu.gpsa.datablock.IntConverter;

public class BFS {

	public static void main(String[] args) throws IOException {
		IntConverter ic = new IntConverter();
		Handler handler = new BFSHandler();
		Manager mgr = new Manager("/home/labserver/CG/google", ic, null, ic, 256, 4096, 5, handler);
		mgr.run();
	}
}
