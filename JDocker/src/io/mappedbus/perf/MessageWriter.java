package io.mappedbus.perf;
import io.mappedbus.MappedBusWriter;

import java.io.File;

public class MessageWriter {

	public static void main(String[] args) {
		final MessageWriter writer = new MessageWriter();
		for(int i = 0; i < 1; i++){
			final int count = i;
			new Thread(new Runnable(){
				@Override
				public void run() {
					writer.run("/tmp/test" + count);
				}}).start();
		}
	}

	public void run(String fileName) {
		try {
			new File(fileName).delete();
			System.out.println("fileName " + fileName);
			MappedBusWriter writer = new MappedBusWriter(fileName, 20000000000L, 12, false);
			writer.open();
			
			PriceUpdate priceUpdate = new PriceUpdate();
			
			for (int i = 0; i < 80000000; i++) {
				writer.write(priceUpdate);
			}
			
			System.out.println("Done");
			writer.close();
			System.out.println("Closed");
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}