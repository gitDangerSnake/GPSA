package edu.hnu.gpsa.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;

import edu.hnu.gpsa.datablock.BytesToValueConverter;
import edu.hnu.gpsa.datablock.IntConverter;
import edu.hnu.gpsa.graph.MapperCore;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class ComputerWorker extends Task{
	private static int counter = 0;
	private int cwid; 
	private Handler handler;
	private Manager mgr;
	private BitSet fisrtMsg  ;
	private int nv;

	
	Mailbox<Object> messages = new Mailbox<Object>(10000);
	public ComputerWorker(Handler handler, int nv,Manager mgr){
		cwid = counter++;
	
		this.handler = handler;
		this.nv = nv;
		fisrtMsg = new BitSet(nv);
		this.mgr = mgr;
		
		
	}
	
	public void execute() throws Pausable, IOException{
		
		byte[] msg = null;
		Object o = messages.get();
		int sizeOfm = GlobalVaribaleManager.mConv.sizeOf();
		int sizeOfv= GlobalVaribaleManager.vConv.sizeOf();
		Object mVal = null;
		Object val = handler.init(0);
		Object newVal = null;
		long offset = 0;
		int translateId = 0;
		
		while(o != Signal.SYSTEM_OVER){
			if(o instanceof byte[]){
				msg = (byte[])o;
				int loop = (msg.length - sizeOfm)/4;
				mVal =  GlobalVaribaleManager.mConv.getValue(msg);
				for(int i=0;i<loop;i++){
					int to =  GlobalVaribaleManager.iConv.getValue(msg, i*4+sizeOfm, 4);
					translateId = translate(to);
					if(fisrtMsg.get(translateId)){
						offset = index(to,1);
					}else{
						offset = index(to,0);
						fisrtMsg.set(translateId);
					}
					//获取顶点to的value
					if (val instanceof Long) {
						val = GlobalVaribaleManager.valMC.getLong(offset);
					} else if (val instanceof Double) {
						val = GlobalVaribaleManager.valMC.getDouble(offset);
					} else if (val instanceof Integer) {
//						if(offset == 7331420)
//	System.out.println("get " + to +" offset is" + offset+" worker id is" + cwid);
						val = GlobalVaribaleManager.valMC.getInt(offset);
					} else if (val instanceof Float) {
						val = GlobalVaribaleManager.valMC.getFloat(offset);
					} else {
						byte[] data = GlobalVaribaleManager.valMC.get(offset, sizeOfv);
						val = GlobalVaribaleManager.vConv.getValue(data);
					}
					
					//然后对vlaue和m进行计算
					newVal = handler.compute(val, mVal);
					
					//写入value
					if(handler.isUpdated(val, newVal)){
						if (val instanceof Long) {
							GlobalVaribaleManager.valMC.putLong(offset,(long)newVal);
						} else if (val instanceof Double) {
							GlobalVaribaleManager.valMC.putDouble(offset,(double)newVal);
						} else if (val instanceof Integer) {
//						System.out.println("newVal is" + newVal +" and oldVal is" + val+" and mVal is" + mVal+"worker " + cwid+ " is about to update the value to buffer file");
							GlobalVaribaleManager.valMC.putInt(offset,(int)newVal);
						} else if (val instanceof Float) {
//						System.out.println("newVal is" + newVal +" and oldVal is" + val+" and mVal is" + mVal+"worker " + cwid+ " is about to update the value to buffer file");
							GlobalVaribaleManager.valMC.putFloat(offset,(float)newVal);
						} else {
							byte[] data = GlobalVaribaleManager.valMC.get(offset, sizeOfv);
							val = GlobalVaribaleManager.vConv.getValue(data);
						}
						mgr.setUpdate(to);
					}else{
						fisrtMsg.clear(translateId);
					}
				}
			}else if(o instanceof Signal){
				if(o == Signal.MANAGER_ITERATION_COMPUTE_OVER){
					//通知manager该compute worker上的计算操作已经完成
					mgr.noteCompute(Signal.COMPUTER_COMPUTE_OVER);
					fisrtMsg.clear();
				}
			}
			o = messages.get();
		}
	}

	private int translate(int to) {
		return to % nv;
	}

	public void putMsg(Object msg) {
		messages.putnb(msg);
	}
	
	public long index(int sequence,int type){
		return mgr.index(sequence,type);
	}

}
