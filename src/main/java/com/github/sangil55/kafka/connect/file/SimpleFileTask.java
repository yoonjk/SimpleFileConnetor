package com.github.sangil55.kafka.connect.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaFileObject;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.file.FileStreamSourceConnector;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.apache.commons.io.input.CountingInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleFileTask extends SourceTask {

	private static final Logger log = LoggerFactory.getLogger(SimpleFileTask.class);
	public String version() {
		// TODO Auto-generated method stub
		return "1.0";
	}

	
	 private String filename;
	 private String pathname="/data01/m_input";
	 private int BUFFER_SIZE = 100000;
	 private String offsetpath="/tmp/";
	 String offsetFileName = "kafka_csi_offset.csv";
	 
	  private InputStream stream;
	  private String topic;
	  private MetaData metadata = null;
	  private long lasttime = 0;
	  private final Object syncObj1 = new Object();
	  public void start(Map<String, String> props) {
	    filename = justGetOrAddSlash(props.get(SimpleFileConnector.FILE_CONFIG));
	    pathname = justGetOrAddSlash(props.get(SimpleFileConnector.FILE_CONFIG));
	    //default filename = pathname > spool all file in the just right directory
	    // ex  /root/1.csv , /root/2.csv >> just set filename as /root/
	    topic = props.get(SimpleFileConnector.TOPIC_CONFIG);
	    if(props.get(SimpleFileConnector.BUFFERSIZE_CONFIG) != null)
	    	BUFFER_SIZE = Integer.parseInt(props.get(SimpleFileConnector.BUFFERSIZE_CONFIG));
	    if(props.get(SimpleFileConnector.OFFSETPATH_CONFIG) != null)
	    	offsetpath = justGetOrAddSlash(props.get(SimpleFileConnector.OFFSETPATH_CONFIG));
	    log.info("[INFO] Kafka Connector Task start ");
	    log.info("[INFO] Kafka Connector start Option > file path = " + pathname + ",topic = " + topic + ",buffersize = " + BUFFER_SIZE + ",offsetpath = " + offsetpath);
	    
	    metadata = new MetaData();	
	  }
	  
	
	 public boolean isFinished(String str)
	 {
		 File file = new File(str);
		 long size = file.length();
		 
		 Long[] offset_full = metadata.offsetmap.get(file.getName());
//		 log.info("exact file size : " + size +" ,offset file size : "+offset_full[1] + " , offset = " + offset_full[0]);
		 
		 //size != pre file size    >> that mean file has changed
		 
		 if( !offset_full[1].equals(size) )
		 {
		 	 return false;
		 }
		 // offset != filesize  >> not finished or file has changed
		 if(!offset_full[1].equals(offset_full[0]))
		 {
		   	return false;
		 }
		 return true;
	 }
	
	 public String justGetOrAddSlash(String str)
	 {
		 if(str.charAt( str.length()-1 ) != '/')
			 str = str + '/';
		 return str;
	 }

	@Override
	public List<SourceRecord> poll() throws InterruptedException {
		synchronized (this)
			{
		//	Thread.sleep(100);	
		
			// TODO Auto-generated method stub
			//log.info("polling-csi");
			
			File dirFile=new File(pathname);
			
			File []fileList=dirFile.listFiles();
			File metafile = new File(offsetpath+offsetFileName);
		    
	        final List<SourceRecord> results = new ArrayList<>();
	
			if(fileList==null)
				return null;
			if( metafile.exists() )
			{
				if(metadata == null)
					metadata = new MetaData();	
				metadata.ReadMetaFile(offsetpath+offsetFileName);
				//log.info("[INFO] meta file read done");
			}
			else
			{
				metadata = new MetaData();			
			}
			for(int i = 0; i<fileList.length; i++)
			{
				//log.info("FILE REAED START WITH : " + pathname + fileList[i].getName() + "TOTAL FILE Counts");
				if(fileList[i].isDirectory())
					continue;
				String filestr = pathname + fileList[i].getName();
				if(metadata.offsetmap.get(fileList[i].getName()) == null)
				{
					//log.info("!no offset data ");
					// new metadata should be created
					long filelen = fileList[i].length();					
					long offset = 0;
					CountingInputStream cin = null;
				
					try {
						cin = new CountingInputStream(new FileInputStream(filestr));
						int s;
						byte[] b = new byte[BUFFER_SIZE];
							cin.skip(offset);
							if ((s = cin.read(b, 0, BUFFER_SIZE)) != -1) {
								String newstr = new String(b, "UTF-8");
						//        log.info("num of data bytes : " + s + "   ||  data : " /*+newstr */);
						        Map sourcePartition = Collections.singletonMap("filename", filestr);
						        offset += s;
						        
						        Map sourceOffset = Collections.singletonMap("position", offset);
						        results.add(new SourceRecord(sourcePartition, sourceOffset, topic, Schema.STRING_SCHEMA, newstr));						      
							 }
							Long[]ll = new Long[2];
							ll[0] = offset; ll[1] = filelen;						
							metadata.offsetmap.put(fileList[i].getName(),  ll);
							
						    metadata.saveoffset(offsetpath);
						      
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					finally{
						if(cin!=null)
							try {
								cin.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
					}
					
					
				}
				else
				{
					if(isFinished(filestr) == true)
					{
						continue;						
					}
					else
					{
						//log.info("!not finished with offset, Read start ");
						// start to read by 1000 rows
						long filelen = fileList[i].length();					
						long offset = 0;
						CountingInputStream cin = null;
						if( metadata.offsetmap.get(fileList[i].getName()) == null)
							offset = 0;
						else
							offset = metadata.offsetmap.get(fileList[i].getName())[0];
						try {
							cin = new CountingInputStream(new FileInputStream(filestr));
							int s;
							byte[] b = new byte[BUFFER_SIZE];
								cin.skip(offset);
								if ((s = cin.read(b, 0, BUFFER_SIZE)) != -1) {
									String newstr = new String(b, "UTF-8");
							//        log.info("num of data bytes : " + s + "   ||  data : "/* +newstr */);
							        Map sourcePartition = Collections.singletonMap("filename", filestr);
							        offset += s;
							        
							        Map sourceOffset = Collections.singletonMap("position", offset);
							        results.add(new SourceRecord(sourcePartition, sourceOffset, topic, Schema.STRING_SCHEMA, newstr));						      
								 }
								Long[]ll = new Long[2];
								ll[0] = offset; ll[1] = filelen;						
								metadata.offsetmap.put(fileList[i].getName(), ll);
								
								metadata.saveoffset("/tmp/");
							      
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						finally{
							if(cin!=null)
								try {
									cin.close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
						}
						
					}
					
					
				}
				
			}
			
			return results;
		}		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}	

}