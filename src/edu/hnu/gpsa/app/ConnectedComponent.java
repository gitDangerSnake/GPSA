package edu.hnu.gpsa.app;

import java.io.IOException;

import edu.hnu.gpsa.core.Handler;
import edu.hnu.gpsa.core.Manager;
import edu.hnu.gpsa.datablock.IntConverter;

public class ConnectedComponent {

	public static void main(String[] args) throws IOException {
		IntConverter ic = new IntConverter();
		Handler handler = new ConnectedComponentHandler();
		Manager mgr = new Manager("google", ic, null, ic, 2, 16, 1, handler,false);
		mgr.run();
	}
}
