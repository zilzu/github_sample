package com.kjlee.e2promwrite;


import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {
	
	private static final String TAG = MainActivity.class.getSimpleName();
	
	private NfcAdapter mAdapter;
	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;
	
	private PlaceholderFragment mPlaceholderFrag;
	
	private boolean mRe;
	
	byte[] GetSystemInfoAnswer = null;
	byte[] ReadEHconfigAnswer = null;
	int cpt;
	
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		
		mPlaceholderFrag  = new PlaceholderFragment();

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, mPlaceholderFrag ).commit();
		}
		PackageManager pm = getPackageManager();
    	if(!pm.hasSystemFeature(PackageManager.FEATURE_NFC))
    	{
    		Log.i(TAG,"NFC not available");
    		Toast.makeText(this, "NFC not available", Toast.LENGTH_SHORT).show();
    		finish();
    	}
    	else
    	{
    		mAdapter = NfcAdapter.getDefaultAdapter(this);
    		if(mAdapter.isEnabled())
    		{
    			Log.i(TAG,"NFC is enabled");
    			mPendingIntent = PendingIntent.getActivity(this, 0,new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
		        mFilters = new IntentFilter[] {ndef,};
		        mTechLists = new String[][] { new String[] { android.nfc.tech.NfcV.class.getName() } };
		        onNewIntent(getIntent());
    		}
    		else
    		{
    			Log.i(TAG,"NFC is not enabled");
    			Toast.makeText(this, "NFC is not enabled", Toast.LENGTH_SHORT).show();
    			finish();
    		}
    	}
		Log.i(TAG, "OnCreate");
	}
	
	@Override
	protected void onNewIntent(Intent intent) {		
		super.onNewIntent(intent);		
    	String action = intent.getAction();
    	if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action))    	
    	{
	    	Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	
	    	DataDevice dataDevice = (DataDevice)getApplication();
	    	dataDevice.setCurrentTag(tagFromIntent);

			GetSystemInfoAnswer = NFCCommand.SendGetSystemInfoCommandCustom(tagFromIntent,(DataDevice)getApplication());
			
			String mgs = Helper.ConvertHexByteArrayToString(GetSystemInfoAnswer);
			
			Log.i(TAG, "GetSystemInfo: "+ mgs);

			
			if(DecodeGetSystemInfoResponse(GetSystemInfoAnswer))
	    	{
				//Intent intentScan = new Intent(this, Scan.class);
	        	//startActivity(intentScan);
				Log.i(TAG, "Decode Get System Info!");
				Log.i(TAG, "Manufacturer:" + dataDevice.getManufacturer());
				Log.i(TAG, "Product Name:" + dataDevice.getProductName());
				Log.i(TAG, "UID:" + dataDevice.getUid());
				Log.i(TAG, "AFI:" + dataDevice.getAfi());
				Log.i(TAG, "DSFID:" + dataDevice.getDsfid());
				
				//mPlaceholderFrag.updateOutput("Manufacturer: " + dataDevice.getManufacturer());
				if(!mRe) return;
				mPlaceholderFrag.updateOutput("Product Name: " + dataDevice.getProductName());
	    	}
			else
			{
				return;
			}
			
    	}
	}

	@Override
	protected void onPause() {		
		Log.i(TAG, "OnPause");
		super.onPause();
		mAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onResume() {		
		Log.i(TAG, "OnResume");
		mRe = true;
		super.onResume();
		mPendingIntent = PendingIntent.getActivity(this, 0,new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		if( id == R.id.EhRead)
		{
			if(mPlaceholderFrag != null)
				new StartReadEHconfigTask(mPlaceholderFrag).execute();
		}
		if( id == R.id.BlockRead)
		{
			new StartReadTask().execute();
		}
		return super.onOptionsItemSelected(item);
	}
	
	//***********************************************************************/
	 //* the function Decode the tag answer for the GetSystemInfo command
	 //* the function fills the values (dsfid / afi / memory size / icRef /..) 
	 //* in the myApplication class. return true if everything is ok.
	 //***********************************************************************/
	 public boolean DecodeGetSystemInfoResponse (byte[] GetSystemInfoResponse)
	 {			 
		 //if the tag has returned a god response 
		 if(GetSystemInfoResponse[0] == (byte) 0x00 && GetSystemInfoResponse.length >= 12)
		 { 
			 DataDevice ma = (DataDevice)getApplication();
			 String uidToString = "";
			 byte[] uid = new byte[8];
			 // change uid format from byteArray to a String
			 for (int i = 1; i <= 8; i++) 
			 {
				 uid[i - 1] = GetSystemInfoResponse[10 - i];
				 uidToString += Helper.ConvertHexByteToString(uid[i - 1]);
			 }			 

			 //***** TECHNO ******
			 ma.setUid(uidToString);
			 if(uid[0] == (byte) 0xE0)
			 		 ma.setTechno("ISO 15693");
			 else if (uid[0] == (byte) 0xD0)
			 	 ma.setTechno("ISO 14443");
			 else
			 	 ma.setTechno("Unknown techno");			 
			 			
			 //***** MANUFACTURER ****
			 if(uid[1]== (byte) 0x02)
			 	 ma.setManufacturer("STMicroelectronics");
			 else if(uid[1]== (byte) 0x04)
			 	 ma.setManufacturer("NXP");
			 else if(uid[1]== (byte) 0x07)
			 	 ma.setManufacturer("Texas Instrument");
			 else
			 	 ma.setManufacturer("Unknown manufacturer");						 			
			 			 
			 //**** PRODUCT NAME *****
			 if(uid[2] >= (byte) 0x04 && uid[2] <= (byte) 0x07)
			 {
			 	 ma.setProductName("LRI512");
			 	 ma.setMultipleReadSupported(false);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x14 && uid[2] <= (byte) 0x17)
			 {
			 	 ma.setProductName("LRI64");
			 	 ma.setMultipleReadSupported(false);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x20 && uid[2] <= (byte) 0x23)
			 {
			 	 ma.setProductName("LRI2K");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x28 && uid[2] <= (byte) 0x2B)
			 {
			 	 ma.setProductName("LRIS2K");
			 	 ma.setMultipleReadSupported(false);	
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x2C && uid[2] <= (byte) 0x2F)
			 {
			 	 ma.setProductName("M24LR64");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 }
			 else if(uid[2] >= (byte) 0x40 && uid[2] <= (byte) 0x43)
			 {
			 	 ma.setProductName("LRI1K");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x44 && uid[2] <= (byte) 0x47)
			 {
			 	 ma.setProductName("LRIS64K");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 }
			 else if(uid[2] >= (byte) 0x48 && uid[2] <= (byte) 0x4B)
			 {
			 	 ma.setProductName("M24LR01E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x4C && uid[2] <= (byte) 0x4F)
			 {
			 	 ma.setProductName("M24LR16E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
				 if(ma.isBasedOnTwoBytesAddress() == false)
					return false;
			 }
			 else if(uid[2] >= (byte) 0x50 && uid[2] <= (byte) 0x53)
			 {
			 	 ma.setProductName("M24LR02E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }
			 else if(uid[2] >= (byte) 0x54 && uid[2] <= (byte) 0x57)
			 {
			 	 ma.setProductName("M24LR32E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
				 if(ma.isBasedOnTwoBytesAddress() == false)
				 	return false;			 	 
			 }
			 else if(uid[2] >= (byte) 0x58 && uid[2] <= (byte) 0x5B)
			 {
				 ma.setProductName("M24LR04E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 }
			 else if(uid[2] >= (byte) 0x5C && uid[2] <= (byte) 0x5F)
			 {
			 	 ma.setProductName("M24LR64E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 	 if(ma.isBasedOnTwoBytesAddress() == false)
			 		return false;
			 }
			 else if(uid[2] >= (byte) 0x60 && uid[2] <= (byte) 0x63)
			 {
			 	 ma.setProductName("M24LR08E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 }
			 else if(uid[2] >= (byte) 0x64 && uid[2] <= (byte) 0x67)
			 {
			 	 ma.setProductName("M24LR128E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 	 if(ma.isBasedOnTwoBytesAddress() == false)
				 	return false;			 	 
			 }
			 else if(uid[2] >= (byte) 0x6C && uid[2] <= (byte) 0x6F)
			 {
			 	 ma.setProductName("M24LR256E");
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
				 if(ma.isBasedOnTwoBytesAddress() == false)
				 	return false;			 	 
			 }
			 else if(uid[2] >= (byte) 0xF8 && uid[2] <= (byte) 0xFB)
			 {
			 	 ma.setProductName("detected product");
			 	 ma.setBasedOnTwoBytesAddress(true);
			 	 ma.setMultipleReadSupported(true);
			 	 ma.setMemoryExceed2048bytesSize(true);
			 }	 
			 else
			 {
			 	 ma.setProductName("Unknown product");
			 	 ma.setBasedOnTwoBytesAddress(false);
			 	 ma.setMultipleReadSupported(false);
			 	 ma.setMemoryExceed2048bytesSize(false);
			 }		
			 
			 //*** DSFID ***
			 ma.setDsfid(Helper.ConvertHexByteToString(GetSystemInfoResponse[10]));
			 
			//*** AFI ***
			 ma.setAfi(Helper.ConvertHexByteToString(GetSystemInfoResponse[11]));			 
			 
			//*** MEMORY SIZE ***
			 if(ma.isBasedOnTwoBytesAddress())
			 {
				 String temp = new String();
				 temp += Helper.ConvertHexByteToString(GetSystemInfoResponse[13]);
				 temp += Helper.ConvertHexByteToString(GetSystemInfoResponse[12]);
				 ma.setMemorySize(temp);
			 }
			 else 
				 ma.setMemorySize(Helper.ConvertHexByteToString(GetSystemInfoResponse[12]));
			 
			//*** BLOCK SIZE ***
			 if(ma.isBasedOnTwoBytesAddress())
				 ma.setBlockSize(Helper.ConvertHexByteToString(GetSystemInfoResponse[14]));
			 else
				 ma.setBlockSize(Helper.ConvertHexByteToString(GetSystemInfoResponse[13]));

			//*** IC REFERENCE ***
			 if(ma.isBasedOnTwoBytesAddress())
				 ma.setIcReference(Helper.ConvertHexByteToString(GetSystemInfoResponse[15]));
			 else
				 ma.setIcReference(Helper.ConvertHexByteToString(GetSystemInfoResponse[14]));
				 
			 return true;
		 }
		 
		//if the tag has returned an error code 
		 else
			 return false;
	 }

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {
		
		private	TextView	mDebugTV;

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);	
			mDebugTV = (TextView)rootView.findViewById(R.id.textView1);
			mDebugTV.setText("");
			return rootView;
		}
		
		public void updateOutput(String msg){
			mDebugTV.setText(mDebugTV.getText() + "\n\n" + msg);
		}
	}
	
	private class StartReadEHconfigTask extends AsyncTask<Void, Void, Void> 
	{
		PlaceholderFragment mFrag;
		public StartReadEHconfigTask(Fragment frag)
		{
			mFrag = (PlaceholderFragment)frag;
		}
		// can use UI thread here
		protected void onPreExecute() 
		{
			//this.dialog.setMessage("Please, place your phone near the card");
			//this.dialog.show();

			DataDevice dataDevice = (DataDevice)getApplication();
			
	    	GetSystemInfoAnswer = NFCCommand.SendGetSystemInfoCommandCustom(dataDevice.getCurrentTag(),dataDevice);
			  
		}
		
		// automatically done on worker thread (separate from UI thread)
		@Override
		protected Void doInBackground(Void... params)
		{
			// TODO Auto-generated method stub
			cpt = 0;
			DataDevice dataDevice = (DataDevice)getApplication();
			
			ReadEHconfigAnswer = null;
			if(DecodeGetSystemInfoResponse(GetSystemInfoAnswer))
	    	{
				while ((ReadEHconfigAnswer == null || ReadEHconfigAnswer[0] == 1) && cpt <= 10)
				{
					ReadEHconfigAnswer = NFCCommand.SendReadEHconfigCommand(dataDevice.getCurrentTag(), dataDevice);
					cpt++;
				}
	    	}
			return null;
		}
		
		// can use UI thread here
		protected void onPostExecute(final Void unused)
		{
			//if (this.dialog.isShowing())
			//	this.dialog.dismiss();
    		
			if (ReadEHconfigAnswer==null)
			{
				//String valueByte = "";
				//valueEHconfigByte.setText(valueByte);
				Toast.makeText(getApplicationContext(), "ERROR Read EH CONFIG byte (No tag answer) ", Toast.LENGTH_SHORT).show();
			}
			else if(ReadEHconfigAnswer[0]==(byte)0x01)
    		{
				//String valueByte = "";
				//valueEHconfigByte.setText(valueByte);
				Toast.makeText(getApplicationContext(), "ERROR Read EH CONFIG byte ", Toast.LENGTH_SHORT).show();
    		}
    		else if(ReadEHconfigAnswer[0]==(byte)0xFF)
    		{
    			//String valueByte = "";
				//valueEHconfigByte.setText(valueByte);
				Toast.makeText(getApplicationContext(), "ERROR Read EH CONFIG byte ", Toast.LENGTH_SHORT).show();
    		}    		
    		else if(ReadEHconfigAnswer[0]==(byte)0x00)
    		{
    			//valueEHconfigByte.setText(Helper.ConvertHexByteToString(ReadEHconfigAnswer[1]).toUpperCase());
    			mFrag.updateOutput("EH :"+Helper.ConvertHexByteToString(ReadEHconfigAnswer[1]).toUpperCase());
    			Toast.makeText(getApplicationContext(), "Read EH CONFIG byte Sucessfull ", Toast.LENGTH_SHORT).show();
    			//finish();
    		}
    		else
    		{
    			//String valueByte = "";
				//valueEHconfigByte.setText(valueByte);
				Toast.makeText(getApplicationContext(), "Read EH CONFIG byte ERROR ", Toast.LENGTH_SHORT).show();
    		}    		
    		
		}
	}
	
	private class StartReadTask extends AsyncTask<Void, Void, Void> {
	      //private final ProgressDialog dialog = new ProgressDialog(ScanRead.this);
	      // can use UI thread here
		  String sNbOfBlock;
		  String startAddressString;
		  byte[] numberOfBlockToRead;
		  byte[] ReadMultipleBlockAnswer;
		  byte[] addressStart;
		  String[] catBlocks = null;
		  String[] catValueBlocks = null;
	      protected void onPreExecute() 
	      {
		
	    	  //Used for DEBUG : Log.i("ScanRead", "Button Read CLICKED **** On Pre Execute ");
	    	  DataDevice dataDevice = (DataDevice)getApplication();
	    	  
	    	  GetSystemInfoAnswer = NFCCommand.SendGetSystemInfoCommandCustom(dataDevice.getCurrentTag(),dataDevice);
			  
	    	  if(DecodeGetSystemInfoResponse(GetSystemInfoAnswer))
			  {		
/*
		    	  startAddressString = etFrom.getText().toString(); 
		    	  startAddressString = Helper.castHexKeyboard(startAddressString);
				  startAddressString = Helper.FormatStringAddressStart(startAddressString, dataDevice);
				  addressStart = Helper.ConvertStringToHexBytes(startAddressString);
				  etFrom.setText(startAddressString);
		    	  				
				  if (etTo.length() == 0)
						etTo.setText("0001");
				  if (Integer.parseInt(etTo.getText().toString()) == 0)
						etTo.setText("0001");

				  */
					
				  sNbOfBlock = "0058"; // 88
				  startAddressString ="0020";
				  addressStart = Helper.ConvertStringToHexBytes(startAddressString);
				  //Used for DEBUG : Log.i("ScanRead", "sNbOfBlock 1 -----> " + sNbOfBlock);
				  //sNbOfBlock = Helper.castHexKeyboard(sNbOfBlock);
				  //Log.i("ScanRead", "sNbOfBlock 2 -----> " + sNbOfBlock);
			  	  //sNbOfBlock = Helper.FormatStringNbBlock(sNbOfBlock, startAddressString ,dataDevice);
			  	  //Log.i("ScanRead", "sNbOfBlock 3 -----> " + sNbOfBlock);				  
				  sNbOfBlock = Helper.FormatStringNbBlockInteger(sNbOfBlock, startAddressString ,dataDevice);
				  //Used for DEBUG : Log.i("ScanRead", "sNbOfBlock 3 -----> " + sNbOfBlock);
				  
				  //numberOfBlockToRead = Helper.ConvertStringToHexBytes(sNbOfBlock);
				  numberOfBlockToRead = Helper.ConvertIntTo2bytesHexaFormat(Integer.parseInt(sNbOfBlock));
				  //Used for DEBUG : Log.i("ScanRead", "numberOfBlockToRead -----> " + Helper.ConvertHexByteArrayToString(numberOfBlockToRead));
				  //etTo.setText(sNbOfBlock);
				  //Used for DEBUG : Log.i("ScanRead", "sNbOfBlock 5 -----> " + sNbOfBlock);
					
		         //this.dialog.setMessage("Please, keep your phone close to the tag");
		         //this.dialog.show();
			  }
	    	  else
	    	  {
	    		  //this.dialog.setMessage("Please, No tag detected");
			      //this.dialog.show();
			      //Used for DEBUG : Log.i("ScanRead", "NON -----> " + sNbOfBlock);
	    	  }
		         
	      }
	      
	      // automatically done on worker thread (separate from UI thread)
	      @Override
	      protected Void doInBackground(Void... params)
			{
	    	  DataDevice dataDevice = (DataDevice)getApplication();
	    	  DataDevice ma = (DataDevice)getApplication();			  			
			  
	    	  ReadMultipleBlockAnswer = null;
	    	  cpt = 0; 
	    	  
	    	  if(DecodeGetSystemInfoResponse(GetSystemInfoAnswer))
			  {	 					
					
					//if(Helper.Convert2bytesHexaFormatToInt(numberOfBlockToRead) <=1) //ex: 1 byte to be read
					//{
					//	while ((ReadMultipleBlockAnswer == null || ReadMultipleBlockAnswer[0] == 1) && cpt <= 10 )
					//	{
					//		Log.i("ScanRead", "Dans le read SINGLE le cpt est �-----> " + String.valueOf(cpt));
					//		ReadMultipleBlockAnswer = NFCCommand.SendReadSingleBlockCommand(dataDevice.getCurrentTag(),addressStart, dataDevice);
					//		cpt ++;
					//	}
					//	cpt = 0;
					//}
					//else if(ma.isMultipleReadSupported() == false) //ex: LRIS2K
					if(ma.isMultipleReadSupported() == false || Helper.Convert2bytesHexaFormatToInt(numberOfBlockToRead) <=1) //ex: LRIS2K
					{
						while((ReadMultipleBlockAnswer == null || ReadMultipleBlockAnswer[0] == 1) && cpt <= 10)
						{
							//Used for DEBUG : Log.i("ScanRead", "Dans le several read single block le cpt est �-----> " + String.valueOf(cpt));
							ReadMultipleBlockAnswer = NFCCommand.Send_several_ReadSingleBlockCommands_NbBlocks(dataDevice.getCurrentTag(),addressStart,numberOfBlockToRead, dataDevice);
							cpt ++;
						}
						cpt = 0;
					}
					else if(Helper.Convert2bytesHexaFormatToInt(numberOfBlockToRead) <32)
					{
						while((ReadMultipleBlockAnswer == null || ReadMultipleBlockAnswer[0] == 1) && cpt <= 10)
						{
							//Used for DEBUG : Log.i("ScanRead", "Dan le read MULTIPLE 1 le cpt est �-----> " + String.valueOf(cpt));
							ReadMultipleBlockAnswer = NFCCommand.SendReadMultipleBlockCommandCustom(dataDevice.getCurrentTag(),addressStart,numberOfBlockToRead[1], dataDevice);
							cpt ++;
						}
						cpt = 0;
					}
					else
					{
						while ((ReadMultipleBlockAnswer == null || ReadMultipleBlockAnswer[0] == 1) && cpt <= 10)
						{
							//Used for DEBUG : Log.i("ScanRead", "Dans le read MULTIPLE 2 le cpt est �-----> " + String.valueOf(cpt));
							ReadMultipleBlockAnswer = NFCCommand.SendReadMultipleBlockCommandCustom2(dataDevice.getCurrentTag(),addressStart,numberOfBlockToRead, dataDevice);
							cpt ++;
						}
						cpt = 0;
					}
					
			  }
			  return null;
			}
	      
	      // can use UI thread here
	      protected void onPostExecute(final Void unused)
	      {
	    	  	    
	    	  Log.i("ScanRead", "Button Read CLICKED **** On Post Execute ");
		      //if (this.dialog.isShowing())
		      //    this.dialog.dismiss();
		    
		      if(DecodeGetSystemInfoResponse(GetSystemInfoAnswer))
			  {
			     	//nbblocks = Helper.ConvertStringToInt(sNbOfBlock);
		    	    int nbblocks = Integer.parseInt(sNbOfBlock);
		    	  
					if (ReadMultipleBlockAnswer != null && ReadMultipleBlockAnswer.length - 1 > 0)
					{				
						if (ReadMultipleBlockAnswer[0] == 0x00)
						{
							catBlocks = Helper.buildArrayBlocks(addressStart, nbblocks);
							catValueBlocks = Helper.buildArrayValueBlocks(ReadMultipleBlockAnswer, nbblocks);
							
							//listOfData = new ArrayList<DataRead>();
							for (int i = 0; i < nbblocks; i++)
							{
								//listOfData.add(new DataRead(catBlocks[i], catValueBlocks[i]));
								Log.i(TAG, i+":" + catBlocks[i]+" "+catValueBlocks[i]);
							}
							//DataReadAdapter adapter = new DataReadAdapter(getApplicationContext(), listOfData);
							//list.setAdapter(adapter);
						}
						else // added to erase screen in case of read fail
						{
							//listOfData.clear();
							//DataReadAdapter adapter = new DataReadAdapter(getApplicationContext(), null);
							//list.setAdapter(null);	
							Toast.makeText(getApplicationContext(), "ERROR Read ", Toast.LENGTH_SHORT).show();
						}
					}
					else	// added to erase screen in case of read fail
					{
						//listOfData.clear();
						//DataReadAdapter adapter = new DataReadAdapter(getApplicationContext(), listOfData);
						//list.setAdapter(adapter);							
						//list.setAdapter(null);	
						Toast.makeText(getApplicationContext(), "ERROR Read (no Tag answer) ", Toast.LENGTH_SHORT).show();
					}
			  }
		      else
		      {
					//listOfData.clear();
					//DataReadAdapter adapter = new DataReadAdapter(getApplicationContext(), listOfData);
					//list.setAdapter(adapter);							
					//list.setAdapter(null);	
					Toast.makeText(getApplicationContext(), "ERROR Read (no Tag answer) ", Toast.LENGTH_SHORT).show();		    	  
		      }
	      } 
	   }


}
