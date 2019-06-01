package com.lucasbaizer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class ApktoolWrapper {
	public static void decode(Path apk, String dir, boolean resources, OutputStream out)
			throws InterruptedException, IOException {
		Util.system(new File(System.getProperty("user.dir")), out, "java", "-jar",
				"tools" + File.separator + "apktool.jar", "decode", "-o", "smali" + File.separator + dir,
				resources ? "" : "-r", apk.toAbsolutePath().toString());
	}

	public static void build(Path apk, String outputLocation, OutputStream out)
			throws InterruptedException, IOException {
		Util.system(new File(System.getProperty("user.dir")), out, "java", "-jar",
				"tools" + File.separator + "apktool.jar", "build", "-o", outputLocation,
				apk.toAbsolutePath().toString());
	}
}
