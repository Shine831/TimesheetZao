package com.jalios.jcmsplugin.jbook.component;

import com.jalios.jcms.StartupDataGenerator;
import com.jalios.jcmsplugin.jbook.JBookManager;

public class JBookStartupDataGenerator extends StartupDataGenerator {
	@Override
	public void createData() {
		createCategory(JBookManager.VID_TOPIC_ROOT);
	}
}