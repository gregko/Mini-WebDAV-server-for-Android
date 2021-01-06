package com.hyperionics.wdserverlib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

// local service program reference
// http://developer.android.com/reference/android/app/Service.html#LocalServiceSample
// http://www.techotopia.com/index.php/Android_Local_Bound_Services_%E2%80%93_A_Worked_Example

public class HttpService extends Service
{
	public static final String CHANNEL_ID = "WebDAVServiceChannel";
	private WifiManager.WifiLock mWifiLock = null;
	private PowerManager.WakeLock mWakeLock = null;
	private final IBinder mBinder = new LocalBinder();
	public boolean mDone = false;

	public class LocalBinder extends Binder {
		HttpService getService()
		{
			return HttpService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d("wdSrv", "service bind");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d("wdSrv", "service onUnbind");
		return super.onUnbind(intent);
	}

	@Override
	public void onCreate()
	{
		Log.d("wdSrv", "service creat");
	}

	@Override
	public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
		Log.d("wdSrv", "service start  - http server start");
		boolean wholeStorage = getSharedPreferences("WebDav", MODE_PRIVATE).getBoolean("WholeStorage", false);
		File wwwRoot = new File(wholeStorage ? "/storage" : getApplicationContext().getExternalFilesDir(null).getAbsolutePath());
		rootDir = wwwRoot.getAbsolutePath();

		// ref http://stackoverflow.com/questions/8897535/android-socket-gets-killed-imidiatelly-after-screen-goes-blank/18916511#18916511
		WifiManager wMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (mWifiLock == null) {
			mWifiLock = wMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "com.hyperionics.webdavserver:MyWifiLock");
			mWifiLock.setReferenceCounted(false);
		}

		PowerManager pMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		if (mWakeLock == null)
			mWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.hyperionics.webdavserver:MyWakeLock");
		if (mWakeLock != null && !mWakeLock.isHeld())
			mWakeLock.acquire();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel serviceChannel = new NotificationChannel(
					CHANNEL_ID,
					"WebDAV Server",
					NotificationManager.IMPORTANCE_LOW
			);
			serviceChannel.setSound(null, null);
			serviceChannel.setShowBadge(false);
			NotificationManager manager = getSystemService(NotificationManager.class);
			manager.createNotificationChannel(serviceChannel);
		}
		Intent notificationIntent = new Intent(this, ServerSettingsActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this,
				0, notificationIntent, 0);
		Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.wds_app_name))
				.setContentText(getString(R.string.srv_running))
				.setSmallIcon(R.drawable.ic_clip)
				.setContentIntent(pendingIntent)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setCategory(NotificationCompat.CATEGORY_SERVICE)
				.setOngoing(true)
				.build();
		startForeground(1, notification);

		(new connect_clinet()).start(); // Start http service
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d("wdSrv", "service destroy");
		mDone = true;

		// Close the server service
		try
		{
			if (mServer != null)
				mServer.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
	}

	private ServerSocket mServer = null;

	private String rootDir = "";

	class connect_clinet extends Thread {
		public int mUsePort = getSharedPreferences("WebDav", MODE_PRIVATE).getInt("port", 8080);

		public void run() {
			if (mWifiLock != null && !mWifiLock.isHeld()) {
				mWifiLock.acquire();
			}

			Socket connected;

			try
			{
				mServer = new ServerSocket();
			}
			catch (IOException e1)
			{
				Log.e("wdSrv", e1.toString());
			}

			try
			{
				mServer.setReuseAddress(true);
			}
			catch (SocketException e1)
			{
				Log.e("wdSrv", e1.toString());
			}

			try
			{
				mServer.bind(new InetSocketAddress(mUsePort));
			}
			catch (IOException e1)
			{
				Log.e("wdSrv", e1.toString());
			}

			while (!mDone)
			{
				try
				{
					connected = mServer.accept(); // waits for connection
					(new clinet_deal(connected)).start();
				}
				catch (IOException e)
				{
					Log.e("wdSrv", e.toString());
					e.printStackTrace();
				}
			}
			if (mWifiLock != null && mWifiLock.isHeld()) {
				mWifiLock.release(); // this takes about 4 seconds on Android 11, run in background thread
			}

			Log.d("wdSrv", "http server close");
		}
	}

	class clinet_deal extends Thread {
		public Socket connectedClient = null;
		public String headersString = "";
		public String http_ver = "HTTP/1.1";
		public byte[] body;

		public void run()
		{
			String requestHeaderStr = null;
			String requestStr = null;

			try
			{
				File targetFile;
				InputStream inputStream = connectedClient.getInputStream();

				// Data receiving area start
				// Hyperionics: Not a good idea to read all of the input into memory - if uploading a file,
				// it could be huge. Modify the original baxermux code to read only the headers, and
				// read the rest of the input later as needed.
				{
					ByteArrayOutputStream bStream = new ByteArrayOutputStream();
					int b;
					boolean nl = false;
					while ((b = inputStream.read()) > -1) {
						bStream.write(b);
						if (b == '\n') {
							if (nl)
								break; // empty line detected, stop reading header
							nl = true;
						}
						else if (b != '\r')
							nl = false;
					}
					requestStr = new String(bStream.toByteArray(), "UTF-8");
					Log.d("wdSrv", "requestStr length: " + requestStr.length());
					Log.d("wdSrv", "requestStr:\n" + requestStr);
				}

				// header analysis area start
				String firstline = "";
				try
				{
					firstline = requestStr.substring(0, requestStr.indexOf("\r\n"));
					Log.d("wdSrv", "firstLine: [" + firstline + "]");
				}
				catch (Exception ex)
				{
					Log.e("wdSrv", "Exceptions in requestStr.substring(): ", ex);
					ex.printStackTrace();
					connectedClient.close();
					return;
				}

				String[] requestInf = firstline.split(" ");
				String requestMethod = requestInf[0];
				// http://www.ewdna.com/2008/11/urlencoder.html
				String requestTarget = URLDecoder.decode(requestInf[1], "UTF-8");

				requestHeaderStr = requestStr.substring(firstline.length() + 2);
				HashMap<String, String> headerList = new HashMap();
				for (String i : requestHeaderStr.split("\r\n"))
				{
					Log.d("wdSrv", "h : [" + i + "]");
					headerList.put(i.substring(0, i.indexOf(": ")), i.substring(i.indexOf(": ") + 2));
				}

				if (requestMethod.equals("PUT")) {
					targetFile = new File(rootDir + requestTarget);
					if (!targetFile.getParentFile().canWrite()) {
						// Read only directory, return 403 Forbidden status code
						headersString = http_ver + " 403 Forbidden\r\n\r\n";
						connectedClient.getOutputStream().write(headersString.getBytes());
						connectedClient.close();
						return;
					}
					if (targetFile.exists())
						targetFile.delete();

					try (FileOutputStream output = new FileOutputStream(targetFile))
					{
						byte[] recBuffer = new byte[1024];
						int readCount;
						long bytesToRead = recBuffer.length;
						int available;
						while ((available = inputStream.available()) > 0) {
							if (bytesToRead > available)
								bytesToRead = available;
							readCount = inputStream.read(recBuffer, 0, (int)bytesToRead);
							output.write(recBuffer, 0, readCount);
							available = inputStream.available();
							bytesToRead = available;
							if (bytesToRead > recBuffer.length)
								bytesToRead = recBuffer.length;
							if (available == 0) {
								try {
									Thread.sleep(200);
								} catch (InterruptedException ignore) {}
							}
						}
						headersString = http_ver + " 201 Created\r\n";
						headersString += "Content-Location: " + requestTarget + "\r\n";
						headersString += "\r\n";
					}
					catch (Exception e)
					{
						if (targetFile.exists())
							targetFile.delete();
						headersString = http_ver + " " + "403 Forbidden" + "\r\n";
						headersString += "\r\n";
					}
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				if (requestMethod.equals("PROPPATCH")) {
					// WebDAV client is sending a patch command to fix file creation and modification
					// date/time.
					targetFile = new File(rootDir + requestTarget);
					ByteArrayOutputStream bStream = new ByteArrayOutputStream();
					int b, nBytes = 0;
					int contentLength;
					try {
						contentLength = Integer.parseInt(headerList.get("Content-Length"));
					} catch (NumberFormatException nfe) {
						contentLength = 0;
					}
					while ((b = inputStream.read()) > -1) {
						bStream.write(b);
						if (++nBytes >= contentLength)
							break;
					}
					String patchStr = new String(bStream.toByteArray(), "UTF-8");
					Log.d("wdSrv", "patchStr:\n" + patchStr);
					/*
					Sample patchStr:
					<?xml version="1.0" encoding="utf-8" ?><D:propertyupdate xmlns:D="DAV:" xmlns:srtns="http://www.southrivertech.com/"><D:set><D:prop><srtns:srt_modifiedtime>2019-06-04T15:42:53Z</srtns:srt_modifiedtime><srtns:srt_creationtime>2021-01-02T22:33:15Z</srtns:srt_creationtime><srtns:srt_proptimestamp>2021-01-02T17:33:15Z</srtns:srt_proptimestamp></D:prop></D:set></D:propertyupdate>
					 */
					int n1 = patchStr.indexOf("<srtns:srt_modifiedtime>");
					if (n1 > 0) try {
						String modTimeStr = patchStr.substring(n1 + 24);
						n1 = modTimeStr.indexOf("</");
						if (n1 > 0) {
							modTimeStr = modTimeStr.substring(0, n1);
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
							long ms = sdf.parse(modTimeStr).getTime();
							targetFile.setLastModified(ms);
						}
					} catch (Exception ex) {
						Log.e("wdSrv", "Exception in set mod time: " + ex);
						ex.printStackTrace();
					}
					headersString = http_ver + " 200 OK\r\n";
					headersString += "\r\n";
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				if (requestMethod.equals("COPY")) {
					String des = (String) headerList.get("Destination");
					String from = rootDir + requestTarget;
					String to = rootDir + URLDecoder.decode(des.substring(("http://" + (String) headerList.get("Host")).length()), "UTF-8");

					targetFile = new File(from);
					File to_file = new File(to);
					File dir = to_file.isDirectory() ? to_file : to_file.getParentFile();
					if (!dir.canWrite()) {
						headersString = http_ver + " 403 Forbidden\r\n\r\n";
						connectedClient.getOutputStream().write(headersString.getBytes());
						connectedClient.close();
						return;
					}

					if (targetFile.isDirectory())
					{
						Utils.copyDirectory(targetFile, to_file);
					}
					else
					{
						Utils.copyFile(targetFile.getAbsolutePath(), to_file.getAbsolutePath());
					}

					headersString = http_ver + " 201 Created\r\n\r\n";
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				if (requestMethod.equals("MOVE")) {
					String des = (String) headerList.get("Destination");
					String from = rootDir + requestTarget;
					String to = rootDir + URLDecoder.decode(des, "UTF-8");

					targetFile = new File(from);
					File to_file = new File(to);
					File dir = to_file.isDirectory() ? to_file : to_file.getParentFile();
					if (!dir.canWrite()) {
						headersString = http_ver + " 403 Forbidden\r\n\r\n";
						connectedClient.getOutputStream().write(headersString.getBytes());
						connectedClient.close();
						return;
					}

					targetFile.renameTo(to_file);

					Log.d("wdSrv", from);
					Log.d("wdSrv", to);

					headersString = http_ver + " 201 Created\r\n\r\n";
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;

				}

				if (requestMethod.equals("DELETE")) {
					targetFile = new File(rootDir + requestTarget);

					if (!targetFile.exists() || !targetFile.canWrite())
					{
						headersString = http_ver + " 403 Forbidden" + "\r\n\r\n";
						connectedClient.getOutputStream().write(headersString.getBytes());
						connectedClient.close();
						return;
					}

					try
					{
						if (targetFile.isDirectory())
							Utils.removeDirectory(targetFile);
						else
							targetFile.delete();

						headersString = http_ver + " 201 Created\r\n";
					}
					catch (Exception e)
					{
						headersString = http_ver +  " 403 Forbidden \r\n";
					}
					headersString += "\r\n";
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;

				}

				if (requestMethod.equals("MKCOL")) {
					File check_dir = new File(rootDir + requestTarget);

					Log.d("wdSrv", "mkcol debug :  " + rootDir + requestTarget);

					if (!check_dir.exists())
					{
						// Directory does not exist, create
						headersString = http_ver + " " + "201 Created" + "\r\n";
						check_dir.mkdir();
					}
					else
					{
						// Prohibited
						headersString = http_ver + " " + "403 Forbidden" + "\r\n";
					}
					headersString += "\r\n";
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				if (requestMethod.equals("PROPFIND")) {
					targetFile = new File(rootDir + requestTarget);

					// pre-check
					// Need to be done, first assume that the path passed in by the other party must be correct

					// Create a structure for returning xml data
					// http://www.mkyong.com/java/how-to-create-xml-file-in-java-dom
					DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder docBuilder = null;
					try
					{
						docBuilder = docFactory.newDocumentBuilder();
					}
					catch (ParserConfigurationException e)
					{
						e.printStackTrace();
					}

					Document doc = docBuilder.newDocument();
					doc.setXmlStandalone(true);
					Element rootElement = doc.createElement("D:multistatus");
					Attr attr = doc.createAttribute("xmlns:D");
					attr.setValue("DAV:");

					rootElement.setAttributeNode(attr);
					doc.appendChild(rootElement);

					// File processing flow
					int th = 0;

					ArrayList<File> ff;
					if ("/storage/emulated".equals(targetFile.getAbsolutePath())) {
						File[] fa = getVolumeDirs().get(0).getParentFile().listFiles();
						if (fa != null && fa.length > 0) {
							ff = new ArrayList<>(Arrays.asList(fa));
						}
						else {
							ff = new ArrayList<>();
							ff.add(getVolumeDirs().get(0));
						}
						requestTarget = ff.get(0).getParent() + "/";
					}
					else if (targetFile.isDirectory()) {
						if (!requestTarget.endsWith("/"))
							requestTarget += "/";
						File[] fa = targetFile.listFiles();
						if (fa != null)
							ff = new ArrayList<>(Arrays.asList(fa));
						else
							ff = new ArrayList<>();
						if (requestTarget.endsWith("/Android/data/")) {
							File appDir = new File(targetFile.getAbsolutePath() + "/" + getApplicationContext().getPackageName());
							if (ff.indexOf(appDir) < 0)
								ff.add(appDir);
						}
						else if ("/storage".equals(targetFile.getAbsolutePath())) {
							ff = getVolumeDirs();
							if (ff.get(0).getAbsolutePath().contains("/emulated/0"))
								ff.set(0, ff.get(0).getParentFile());
						}
					}
					else {
						ff = new ArrayList<>(1);
						ff.add(targetFile);
					}
					if (ff != null) {
						for (File file_obj : ff) {
							if (file_obj.isFile()) {
								// Process regular file
								rootElement.appendChild(doc.createElement("D:response"));
								rootElement.getChildNodes().item(th).appendChild(doc.createElement("D:href"));

								rootElement.getChildNodes().item(th).getChildNodes().item(0).setTextContent(requestTarget + file_obj.getName());
								rootElement.getChildNodes().item(th).appendChild(doc.createElement("D:propstat"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).appendChild(doc.createElement("D:prop"));

								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).appendChild(doc.createElement("D:status"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(0)
										.setTextContent("HTTP/1.1 200 OK");
								String modTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss Z").format(file_obj.lastModified());
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:creationdate"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(1)
										.setTextContent(modTime);
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:getlastmodified"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(2)
										.setTextContent(modTime);
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:resourcetype"));// index 3

								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:getcontentlength")); // index 4
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(4)
										.setTextContent(String.valueOf(file_obj.length()));

								th++;
							}
							else if (file_obj.isDirectory()) {
								rootElement.appendChild(doc.createElement("D:response"));
								rootElement.getChildNodes().item(th).appendChild(doc.createElement("D:href"));

								rootElement.getChildNodes().item(th).getChildNodes().item(0).setTextContent(requestTarget + file_obj.getName() + "/");

								rootElement.getChildNodes().item(th).appendChild(doc.createElement("D:propstat"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).appendChild(doc.createElement("D:prop"));
								// ((Element) rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)).setAttributeNode(attr_prop);
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).appendChild(doc.createElement("D:status"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(0)
										.setTextContent("HTTP/1.1 200 OK");
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:creationdate"));
								String modTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss Z").format(file_obj.lastModified());
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(1)
										.setTextContent(modTime);
								//.setTextContent("2013-11-21T10:12:14:Z");// Preemptive death
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:getlastmodified"));
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(2)
										.setTextContent(modTime);
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:resourcetype"));// index 3

								// for dir add
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(3)
										.appendChild(doc.createElement("D:collection"));

								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0)
										.appendChild(doc.createElement("D:getcontentlength")); // index 4
								rootElement.getChildNodes().item(th).getChildNodes().item(1).getChildNodes().item(0).getChildNodes().item(4)
										.setTextContent(String.valueOf(file_obj.length()));

								th++;
							}
						}
					}

					// http://stackoverflow.com/questions/4412848/xml-node-to-string-in-java
					String xmlbody_str = nodeToString(doc.getDocumentElement());

					// Log.d("wdSrv", xmlbody_str);

					body = xmlbody_str.getBytes();
					headersString = http_ver + " " + "207 Multi-Status" + "\r\n";
					headersString += "Content-Type: application/xml; charset=\"utf-8\"" + "\r\n";
					headersString += "Content-Length: " + String.valueOf(body.length) + "\r\n";
					headersString += "\r\n";

					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().write(body);
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;

				}

				if (requestMethod.equals("GET")) {
					OutputStream os = connectedClient.getOutputStream();
					// Read homepage
					if (requestTarget.equals("/"))
					{
						headersString = http_ver + " 403 Forbidden\r\n";
						headersString += "\r\n";
						os.write(headersString.getBytes());
						os.flush();
						connectedClient.close();
						return;
					}
					else
					{
						targetFile = new File(rootDir + requestTarget);

						if (targetFile.exists())
						{
							// Process files
							if (targetFile.isFile())
							{
								headersString = http_ver + " 200 OK" + "\r\n";
								headersString += "Date: " + new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new Date()) + "\r\n"; // "Fri, 1 Jan 2021 15:44:21 GMT\r\n";
								String extension = "";
								int i = targetFile.getName().lastIndexOf('.');
								int p = Math.max(targetFile.getName().lastIndexOf('/'), targetFile.getName().lastIndexOf('\\'));
								if (i > p)
									extension = targetFile.getName().substring(i + 1).toLowerCase();

								if (extension.equals("htm") || extension.equals("html"))
									headersString += "Content-Type: text/html\r\n";
								else if (extension.equals("jpg"))
									headersString += "Content-type: image/jpeg\r\n";
								else
									headersString += "Content-type: application/octet-stream\r\n";
								headersString += "Content-Length: " + targetFile.length() + "\r\n";
								headersString += "Connection: Keep-Alive\r\n";
								headersString += "\r\n";

								try (FileInputStream fin = new FileInputStream(targetFile)) {
									os.write(headersString.getBytes());
									body = new byte[1024]; // new byte[(int) targetFile.length()];
									int nBytes = 0;
									while ((nBytes = fin.read(body)) > 0) {
										os.write(body, 0, nBytes);
									}
								} catch (IOException iox) {
									Log.e("wdSrv", "Exception in GET: ", iox);
									iox.printStackTrace();
								}
							}
							else
							{
								// Not processing catalogs (directories)
								headersString = http_ver + " 403 Forbidden\r\n";
								headersString += "\r\n";
								connectedClient.getOutputStream().write(headersString.getBytes());
							}

						}
						else
						{
							// A directory or file that does not exist and has not been processed
							headersString = http_ver + " 404 Not found\r\n";
							headersString += "\r\n";
							connectedClient.getOutputStream().write(headersString.getBytes());
						}

					}
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				if (requestMethod.equals("OPTIONS")) {
					headersString = http_ver + " " + "200 OK" + "\r\n";
					headersString += "Date: " + new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new Date()) + "\r\n"; // "Fri, 1 Jan 2021 15:44:21 GMT\r\n";
					headersString += "Server: WebDav Server 1.0\r\n";
					headersString += "DAV: 1, 2\r\n"; // or "DAV: 1, 2\r\n"
					headersString += "Accept-Ranges: none\r\n";
					//HeadersString += "Allow: GET, POST, OPTIONS, HEAD, MKCOL, PUT, PROPFIND, PROPPATCH, DELETE, MOVE, COPY, GETLIB, LOCK, UNLOCK\r\n";
					headersString += "Allow: GET, OPTIONS, MKCOL, PUT, PROPFIND, PROPPATCH, DELETE, MOVE, COPY\r\n";
					headersString += "Cache-Control: private\r\n";
					headersString += "Content-Length: 0\r\n";
					headersString += "X-MSDAVEXT: 1\r\n";
					headersString += "Public-Extension: http://schemas.fourthcoffee.com/repl-2\r\n";
					headersString += "\r\n"; // Extra empty new line is necessary or it won't work!
					connectedClient.getOutputStream().write(headersString.getBytes());
					connectedClient.getOutputStream().flush();
					connectedClient.close();
					return;
				}

				Log.e("wdSrv", "Unknown method: " + requestMethod);
				headersString = http_ver + " 200 OK\r\n";
				headersString += "\r\n";
				connectedClient.getOutputStream().write(headersString.getBytes());
				connectedClient.getOutputStream().flush();
				connectedClient.close();
			}
			catch (IOException e)
			{
				Log.e("wdSrv", "Exception in client_deal run(): ", e);
				e.printStackTrace();
			}
		}

		public clinet_deal(Socket client)
		{
			connectedClient = client;
		}

		private String nodeToString(Node node)
		{
			StringWriter sw = new StringWriter();
			try
			{
				Transformer t = TransformerFactory.newInstance().newTransformer();
				t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
				t.setOutputProperty(OutputKeys.INDENT, "yes");
				t.transform(new DOMSource(node), new StreamResult(sw));
			}
			catch (TransformerException te)
			{
				System.out.println("nodeToString Transformer Exception");
			}
			return sw.toString();
		}

		public ArrayList<File> getVolumeDirs() {
			ArrayList<File> volDirs = new ArrayList<>();
			File[] dirs = getExternalFilesDirs(null);
			for (int i = 0; i < dirs.length; i++) {
				if (dirs[i] == null)
					continue;
				// .../Android/data/com.hyperionics.avar/files
				String path = dirs[i].getAbsolutePath();
				int n = path.indexOf("/Android/data/");
				if (n > 0) {
					path = path.substring(0, n);
					File f = new File(path);
					volDirs.add(f); // volDirs[i].getParentFile().getParentFile().getParentFile().getParentFile();
				}
			}
			return volDirs;
		}
	}
}
