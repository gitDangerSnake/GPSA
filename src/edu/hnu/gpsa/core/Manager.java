package edu.hnu.gpsa.core;

import edu.hnu.gpsa.datablock.BytesToValueConverter;
import edu.hnu.gpsa.graph.Filename;
import edu.hnu.gpsa.graph.Graph;
import edu.hnu.gpsa.graph.MapperCore;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

import java.io.*;
import java.util.BitSet;

public class Manager extends Task {

	protected static int ndispatcher;
	protected static int ncomputer;
	protected static int nedges;
	protected static int maxid;

	private boolean PINGPANG = true;

	Graph graph;
	String graphFilename;
	ComputerWorker[] cws;
	DispatcherWorker[] dws;
	private BitSet bits;
	private BitSet workerBit;
	int currIte;
	int endIte;
	Handler handler;
	boolean isOutdegreeMatters;

	Mailbox<Signal> computerMailbox = new Mailbox<Signal>(ncomputer + 1,
			ncomputer + 1);
	Mailbox<Signal> dispatcherMailbox = new Mailbox<Signal>(ndispatcher + 1,
			ndispatcher + 3);

	private MapperCore csrMC;

	private MapperCore valMC;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Manager(String graphFilename, BytesToValueConverter vConv,
			BytesToValueConverter eConv, BytesToValueConverter mConv,
			int ndispatcher, int ncomputer, int endIte, Handler handler,
			boolean isOutdegreeMatters) throws IOException {
		this.graphFilename = graphFilename;
		Manager.ndispatcher = ndispatcher;
		Manager.ncomputer = ncomputer;
		this.cws = new ComputerWorker[ncomputer];
		this.dws = new DispatcherWorker[ndispatcher];
		this.currIte = 0;
		this.endIte = endIte;
		this.isOutdegreeMatters = isOutdegreeMatters;
		graph = new Graph(graphFilename, "edgelist", eConv, vConv, mConv, null,
				isOutdegreeMatters);
		graph.preprocess();
		Manager.nedges = graph.getNumEdges();
		maxid = Graph.MAXID;
		bits = new BitSet(maxid + 1);
		this.handler = handler;

		int sizeOfVal = vConv.sizeOf();
		File csrfile = new File(Filename.csrFilename(graphFilename));
		File valfile = new File(Filename.vertexValueFilename(graphFilename));
		valfile.delete();
		valfile.createNewFile();

		csrMC = new MapperCore(csrfile, csrfile.length());
		valMC = new MapperCore(valfile, (maxid + 1) * 2 * sizeOfVal);

		byte[] valTemp = new byte[sizeOfVal];
		Object val = null;
		byte[] writeTemp = new byte[sizeOfVal * 2];

		for (int i = 0; i <= maxid; i++) {
			val = handler.init(i);
			vConv.setValue(valTemp, val);
			valTemp[0] |= 0x80;
			System.arraycopy(valTemp, 0, writeTemp, 0, sizeOfVal);			
			System.arraycopy(valTemp, 0, writeTemp, sizeOfVal, sizeOfVal);
			valMC.put(i * sizeOfVal * 2, writeTemp);
		}

		valTemp = null;

		GlobalVaribaleManager.init(csrMC, valMC, vConv, eConv, mConv);
	}

	public void initWorker() throws IOException {

		assignComputeWork();
		assignDispatchWork(ndispatcher, nedges, csrMC);
	}

	public void assignComputeWork() {
		int averg_per_computer = 0;
		if ((maxid + 1) % ncomputer == 0) {
			averg_per_computer = (maxid + 1) / ncomputer;
		} else {
			averg_per_computer = (maxid + 1) / ncomputer + 1;
		}

		for (int i = 0; i < ncomputer; i++) {
			cws[i] = new ComputerWorker(handler, averg_per_computer, this);
			cws[i].start();
		}
	}

	public void assignDispatchWork(int ndispatcher, int nedges, MapperCore mc)
			throws IOException {
		SequenceInterval[] sequenceIntervals = new SequenceInterval[ndispatcher];
		int averg_per_dispatcher = 0;
		if (nedges % ndispatcher == 0) {
			averg_per_dispatcher = nedges / ndispatcher;
		} else {
			averg_per_dispatcher = nedges / ndispatcher + 1;
		}
		System.out.println(averg_per_dispatcher);

		int left_sequence = 0;
		int right_sequence = 0;
		int left_offset = 0;
		int right_offset = 0;
		int counter = 0;
		int k = 0;

		long limit = mc.getSize();
		int to = -1;
		int lastTo = -1;

		if (isOutdegreeMatters) {
			while (right_offset < limit) {
				to = mc.getInt(right_offset);
				right_offset+=4;

				if (lastTo == -1 && to != -1) {					
					continue;
				} else {
					if (to == -1) {
						right_sequence++;
						if(counter >= averg_per_dispatcher){
							sequenceIntervals[k++] = new SequenceInterval(
									left_sequence, right_sequence, left_offset,
									right_offset);
							counter = 0;
							left_offset = right_offset;
							left_sequence = right_sequence;
							while (mc.getInt(right_offset) == -1) {
								right_sequence++;
								right_offset += 4;
								left_offset = right_offset;
								left_sequence = right_sequence;
								if (right_offset == limit)
									break;
							}
						}
						
					} else {
						counter++;
					}
					lastTo = to;
					
				}
			}
			
			if(left_sequence < right_sequence){
				sequenceIntervals[k] = new SequenceInterval(left_sequence,
						right_sequence, left_offset, right_offset);
			}

		} else {

			while (right_offset < limit) {

				to = mc.getInt(right_offset);
				right_offset += 4;
				if (to == -1) {
					right_sequence++;
					if(counter >= averg_per_dispatcher){
						System.out.println("current k is" + k + " current counter is" + counter);

						sequenceIntervals[k++] = new SequenceInterval(
								left_sequence, right_sequence, left_offset,
								right_offset);
						counter = 0;
						left_offset = right_offset;
						left_sequence = right_sequence;
						while (mc.getInt(right_offset) == -1) {
							right_sequence++;
							right_offset += 4;
							left_offset = right_offset;
							left_sequence = right_sequence;
							if (right_offset ==- limit)
								break;
						}
					}
				} else {
					counter++;
				}
				
				
			}
			
			if(left_sequence < right_sequence){
				sequenceIntervals[k] = new SequenceInterval(left_sequence,
						right_sequence, left_offset, right_offset);
			}

			
			
		}

		System.out.println("k == dispatcher ?" + (k==ndispatcher));
		
		for (int i = 0; i < ndispatcher; i++) {
			System.out.println(sequenceIntervals[i]);
			dws[i] = new DispatcherWorker(sequenceIntervals[i], handler,
					isOutdegreeMatters, this);
			dws[i].start();
		}
	}

	public void execute() throws Pausable {

		Signal s = null;
		int dispatcher_counter = 0;
		int computer_counter = 0;

		long start = System.currentTimeMillis();
		while (currIte < endIte) {
			System.out.println("manager said : new iteration " + currIte);
			activeDispatcherWorker();

			System.out.println("manager said : dispatcherWorker actived.. ");
			while (true) {
				s = dispatcherMailbox.get();
				if (s == Signal.DISPATCHER_ITERATION_DISPATCH_OVER)
					dispatcher_counter++;
				// System.out.println("manager said : "+
				// dispatcher_counter+" dipatchers has finished..");
				if (dispatcher_counter == ndispatcher) {
					dispatcher_counter = 0;
					break;
				}
			}
			intervene();
			System.out
					.println("Manager said : successful intervene computer..");

			while (true) {
				s = computerMailbox.get();
				if (s == Signal.COMPUTER_COMPUTE_OVER)
					computer_counter++;
				// System.out.println("manager said : "+ computer_counter
				// +" computers has finished..");
				if (computer_counter == ncomputer) {
					computer_counter = 0;
					break;
				}
			}
			PINGPANG = !PINGPANG;
			currIte++;
		}
		System.out.println("Time :" + (System.currentTimeMillis() - start)
				+ " ms");
		System.exit(0);
	}

	private void intervene() {
		// 给computer发送计算结束消息
		for (int i = 0; i < cws.length; i++) {
			cws[i].putMsg(Signal.MANAGER_ITERATION_COMPUTE_OVER);
		}
	}

	private void activeDispatcherWorker() throws Pausable {
		for (int i = 0; i < dws.length; i++) {
			// System.out.println("send iteration start signal to dispatcher worker "
			// + i + " at iteration " + currIte);
			dws[i].putSignal(Signal.MANAGER_ITERATION_START);
		}
	}

	public void send(int id, byte[] msg) {
		if (id > -1 && id < cws.length) {
			cws[id].putMsg(msg);
		}
	}

	public long index(int to, int type) {

		int sizeOfValue = GlobalVaribaleManager.vConv.sizeOf();
		if (PINGPANG) {
			if (type == 0) {
				return to * sizeOfValue * 2;
			}

			return (sizeOfValue * 2) * to + sizeOfValue;
		} else {
			if (type == 0) {
				return (sizeOfValue * 2) * to + sizeOfValue;
			}
			return to * sizeOfValue * 2;
		}
	}

	public void setUpdate(int to) {
		bits.set(to);
	}

	public void noteCompute(Signal computerComputeOver) throws Pausable {
		computerMailbox.put(computerComputeOver);
	}

	public void noteDispatch(Signal dispatcherIterationDispatchOver)
			throws Pausable {
		dispatcherMailbox.put(dispatcherIterationDispatchOver);
	}

	public void run() throws IOException {
		initWorker();
		start();
	}

}

// [start,end) && [startOffset,endOffset)
class SequenceInterval {
	protected int start;
	protected int end;
	protected int startOffset;
	protected int endOffset;

	public SequenceInterval(int start, int end, int startOffset, int endOffset) {
		super();
		this.start = start;
		this.end = end;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
	}

	@Override
	public String toString() {
		return "SequenceInterval [start=" + start + ", end=" + end
				+ ", startOffset=" + startOffset + ", endOffset=" + endOffset
				+ "]";
	}

}

// [startOffset,endOffset)
class offsetInterval {

}
