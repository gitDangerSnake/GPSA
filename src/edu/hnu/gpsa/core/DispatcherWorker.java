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

	public DispatcherWorker(SequenceInterval interval, Handler handler,
			boolean isOutdegreeMatters, Manager mgr) {
		this.interval = interval;
		sequence = interval.start;
		currentoffset = interval.startOffset;
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
		long offset = 0;
		int vid = -1;
		int lastSequence = -1;
		int i = 0;
		int tos = 0;
		boolean overFlag = false;
		boolean inData = false;
		Stack<Integer> stack = new Stack<Integer>();
		int sizeOfM = GlobalVaribaleManager.mConv.sizeOf();
		int sizeOfV = GlobalVaribaleManager.vConv.sizeOf();
		int outdegree = 0;
		Object msgVal = null;

		// TO DO : add logic to process outdegrees

		while (s != Signal.SYSTEM_OVER) {

			restAndStart();
			offset = index(lastSequence, 0);
			getValue(offset);

			lastSequence = sequence;
			while (currentoffset < interval.endOffset) {
				
				

				if (isOutdegreeMatters) {

					while ((vid = GlobalVaribaleManager.csrMC
							.getInt(currentoffset)) == -1) {
						sequenceIncrement();
						offsetIncrement();
						if (inData) {
							inData = false;
						}
						if (currentoffset == interval.endOffset) {
							overFlag = true;
							break;
						}
					}

					if (overFlag) {
						overFlag = false;
						break;
					}

					if (!inData) {
						inData = true;
						outdegree = vid;
						continue;
					}
					if (isWeightMatters) {
						
						

					} else {
						if (lastSequence != sequence) {
							offset = index(lastSequence, 0);
							getValue(offset);
							tos = stack.size();
							msg = new byte[tos * 4 + sizeOfM];
							msgVal = handler.msgVal(val,outdegree,null);
							GlobalVaribaleManager.mConv.setValue(msg, msgVal);
							for (i = 0; i < tos; ++i) {
								GlobalVaribaleManager.iConv.setValue(msg,
										stack.pop(), sizeOfM + i * 4);
							}
							// send message
							mgr.send(lastDest, msg);
							lastSequence = sequence;

						} else {
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
									GlobalVaribaleManager.mConv.setValue(msg,msgVal);
									for (i = 0; i < tos; i++) {
										GlobalVaribaleManager.iConv.setValue(
												msg, stack.pop(), sizeOfM + i
														* 4);
									}
									// send message
									mgr.send(lastDest, msg);

									lastDest = dest;
									stack.push(vid);

								}
							}
						}

					}
				} else {

					// System.out.println("Worker " + did +" start processing "
					// + sequence);
					while ((vid = GlobalVaribaleManager.csrMC
							.getInt(currentoffset)) == -1) {
						sequenceIncrement();
						offsetIncrement();
						if (currentoffset == interval.endOffset) {
							overFlag = true;
							break;
						}
					}
					if (overFlag) {
						overFlag = false;
						break;
					}

					if (lastSequence != sequence) {
						offset = index(lastSequence, 0);
						getValue(offset);
						tos = stack.size();
						msg = new byte[tos * 4 + sizeOfM];
						GlobalVaribaleManager.mConv.setValue(msg, val);
						for (i = 0; i < tos; ++i) {
							GlobalVaribaleManager.iConv.setValue(msg,
									stack.pop(), sizeOfM + i * 4);
						}
						// send message
						mgr.send(lastDest, msg);
						lastSequence = sequence;

					} else {
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
									GlobalVaribaleManager.iConv.setValue(msg,
											stack.pop(), sizeOfM + i * 4);
								}
								// send message
								mgr.send(lastDest, msg);

								lastDest = dest;
								stack.push(vid);

							}
						}
					}
				}

				offsetIncrement();
			}

			// System.out.println("current iteration dispatch finished notify manager"
			// );
			// 通知manager，本迭代的分发任务完成
			mgr.noteDispatch(Signal.DISPATCHER_ITERATION_DISPATCH_OVER);
			// System.out.println("success notify manager");
			s = signals.get();
		}
	}

	public void getValue(long offset) throws IOException {
		// 获取当前sequence的value值
		if (val instanceof Long) {
			val = GlobalVaribaleManager.valMC.getLong(offset);
		} else if (val instanceof Double) {
			val = GlobalVaribaleManager.valMC.getDouble(offset);
		} else if (val instanceof Integer) {
			val = GlobalVaribaleManager.valMC.getInt(offset);
		} else if (val instanceof Float) {
			val = GlobalVaribaleManager.valMC.getFloat(offset);
		} else {
			byte[] data = GlobalVaribaleManager.valMC.get(0,
					GlobalVaribaleManager.vConv.sizeOf());
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
