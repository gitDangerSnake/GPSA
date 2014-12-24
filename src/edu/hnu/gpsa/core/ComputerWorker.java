package edu.hnu.gpsa.core;

import java.io.IOException;
import java.util.BitSet;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class ComputerWorker extends Task {
	private static int counter = 0;
	private int cwid;
	private Handler handler;
	private Manager mgr;
	private BitSet fisrtMsg;
	private int nv;
	private Object val;

	Mailbox<Object> messages = new Mailbox<Object>(10000);

	public ComputerWorker(Handler handler, int nv, Manager mgr) {
		cwid = counter++;
		this.handler = handler;
		this.nv = nv;
		fisrtMsg = new BitSet(nv);
		this.mgr = mgr;
		val = handler.init(0);
//		System.out.println("constructor --" + (val instanceof Long));


	}

	public void execute() throws Pausable, IOException {

		byte[] msg = null;
		Object o = messages.get();
		int sizeOfm = GlobalVaribaleManager.mConv.sizeOf();
		int sizeOfv = GlobalVaribaleManager.vConv.sizeOf();
		Object mVal = null;
		Object newVal = null;
		long offset = 0;
		int translateId = 0;
		int lastTo = -1;
		Object lastVal = val;
//		System.out.println("before execute " + (val instanceof Long));

		while (o != Signal.SYSTEM_OVER) {
			if (o instanceof byte[]) {
				msg = (byte[]) o;
				int loop = (msg.length - sizeOfm) / 4;
				mVal = GlobalVaribaleManager.mConv.getValue(msg);
				for (int i = 0; i < loop; i++) {
					int to = GlobalVaribaleManager.iConv.getValue(msg, i * 4
							+ sizeOfm, 4);
					if (lastTo != to) {
						translateId = translate(to);
						if (fisrtMsg.get(translateId)) {
							offset = index(to, 1);
						} else {
							offset = index(to, 0);
							fisrtMsg.set(translateId);
						}
						// 获取顶点to的value
						if (val instanceof Long) {
							val = GlobalVaribaleManager.valMC.getLong(offset) & 0x7f_ff_ff_ff_ff_ff_ff_ffL;
							
						} else if (val instanceof Double) {
							val = GlobalVaribaleManager.valMC.getDouble(offset);
							if((double)val < 0){
								val = (double) val * -1;
							}
						} else if (val instanceof Integer) {
							val = GlobalVaribaleManager.valMC.getInt(offset) & 0x7f_ff_ff_ff;
//							System.out.println("get val of vertex " + to + " and value is " + val + " original value is" + GlobalVaribaleManager.valMC.getInt(offset)+" and message value is" + mVal);
						} else if (val instanceof Float) {
							val = GlobalVaribaleManager.valMC.getFloat(offset);
							if((float)val <0){
								val = (float)val *-1;
							}
						} else {
							byte[] data = GlobalVaribaleManager.valMC.get(
									offset, sizeOfv);
							val = GlobalVaribaleManager.vConv.getValue(data);
						}
					} else {
						val = lastVal;
					}

					// 然后对vlaue和m进行计算
					
//					System.out.println(val instanceof Long);
					newVal = handler.compute(val, mVal);
					lastVal = newVal;
					lastTo = to;

					// 写入value
					if (handler.isUpdated(val, newVal)) {
						writeValue(offset, newVal);
					} else if (fisrtMsg.get(translateId)) {
						writeNegValue(offset, val);
					}
				}
			} else if (o instanceof Signal) {
				if (o == Signal.MANAGER_ITERATION_COMPUTE_OVER) {
					// 通知manager该compute worker上的计算操作已经完成
					mgr.noteCompute(Signal.COMPUTER_COMPUTE_OVER);
					fisrtMsg.clear();
				}
			}
			o = messages.get();
		}
	}

	private void writeValue(long offset, Object newVal) {
		if (val instanceof Long) {
			GlobalVaribaleManager.valMC.putLong(offset, (long) newVal);
		} else if (val instanceof Double) {
			GlobalVaribaleManager.valMC.putDouble(offset, (double) newVal);
		} else if (val instanceof Integer) {
			// System.out.println("newVal is" + newVal +" and oldVal is" +
			// val+" and mVal is" + mVal+"worker " + cwid+
			// " is about to update the value to buffer file");
			GlobalVaribaleManager.valMC.putInt(offset, (int) newVal);
		} else if (val instanceof Float) {
			// System.out.println("newVal is" + newVal +" and oldVal is" +
			// val+" and mVal is" + mVal+"worker " + cwid+
			// " is about to update the value to buffer file");
			GlobalVaribaleManager.valMC.putFloat(offset, (float) newVal);
		} else {
			// 处理其他复杂类型的数据结构，有待进一步实现，但是对于实验用的例子，以上四种数据类型够用了

		}
	}

	private void writeNegValue(long offset, Object newVal) {
		if (val instanceof Long) {
			GlobalVaribaleManager.valMC.putLong(offset,
					((long) newVal | 0x80_00_00_00_00_00_00_00L));
		} else if (val instanceof Double) {
			GlobalVaribaleManager.valMC.putNegDouble(offset, (double) newVal);
		} else if (val instanceof Integer) {
			// System.out.println("newVal is" + newVal +" and oldVal is" +
			// val+" and mVal is" + mVal+"worker " + cwid+
			// " is about to update the value to buffer file");
			GlobalVaribaleManager.valMC.putInt(offset,
					((int) newVal | 0x80_00_00_00));
		} else if (val instanceof Float) {
			// System.out.println("newVal is" + newVal +" and oldVal is" +
			// val+" and mVal is" + mVal+"worker " + cwid+
			// " is about to update the value to buffer file");
			GlobalVaribaleManager.valMC.putNegFloat(offset, (float) newVal);
		} else {
			// 处理其他复杂类型的数据结构，有待进一步实现，但是对于实验用的例子，以上四种数据类型够用了

		}
	}

	private int translate(int to) {
		return to % nv;
	}

	public void putMsg(Object msg) {
		messages.putnb(msg);
	}

	public long index(int sequence, int type) {
		return mgr.index(sequence, type);
	}

}
