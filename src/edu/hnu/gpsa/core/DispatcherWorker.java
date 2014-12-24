package edu.hnu.gpsa.core;

import java.io.IOException;
import java.util.Stack;

import edu.hnu.gpsa.graph.MapperCore;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class DispatcherWorker extends Task {

	private static int counter = 0;
	private int did = counter++;

	private int currentoffset;
	private SequenceInterval interval;
	private Object val;

	Mailbox<Signal> signals = new Mailbox<Signal>(3);

	private int sequence;
	private Handler handler;
	private Manager mgr;
	boolean isOutdegreeMatters;
	private boolean isWeightMatters;

	public DispatcherWorker(SequenceInterval interval, Handler handler, boolean isOutdegreeMatters, Manager mgr) {
		if (interval != null) {
			this.interval = interval;
			sequence = interval.start;
			currentoffset = interval.startOffset;
		}
		this.mgr = mgr;
		this.handler = handler;
		this.isOutdegreeMatters = isOutdegreeMatters;
	}

	public void offsetIncrement() {
		currentoffset += 4;
	}

	public void offsetReset() {
		currentoffset = interval.startOffset;
	}

	public void sequenceIncrement() {
		++sequence;
	}

	public void sequenceReset() {
		sequence = interval.start;

	}

	public void restAndStart() {
		sequenceReset();
		offsetReset();
	}

	@SuppressWarnings("unchecked")
	public void execute() throws Pausable, IOException {
		val = handler.init(sequence);
		Signal s = Signal.MANAGER_ITERATION_START;
		byte[] msg = null;
		int lastDest = -1;
		int dest = -1;
		int vid = -1;
		int i = 0;
		int tos = 0;
		boolean inData = false;
		Stack<Integer> stack = new Stack<Integer>();
		int sizeOfM = GlobalVaribaleManager.mConv.sizeOf();
		// int sizeOfV = GlobalVaribaleManager.vConv.sizeOf();
		int outdegree = 0;
		Object msgVal = null;

		while (s != Signal.SYSTEM_OVER) {

			if (interval != null) {
				while (sequence < interval.end && currentoffset < interval.endOffset) {

					while ((vid = GlobalVaribaleManager.csrMC.getInt(currentoffset)) == -1) {
						offsetIncrement();
						sequenceIncrement();
					}

					value(sequence);

					if ((float)val<0) { // 如果该sequence处的顶点在上一个超步中没有发生更新则跳过，进入下一个sequence的数据区
						while (currentoffset < interval.endOffset && (vid = GlobalVaribaleManager.csrMC.getInt(currentoffset)) != -1) {
							offsetIncrement();
						}
						while (currentoffset < interval.endOffset && (vid = GlobalVaribaleManager.csrMC.getInt(currentoffset)) == -1) {
							offsetIncrement();
							sequenceIncrement();
						}
					} else {// 数据发生了更新

						if (isOutdegreeMatters) {

							// 处理一个vertex entry
							while ((vid = GlobalVaribaleManager.csrMC.getInt(currentoffset)) != -1) {
								if (!inData) {
									outdegree = vid;
									inData = !inData;
								}
								if (lastDest == -1) {
									lastDest = locate(vid);
									stack.push(vid);
								} else {
									dest = locate(vid);
									if (dest == lastDest) {
										stack.push(vid);
									} else {
										tos = stack.size();
										msg = new byte[tos * 4 + sizeOfM];
										msgVal = handler.msgVal(val, outdegree, null);
										GlobalVaribaleManager.mConv.setValue(msg, msgVal);
										for (i = 0; i < tos; i++) {
											GlobalVaribaleManager.iConv.setValue(msg, stack.pop(), sizeOfM + i * 4);
										}
										// send message
										mgr.send(lastDest, msg);
										lastDest = dest;
										stack.push(vid);
									}
								}
								offsetIncrement();
							}

							if (!stack.isEmpty()) {
								tos = stack.size();
								msg = new byte[tos * 4 + sizeOfM];
								msgVal = handler.msgVal(val, outdegree, null);
								GlobalVaribaleManager.mConv.setValue(msg, msgVal);
								for (i = 0; i < tos; i++) {
									int t = stack.pop();
									GlobalVaribaleManager.iConv.setValue(msg, t, sizeOfM + i * 4);
//									System.out.println("sequence " + sequence + " send message to " + t + " and msgval is " + msgVal);

								}
								// send message
								mgr.send(lastDest, msg);
							}

							lastDest = -1;
							inData = !inData;

						} else {

							while ((vid = GlobalVaribaleManager.csrMC.getInt(currentoffset)) != -1) {

								if (lastDest == -1) {
									lastDest = locate(vid);
									stack.push(vid);
								} else {
									dest = locate(vid);
									if (dest == lastDest) {
										stack.push(vid);
									} else {
										tos = stack.size();
										msg = new byte[tos * 4 + sizeOfM];
										GlobalVaribaleManager.mConv.setValue(msg, val);
										for (i = 0; i < tos; i++) {
											int t = stack.pop();
											GlobalVaribaleManager.iConv.setValue(msg, t, sizeOfM + i * 4);
//									if (0 == did)
//										System.out.println("sequence " + sequence + " send message to " + t + " and msgval is " + val);
										}
										// send message
										mgr.send(lastDest, msg);
										lastDest = dest;
										stack.push(vid);
									}
								}
								offsetIncrement();
							}

							if (!stack.isEmpty()) {
								tos = stack.size();
								msg = new byte[tos * 4 + sizeOfM];
								GlobalVaribaleManager.mConv.setValue(msg, val);
								for (i = 0; i < tos; i++) {
									int t = stack.pop();
									GlobalVaribaleManager.iConv.setValue(msg, t, sizeOfM + i * 4);
//									if (0 == did)
//										System.out.println("sequence " + sequence + " send message to " + t + " and msgval is " + val);

								}
								// send message
								mgr.send(lastDest, msg);
							}

							lastDest = -1;

						}

						offsetIncrement();
						sequenceIncrement();
					}
				}
				restAndStart();
			}

			// System.out.println("current iteration dispatch finished notify manager"
			// );
			// 通知manager，本迭代的分发任务完成
			mgr.noteDispatch(Signal.DISPATCHER_ITERATION_DISPATCH_OVER);

			// 在通知完manager后，到收到来自manager的通知之前，会有一段空闲时间，这里可以添加一些清理或者监控的功能，达到最大的CPU利用率
			if (zeroIte)
				zeroIte = !zeroIte; //
			
			

			s = signals.get();

		}

	}

	private void value(int currentSequence) throws IOException {
		long offset = index(currentSequence, 0);
		getValue(offset);
	}
	
	@SuppressWarnings("unchecked")
	public boolean isUnupdated(){
		byte[] array = new byte[GlobalVaribaleManager.vConv.sizeOf()];
		GlobalVaribaleManager.vConv.setValue(array, val);
		return (array[0] & 0x80)!=0;
	}

	boolean zeroIte = true;

	public void getValue(long offset) throws IOException {
		// 获取当前sequence的value值
		if (val instanceof Long) {
			if (zeroIte)
				val = GlobalVaribaleManager.valMC.getLong(offset) & 0x7f_ff_ff_ff_ff_ff_ff_ffL;
			else
				val = GlobalVaribaleManager.valMC.getLong(offset);
		} else if (val instanceof Double) {
			if (zeroIte)
				val = GlobalVaribaleManager.valMC.getDouble(offset) * -1;
			else
				val = GlobalVaribaleManager.valMC.getDouble(offset);
		} else if (val instanceof Integer) {
			if (zeroIte)
				val = GlobalVaribaleManager.valMC.getInt(offset) & 0x7f_ff_ff_ff;
			else
				val = GlobalVaribaleManager.valMC.getInt(offset);
		} else if (val instanceof Float) {
			if (zeroIte)
				val = GlobalVaribaleManager.valMC.getFloat(offset) * -1;
			else
				val = GlobalVaribaleManager.valMC.getFloat(offset);
		} else {
			// 这里用来处理更加复杂的数据，通过字节数组来转换，但是对于目前的理论实现应用而言，基本数据类型够用
			byte[] data = GlobalVaribaleManager.valMC.get(0, GlobalVaribaleManager.vConv.sizeOf());
			val = GlobalVaribaleManager.vConv.getValue(data);
		}
	}

	public int locate(int id) {
		return id * Manager.ncomputer / (Manager.maxid + 1);
	}

	public void putSignal(Signal managerIterationStart) throws Pausable {
		signals.put(managerIterationStart);
	}

	public long index(int vid, int type) {
		return mgr.index(vid, type);
	}

}
