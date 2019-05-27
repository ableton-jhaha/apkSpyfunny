package com.lucasbaizer;

import java.util.HashMap;
import java.util.Map;

public class ChangeCache {
	private static final Map<String, String> CHANGES = new HashMap<>();

	public static Map<String, String> getChanges() {
		return CHANGES;
	}
}
