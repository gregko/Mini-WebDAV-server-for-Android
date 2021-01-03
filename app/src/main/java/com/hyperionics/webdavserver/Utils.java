package com.hyperionics.webdavserver;

//import org.apache.http.conn.util.InetAddressUtils;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.List;

public class Utils
{
	// Copy files
	// http://herolin.twbbs.org/entry/java-copy-file-directory/
	public static void copyFile(String srFile, String dtFile)
	{
		try
		{
			FileChannel srcChannel = new FileInputStream(srFile).getChannel();
			FileChannel dstChannel = new FileOutputStream(dtFile).getChannel();
			dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
			srcChannel.close();
			dstChannel.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	// Recursively copy the entire directory
	public static void copyDirectory(File source, File target)
	{
		File[] file = source.listFiles();
		for (int i = 0; i < file.length; i++)
		{
			if (file[i].isFile())
			{
				File sourceDemo = new File(source.getAbsolutePath() + "/" + file[i].getName());
				File destDemo = new File(target.getAbsolutePath() + "/" + file[i].getName());
				copyFile(sourceDemo.getAbsolutePath(), destDemo.getAbsolutePath());
			}
			if (file[i].isDirectory())
			{
				File sourceDemo = new File(source.getAbsolutePath() + "/" + file[i].getName());
				File destDemo = new File(target.getAbsolutePath() + "/" + file[i].getName());
				destDemo.mkdir();
				copyDirectory(sourceDemo, destDemo);
			}
		}
	}

	// Recursively delete the entire directory
	public static boolean removeDirectory(File directory)
	{
		if (directory == null)
			return false;
		if (!directory.exists())
			return true;
		if (!directory.isDirectory())
			return false;

		String[] list = directory.list();
		if (list != null)
		{
			for (int i = 0; i < list.length; i++)
			{
				File entry = new File(directory, list[i]);
				if (entry.isDirectory())
				{
					if (!removeDirectory(entry))
						return false;
				}
				else
				{
					if (!entry.delete())
						return false;
				}
			}
		}
		return directory.delete();
	}

	public static String getIPAddress(boolean useIPv4)
	{
		try
		{
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces)
			{
				List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
				for (InetAddress addr : addrs)
				{
					if (!addr.isLoopbackAddress())
					{
						String sAddr = addr.getHostAddress().toUpperCase();
						boolean isIPv4 = sAddr.indexOf(':') < 0;
						if (useIPv4)
						{
							if (isIPv4)
								return sAddr;
						}
						else
						{
							if (!isIPv4)
							{
								int delim = sAddr.indexOf('%'); // drop ip6 port suffix
								return delim < 0 ? sAddr : sAddr.substring(0, delim);
							}
						}
					}
				}
			}
		}
		catch (Exception ex)
		{
			Log.e("my", "Exception in getIPAddress(): " + ex);
			ex.printStackTrace();
		}
		return "";
	}
}
