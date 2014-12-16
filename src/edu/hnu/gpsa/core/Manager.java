package edu.hnu.gpsa.core;

import edu.hnu.gpsa.datablock.BytesToValueConverter;
import edu.hnu.gpsa.datablock.IntConverter;
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

	Mailbox<Signal> computerMailbox = new Mailbox<Signal>(ncomputer, ncomputer);
	Mailbox<Signal> dispatcherMailbox = new Mailbox<Signal>(ndispatcher, ndispatcher);

	private MapperCore csrMC;

	private MapperCore valMC;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Manager(String graphFilename, BytesToValueConverter vConv,
			BytesToValueConverter eConv, BytesToValueConverter mConv,
			int ndispatcher, int ncomputer, int endIte, Handler handler)
			throws IOException {
		this.graphFilename = graphFilename;
		Manager.ndispatcher = ndispatcher;
		Manager.ncomputer = ncomputer;
		this.cws = new ComputerWorker[ncomputer];
		this.dws = new DispatcherWorker[ndispatcher];
		this.currIte = 0;
		this.endIte = endIte;
		graph = new Graph(graphFilename, "edgelist", eConv, vConv, mConv, null);
		maxid = Graph.MAXID;
		bits = new BitSet(maxid);

		int sizeOfVal = GlobalVaribaleManager.vConv.sizeOf();
		File csrfile = new File(Filename.csrFilename(graphFilename));
		File valfile = new File(Filename.vertexValueFilename(graphFilename));
		valfile.delete();
		valfile.createNewFile();

		csrMC = new MapperCore(csrfile, csrfile.length());
		valMC = new MapperCore(valfile, maxid * 2 * sizeOfVal);

		byte[] valTemp = new byte[sizeOfVal];
		Object val = null;
		byte[] writeTemp = new byte[sizeOfVal * 2];

		valTemp = null;
		for (int i = 0; i < maxid; i++) {
			val = handler.init(i);
			GlobalVaribaleManager.vConv.setValue(valTemp, val);
			System.arraycopy(valTemp, 0, writeTemp, 0, sizeOfVal);
			System.arraycopy(valTemp, 0, writeTemp, sizeOfVal, sizeOfVal);
			valMC.put(i * sizeOfVal * 2, writeTemp);
		}

		GlobalVaribaleManager.init(csrMC, valMC, vConv, eConv, mConv);

	}

	public void initWorker() throws IOException {

		assignDispatchWork(ndispatcher, nedges, csrMC);

		for (int i = 0; i < ncomputer; i++) {
			cws[i] = new ComputerWorker(handler, this);
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

		int left_sequence = 0;
		int right_sequence = 0;
		int left_offset = 0;
		int right_offset = 0;
		int counter = 0;
		int k = 0;

		while (right_offset < mc.getSize()) {
			int to = mc.getInt(right_offset);
			if (to == -1) {
				right_sequence++;
			} else {
				counter++;
			}
			right_offset += 4;
			if (counter == averg_per_dispatcher) {
				sequenceIntervals[k++] = new SequenceInterval(left_sequence,
						right_sequence, left_offset, right_offset);
				counter = 0;
				left_offset = right_offset;
				left_sequence = right_sequence;
				while (mc.getInt(right_offset) == -1) {
					right_sequence++;
					right_offset += 4;
				}
			}
		}

		if (nedges % ndispatcher != 0) {
			sequenceIntervals[k] = new SequenceInterval(left_sequence,
					right_sequence, left_offset, right_offset);
		}

		for (int i = 0; i < ndispatcher; i++) {
			dws[i] = new DispatcherWorker(csrMC, valMC, sequenceIntervals[i],
					handler, this);
			dws[i].start();
		}
	}

	public void execute() throws Pausable {

		Signal s = null;
		int dispatcher_counter = 0;
		int computer_counter = 0;
		
		while (currIte < endIte) {
			activeDispatcherWorker();

			while ((s = dispatcherMailbox.get()) != null) {
				if (s == Signal.DISPATCHER_ITERATION_DISPATCH_OVER)
					dispatcher_counter++;
				if (dispatcher_counter == ndispatcher)
					break;
			}

			intervene();

			while ((s = computerMailbox.get()) != null) {
				if (s == Signal.DISPATCHER_ITERATION_COMPUTE_OVER)
					computer_counter++;
				if (computer_counter == ncomputer)
					break;
			}
			PINGPANG = !PINGPANG;
		}
	}

	private void intervene() {
		// 给computer发送计算结束消息
		for (int i = 0; i < cws.length; i++) {
			cws[i].putMsg(Signal.MANAGER_ITERATION_COMPUTE_OVER);
		}
	}

	private void activeDispatcherWorker() throws Pausable {
		for (int i = 0; i < dws.length; i++) {
			dws[i].putSignal(Signal.MANAGER_ITERATION_START);
		}
	}

	public void send(int id, byte[] msg) {
		cws[id].putMsg(msg);
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
