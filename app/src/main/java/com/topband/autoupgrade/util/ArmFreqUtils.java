package com.topband.autoupgrade.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.os.Build;
import android.util.Log;

public class ArmFreqUtils {

	private static final String TAG = "ArmFreqUtils";

	private static File frequenciesFile = new File(
			"/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies");
	private static File cur_freqFile = new File(
			"/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
	private static File governor_freqFile = new File(
			"/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
	private static File setspeed_freqFile = new File(
			"/sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed");
	private static File gpu_availableFreqsFile = new File("/sys/mali400_utility/param");
	private static File gpu_freqFile_3188 = new File("/sys/mali400_utility/utility");
	private static File gpu_freqFile = new File("/proc/pvr/freq");
	private static File ddr_freqFile = new File("/proc/driver/ddr_ts");
	public static final String USERSPACE_MODE = "userspace";
	public static final String INTERACTIVE_MODE = "interactive";
	public static int GPU_AVAILABLE_FREQ_COUNT = 6;

	public static void setDDRFreq(String value) throws FileNotFoundException,
			IOException {

		PMwriteFile(ddr_freqFile, value);

	}

	public static void openGpuEcho() throws FileNotFoundException, IOException {

		PMwriteFile(gpu_freqFile, "debug_lo");

	}

	public static void setGpuFreq(String value) throws FileNotFoundException,
			IOException {

		PMwriteFile(gpu_freqFile, value);

	}
	
	public static void setGpuFreqFor3188(String value) throws FileNotFoundException,
	IOException {
		PMwriteFile(gpu_freqFile_3188, value);
	}

	public static void setGovernorMode(String mode)
			throws FileNotFoundException, IOException {
		// if(governor_freqFile.exists()){
		// try {
		PMwriteFile(governor_freqFile, mode);
		// } catch (Exception re) {
		// re.printStackTrace();
		// Log.e(TAG, "IO Exception");
		// }
		// }
	}

	public static void setSpeedFreq(int value) throws FileNotFoundException,
			IOException {
		// if(setspeed_freqFile.exists()){
		// try {
		PMwriteFile(setspeed_freqFile, String.valueOf(value));
		// } catch (Exception re) {
		// re.printStackTrace();
		// Log.e(TAG, "IO Exception");
		// }
		// }
	}

	private static boolean PMwriteFile(File file, String message)
			throws FileNotFoundException, IOException {
		// Log.i(TAG, "PMwriteFile:" + Path + "\nMSG:" + message);
		// try {
		// File file = new File(Path);
		if (!file.exists()) {
			throw new FileNotFoundException();
		}
		if (file.canWrite()) {
			FileOutputStream fout = new FileOutputStream(file);
			byte[] bytes = message.getBytes();
			fout.write(bytes);
			fout.close();
		} else {
			Log.e(TAG, file.toString() + "can not write");
			IOException io = new IOException();
			throw io;
		}
		// } catch (Exception e) {
		// e.printStackTrace();
		// return false;
		// }
		return true;

	}

	public static List<String> getAvailableFrequencies() {
		List<String> result = new ArrayList<String>();
		if (frequenciesFile.exists()) {
			try {
				FileReader fread = new FileReader(frequenciesFile);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;
				StringBuilder sb = new StringBuilder();
				while ((str = buffer.readLine()) != null) {
					sb.append(str);
				}
				String temp[] = sb.toString().split(" ");
				if (temp != null && temp.length > 0) {
					for (int i = 0; i < temp.length; i++) {
						result.add(Integer.valueOf(temp[i]) / 1000 + "M");
					}
				}
			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		}

		return result;
	}

	public static Integer getCurFrequencies() {
		Integer result = 0;
		if (cur_freqFile.exists()) {
			try {
				FileReader fread = new FileReader(cur_freqFile);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;
				StringBuilder sb = new StringBuilder();
				while ((str = buffer.readLine()) != null) {
					sb.append(str);
				}
				result = Integer.valueOf(sb.toString());
			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		}

		return result;
	}

	public static String getCurDDR() {
		String result = "";
		if (ddr_freqFile.exists()) {
			try {
				FileReader fread = new FileReader(ddr_freqFile);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;
				StringBuilder sb = new StringBuilder();
				while ((str = buffer.readLine()) != null) {
					sb.append(str);
				}
				result = sb.toString();
			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		}

		return result;
	}
	
	public static  List<String> getAvailableGpuFreqs() {
		List<String> result = new ArrayList<String>();
		if (gpu_availableFreqsFile.exists()) {
			try {
				FileReader fread = new FileReader(gpu_availableFreqsFile);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;
				StringBuilder sb = new StringBuilder();
				while ((str = buffer.readLine()) != null) {
					sb.append(str);
				}
				String temp[] = sb.toString().split(",");
				if (temp != null && temp.length > 0) {
					GPU_AVAILABLE_FREQ_COUNT = Integer.valueOf(temp[0]);
					for (int i = 0; i < GPU_AVAILABLE_FREQ_COUNT; i++) {
						result.add(Integer.valueOf(temp[i+1])+"");
					}
				}
			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		}

		return result;
	}

	public static int getNumberOfCPUCores() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			// Gingerbread doesn't support giving a single application access to both cores, but a
			// handful of devices (Atrix 4G and Droid X2 for example) were released with a dual-core
			// chipset and Gingerbread; that can let an app in the background run without impacting
			// the foreground application. But for our purposes, it makes them single core.
			return 1;
		}
		int cores;
		try {
			cores = new File("/sys/devices/system/cpu/").listFiles(CPU_FILTER).length;
		} catch (SecurityException e) {
			cores = 1;
		} catch (NullPointerException e) {
			cores = 1;
		}
		return cores;
	}

	private static final FileFilter CPU_FILTER = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			String path = pathname.getName();
			//regex is slow, so checking char by char.
			if (path.startsWith("cpu")) {
				for (int i = 3; i < path.length(); i++) {
					if (path.charAt(i) < '0' || path.charAt(i) > '9') {
						return false;
					}
				}
				return true;
			}
			return false;
		}
	};
}
