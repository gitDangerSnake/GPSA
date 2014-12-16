package edu.hnu.gpsa.core;

import edu.hnu.gpsa.datablock.BytesToValueConverter;
import edu.hnu.gpsa.datablock.IntConverter;
import edu.hnu.gpsa.graph.MapperCore;

public class GlobalVaribaleManager {

	protected static MapperCore csrMC;
	protected static MapperCore valMC;

	protected static BytesToValueConverter vConv;
	protected static BytesToValueConverter mConv;
	protected static BytesToValueConverter eConv;
	protected static IntConverter iConv = new IntConverter();

	public static void init(MapperCore csr, MapperCore val,
			BytesToValueConverter v, BytesToValueConverter e,
			BytesToValueConverter m) {
		csrMC = csr;
		valMC = val;
		vConv = v;
		mConv = m;
		eConv = e;
	}
}
